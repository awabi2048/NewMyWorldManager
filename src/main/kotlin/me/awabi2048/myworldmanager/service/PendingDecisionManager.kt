package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PendingInteraction
import me.awabi2048.myworldmanager.model.PendingInteractionType
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PendingDecisionManager(private val plugin: MyWorldManager) {

    enum class PendingType {
        WORLD_INVITE,
        MEMBER_INVITE,
        MEMBER_REQUEST,
        MEET_REQUEST
    }

    data class PendingEntryView(
        val id: UUID,
        val type: PendingType,
        val worldUuid: UUID?,
        val actorUuid: UUID,
        val createdAt: Long,
        val persistent: Boolean
    )

    data class EnqueueResult(
        val id: UUID,
        val count: Int
    )

    private sealed interface PendingDecision {
        val id: UUID
        val type: PendingType
        val actorUuid: UUID
        val worldUuid: UUID?
        val createdAt: Long
        val expiresAt: Long
    }

    private data class WorldInviteDecision(
        override val id: UUID,
        override val worldUuid: UUID,
        override val actorUuid: UUID,
        override val createdAt: Long,
        override val expiresAt: Long
    ) : PendingDecision {
        override val type: PendingType = PendingType.WORLD_INVITE
    }

    private data class MeetRequestDecision(
        override val id: UUID,
        val requesterUuid: UUID,
        override val type: PendingType = PendingType.MEET_REQUEST,
        override val actorUuid: UUID,
        override val worldUuid: UUID?,
        override val createdAt: Long,
        override val expiresAt: Long
    ) : PendingDecision

    data class PersistentPendingView(
        val id: UUID,
        val type: PendingInteractionType,
        val worldUuid: UUID,
        val actorUuid: UUID,
        val createdAt: Long
    )

    private val transientByTarget = ConcurrentHashMap<UUID, ArrayDeque<PendingDecision>>()

    fun enqueueWorldInvite(target: Player, worldUuid: UUID, senderUuid: UUID, timeoutSeconds: Long): EnqueueResult {
        val now = System.currentTimeMillis()
        val decision = WorldInviteDecision(
            id = UUID.randomUUID(),
            worldUuid = worldUuid,
            actorUuid = senderUuid,
            createdAt = now,
            expiresAt = now + (timeoutSeconds * 1000L)
        )
        val count = enqueueTransient(
            target.uniqueId,
            decision
        )
        return EnqueueResult(decision.id, count)
    }

    fun enqueueMemberInvite(targetUuid: UUID, worldUuid: UUID, senderUuid: UUID): Int {
        plugin.pendingInteractionRepository.add(
            type = PendingInteractionType.MEMBER_INVITE,
            targetUuid = targetUuid,
            worldUuid = worldUuid,
            actorUuid = senderUuid
        )
        return plugin.pendingInteractionRepository.countByTarget(targetUuid)
    }

    fun enqueueMemberRequest(ownerUuid: UUID, worldUuid: UUID, requestorUuid: UUID): Int {
        plugin.pendingInteractionRepository.add(
            type = PendingInteractionType.MEMBER_REQUEST,
            targetUuid = ownerUuid,
            worldUuid = worldUuid,
            actorUuid = requestorUuid
        )
        return plugin.pendingInteractionRepository.countByTarget(ownerUuid)
    }

    fun enqueueMeetRequest(target: Player, requesterUuid: UUID, worldUuid: UUID?, timeoutSeconds: Long): EnqueueResult {
        val now = System.currentTimeMillis()
        val decision = MeetRequestDecision(
            id = UUID.randomUUID(),
            requesterUuid = requesterUuid,
            actorUuid = requesterUuid,
            worldUuid = worldUuid,
            createdAt = now,
            expiresAt = now + (timeoutSeconds * 1000L)
        )
        val count = enqueueTransient(
            target.uniqueId,
            decision
        )
        return EnqueueResult(decision.id, count)
    }

    fun getPersistentPending(targetUuid: UUID): List<PersistentPendingView> {
        return plugin.pendingInteractionRepository.findByTarget(targetUuid).map {
            PersistentPendingView(
                id = it.id,
                type = it.type,
                worldUuid = it.worldUuid,
                actorUuid = it.actorUuid,
                createdAt = it.createdAt
            )
        }
    }

    fun getPendingEntries(targetUuid: UUID): List<PendingEntryView> {
        val persistent = plugin.pendingInteractionRepository.findByTarget(targetUuid).map {
            PendingEntryView(
                id = it.id,
                type = when (it.type) {
                    PendingInteractionType.MEMBER_INVITE -> PendingType.MEMBER_INVITE
                    PendingInteractionType.MEMBER_REQUEST -> PendingType.MEMBER_REQUEST
                },
                worldUuid = it.worldUuid,
                actorUuid = it.actorUuid,
                createdAt = it.createdAt,
                persistent = true
            )
        }
        val transient = getTransientPendingEntries(targetUuid)
        return (persistent + transient).sortedByDescending { it.createdAt }
    }

    fun getPendingEntry(targetUuid: UUID, decisionId: UUID): PendingEntryView? {
        return getPendingEntries(targetUuid).firstOrNull { it.id == decisionId }
    }

    fun getSinglePendingCandidate(targetUuid: UUID): PendingEntryView? {
        val entries = getPendingEntries(targetUuid)
        return if (entries.size == 1) entries.first() else null
    }

    fun getPersistentPendingCount(targetUuid: UUID): Int {
        return plugin.pendingInteractionRepository.countByTarget(targetUuid)
    }

    fun getLatestPersistentCreatedAt(targetUuid: UUID): Long? {
        return plugin.pendingInteractionRepository.latestByTarget(targetUuid)?.createdAt
    }

    fun resolvePersistentById(target: Player, decisionId: UUID, accept: Boolean): Boolean {
        val lang = plugin.languageManager
        val interaction = plugin.pendingInteractionRepository.findById(decisionId)
        if (interaction == null || interaction.targetUuid != target.uniqueId) {
            target.sendMessage(lang.getMessage(target, "messages.myworld_pending_none"))
            return false
        }

        plugin.pendingInteractionRepository.remove(interaction.id)
        handlePersistentResolve(target, interaction, accept)

        val remaining = getPendingCount(target.uniqueId)
        if (remaining > 0) {
            target.sendMessage(
                lang.getMessage(
                    target,
                    "messages.myworld_pending_remaining",
                    mapOf("count" to remaining)
                )
            )
        }
        return true
    }

    fun resolveById(target: Player, decisionId: UUID, accept: Boolean): Boolean {
        val persistent = plugin.pendingInteractionRepository.findById(decisionId)
        if (persistent != null) {
            return resolvePersistentById(target, decisionId, accept)
        }

        val decision = removeTransientById(target.uniqueId, decisionId)
        if (decision == null) {
            target.sendMessage(plugin.languageManager.getMessage(target, "messages.myworld_pending_none"))
            return false
        }

        handleTransientResolve(target, decision, accept)
        val remaining = getPendingCount(target.uniqueId)
        if (remaining > 0) {
            target.sendMessage(
                plugin.languageManager.getMessage(
                    target,
                    "messages.myworld_pending_remaining",
                    mapOf("count" to remaining)
                )
            )
        }
        return true
    }

    fun resolveLatest(target: Player, accept: Boolean): Boolean {
        val latest = getPendingEntries(target.uniqueId).firstOrNull() ?: run {
            target.sendMessage(plugin.languageManager.getMessage(target, "messages.myworld_pending_none"))
            return false
        }
        return resolveById(target, latest.id, accept)
    }

    fun sendPendingHint(target: Player, count: Int) {
        if (count < 1) {
            return
        }
        target.sendMessage(
            plugin.languageManager.getMessage(
                target,
                "messages.myworld_pending_hint",
                mapOf("count" to count)
            )
        )
    }

    fun getPendingCount(targetUuid: UUID): Int {
        return getPersistentPendingCount(targetUuid) + getTransientPendingCount(targetUuid)
    }

    private fun handlePersistentResolve(target: Player, interaction: PendingInteraction, accept: Boolean) {
        val lang = plugin.languageManager
        when (interaction.type) {
            PendingInteractionType.MEMBER_INVITE -> {
                if (accept) {
                    plugin.memberInviteManager.handleMemberInviteAcceptDirect(
                        target,
                        interaction.worldUuid,
                        interaction.actorUuid
                    )
                } else {
                    target.sendMessage(lang.getMessage(target, "messages.member_invite_declined"))
                    Bukkit.getPlayer(interaction.actorUuid)?.let { sender ->
                        sender.sendMessage(
                            lang.getMessage(
                                sender,
                                "messages.member_invite_declined_sender",
                                mapOf("player" to target.name)
                            )
                        )
                    }
                }
            }

            PendingInteractionType.MEMBER_REQUEST -> {
                if (accept) {
                    plugin.memberRequestManager.handleApprovalDirect(
                        target,
                        interaction.actorUuid,
                        interaction.worldUuid
                    )
                } else {
                    plugin.memberRequestManager.handleRejectionDirect(
                        target,
                        interaction.actorUuid,
                        interaction.worldUuid
                    )
                }
            }
        }
    }

    private fun handleTransientResolve(target: Player, decision: PendingDecision, accept: Boolean) {
        when (decision) {
            is WorldInviteDecision -> {
                if (accept) {
                    plugin.worldService.teleportToWorld(target, decision.worldUuid) {
                        target.sendMessage(plugin.languageManager.getMessage(target, "messages.warp_invite_success"))
                    }
                } else {
                    target.sendMessage(plugin.languageManager.getMessage(target, "messages.invite_declined"))
                }
            }

            is MeetRequestDecision -> {
                if (accept) {
                    handleMeetRequestAccept(target, decision.requesterUuid)
                } else {
                    target.sendMessage(plugin.languageManager.getMessage(target, "messages.meet.request_denied"))
                    Bukkit.getPlayer(decision.requesterUuid)?.let { requester ->
                        requester.sendMessage(
                            plugin.languageManager.getMessage(
                                requester,
                                "messages.meet.request_denied_by_target",
                                mapOf("player" to target.name)
                            )
                        )
                    }
                }
            }
        }
    }

    private fun getTransientPendingCount(targetUuid: UUID): Int {
        val queue = transientByTarget[targetUuid] ?: return 0
        synchronized(queue) {
            cleanupExpiredLocked(queue)
            val size = queue.size
            if (size == 0) {
                transientByTarget.remove(targetUuid)
            }
            return size
        }
    }

    private fun enqueueTransient(targetUuid: UUID, decision: PendingDecision): Int {
        val queue = transientByTarget.computeIfAbsent(targetUuid) { ArrayDeque() }
        synchronized(queue) {
            cleanupExpiredLocked(queue)
            queue.addFirst(decision)
            return queue.size
        }
    }

    private fun getTransientPendingEntries(targetUuid: UUID): List<PendingEntryView> {
        val queue = transientByTarget[targetUuid] ?: return emptyList()
        synchronized(queue) {
            cleanupExpiredLocked(queue)
            val entries = queue.map {
                PendingEntryView(
                    id = it.id,
                    type = it.type,
                    worldUuid = it.worldUuid,
                    actorUuid = it.actorUuid,
                    createdAt = it.createdAt,
                    persistent = false
                )
            }
            if (queue.isEmpty()) {
                transientByTarget.remove(targetUuid)
            }
            return entries
        }
    }

    private fun removeTransientById(targetUuid: UUID, decisionId: UUID): PendingDecision? {
        val queue = transientByTarget[targetUuid] ?: return null
        synchronized(queue) {
            cleanupExpiredLocked(queue)
            val iter = queue.iterator()
            while (iter.hasNext()) {
                val item = iter.next()
                if (item.id == decisionId) {
                    iter.remove()
                    if (queue.isEmpty()) {
                        transientByTarget.remove(targetUuid)
                    }
                    return item
                }
            }
            if (queue.isEmpty()) {
                transientByTarget.remove(targetUuid)
            }
            return null
        }
    }

    private fun pollLatestValidTransient(targetUuid: UUID): PendingDecision? {
        val queue = transientByTarget[targetUuid] ?: return null
        synchronized(queue) {
            cleanupExpiredLocked(queue)
            val latest = if (queue.isEmpty()) null else queue.removeFirst()
            if (queue.isEmpty()) {
                transientByTarget.remove(targetUuid)
            }
            return latest
        }
    }

    private fun cleanupExpiredLocked(queue: ArrayDeque<PendingDecision>) {
        val now = System.currentTimeMillis()
        val iter = queue.iterator()
        while (iter.hasNext()) {
            val item = iter.next()
            if (item.expiresAt < now) {
                iter.remove()
            }
        }
    }

    private fun handleMeetRequestAccept(target: Player, requesterUuid: UUID) {
        val requester = Bukkit.getPlayer(requesterUuid)
        if (requester == null || !requester.isOnline) {
            target.sendMessage(plugin.languageManager.getMessage(target, "messages.meet.requester_offline"))
            return
        }

        val worldData = plugin.worldConfigRepository.findByWorldName(target.world.name)
        if (worldData == null) {
            target.sendMessage(plugin.languageManager.getMessage(target, "messages.meet.not_in_valid_world"))
            return
        }

        plugin.worldService.teleportToWorld(requester, worldData.uuid) {
            target.sendMessage(
                plugin.languageManager.getMessage(
                    target,
                    "messages.meet.request_accepted",
                    mapOf("player" to requester.name)
                )
            )
            requester.sendMessage(
                plugin.languageManager.getMessage(
                    requester,
                    "messages.meet.request_accepted_by_target",
                    mapOf("player" to target.name)
                )
            )

            val isMember =
                worldData.owner == requester.uniqueId ||
                    worldData.moderators.contains(requester.uniqueId) ||
                    worldData.members.contains(requester.uniqueId)
            if (!isMember) {
                worldData.recentVisitors[0]++
                plugin.worldConfigRepository.save(worldData)
            }
        }
    }
}
