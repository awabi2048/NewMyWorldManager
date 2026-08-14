package me.awabi2048.myworldmanager.service

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys

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
        MEET_REQUEST,
        VISIT_REQUEST
    }

    data class PendingEntryView(
        val id: UUID,
        val type: PendingType,
        val worldUuid: UUID?,
        val actorUuid: UUID,
        val createdAt: Long,
        val actionCode: String,
        val persistent: Boolean
    )

    data class EnqueueResult(
        val id: UUID,
        val actionCode: String,
        val count: Int
    )

    enum class ResolveCodeResult {
        RESOLVED,
        INVALID_FORMAT,
        NOT_FOUND,
        EXPIRED
    }

    private sealed interface PendingDecision {
        val id: UUID
        val type: PendingType
        val actorUuid: UUID
        val worldUuid: UUID?
        val createdAt: Long
        val actionCode: String
        val expiresAt: Long
    }

    private data class WorldInviteDecision(
        override val id: UUID,
        override val worldUuid: UUID,
        override val actorUuid: UUID,
        override val createdAt: Long,
        override val actionCode: String,
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
        override val actionCode: String,
        override val expiresAt: Long
    ) : PendingDecision

    private data class VisitRequestDecision(
        override val id: UUID,
        val requesterUuid: UUID,
        override val actorUuid: UUID,
        override val worldUuid: UUID,
        override val createdAt: Long,
        override val actionCode: String,
        override val expiresAt: Long
    ) : PendingDecision {
        override val type: PendingType = PendingType.VISIT_REQUEST
    }

    data class PersistentPendingView(
        val id: UUID,
        val type: PendingInteractionType,
        val worldUuid: UUID,
        val actorUuid: UUID,
        val createdAt: Long,
        val actionCode: String
    )

    private val transientByTarget = ConcurrentHashMap<UUID, ArrayDeque<PendingDecision>>()
    private val expiredCodesByTarget = ConcurrentHashMap<UUID, MutableMap<String, Long>>()
    private val actionCodeAllocator = PendingActionCodeAllocator()

    @Synchronized
    fun enqueueWorldInvite(target: Player, worldUuid: UUID, senderUuid: UUID, timeoutSeconds: Long): EnqueueResult {
        val now = System.currentTimeMillis()
        val actionCode = allocateActionCode(target.uniqueId)
            ?: error("Pending action code space exhausted for ${target.uniqueId}")
        val decision = WorldInviteDecision(
            id = UUID.randomUUID(),
            worldUuid = worldUuid,
            actorUuid = senderUuid,
            createdAt = now,
            actionCode = actionCode,
            expiresAt = now + (timeoutSeconds * 1000L)
        )
        val count = enqueueTransient(
            target.uniqueId,
            decision
        )
        return EnqueueResult(decision.id, actionCode, count)
    }

    @Synchronized
    fun enqueueMemberInvite(
        targetUuid: UUID,
        worldUuid: UUID,
        senderUuid: UUID,
        targetOnlineAtCreation: Boolean = true,
    ): EnqueueResult {
        val actionCode = allocateActionCode(targetUuid)
            ?: error("Pending action code space exhausted for $targetUuid")
        val interaction = plugin.pendingInteractionRepository.add(
            type = PendingInteractionType.MEMBER_INVITE,
            targetUuid = targetUuid,
            worldUuid = worldUuid,
            actorUuid = senderUuid,
            actionCode = actionCode,
            targetOnlineAtCreation = targetOnlineAtCreation,
        )
        return EnqueueResult(interaction.id, actionCode, getPendingCount(targetUuid))
    }

    @Synchronized
    fun enqueueMemberRequest(ownerUuid: UUID, worldUuid: UUID, requestorUuid: UUID): EnqueueResult {
        val actionCode = allocateActionCode(ownerUuid)
            ?: error("Pending action code space exhausted for $ownerUuid")
        val interaction = plugin.pendingInteractionRepository.add(
            type = PendingInteractionType.MEMBER_REQUEST,
            targetUuid = ownerUuid,
            worldUuid = worldUuid,
            actorUuid = requestorUuid,
            actionCode = actionCode
        )
        return EnqueueResult(interaction.id, actionCode, getPendingCount(ownerUuid))
    }

    @Synchronized
    fun enqueueMeetRequest(target: Player, requesterUuid: UUID, worldUuid: UUID?, timeoutSeconds: Long): EnqueueResult {
        val now = System.currentTimeMillis()
        val actionCode = allocateActionCode(target.uniqueId)
            ?: error("Pending action code space exhausted for ${target.uniqueId}")
        val decision = MeetRequestDecision(
            id = UUID.randomUUID(),
            requesterUuid = requesterUuid,
            actorUuid = requesterUuid,
            worldUuid = worldUuid,
            createdAt = now,
            actionCode = actionCode,
            expiresAt = now + (timeoutSeconds * 1000L)
        )
        val count = enqueueTransient(
            target.uniqueId,
            decision
        )
        return EnqueueResult(decision.id, actionCode, count)
    }

    @Synchronized
    fun enqueueVisitRequest(target: Player, requesterUuid: UUID, worldUuid: UUID, timeoutSeconds: Long): EnqueueResult? {
        if (hasPendingVisitRequest(target.uniqueId, requesterUuid, worldUuid)) {
            return null
        }
        val now = System.currentTimeMillis()
        val actionCode = allocateActionCode(target.uniqueId)
            ?: error("Pending action code space exhausted for ${target.uniqueId}")
        val decision = VisitRequestDecision(
            id = UUID.randomUUID(),
            requesterUuid = requesterUuid,
            actorUuid = requesterUuid,
            worldUuid = worldUuid,
            createdAt = now,
            actionCode = actionCode,
            expiresAt = now + (timeoutSeconds * 1000L)
        )
        val count = enqueueTransient(target.uniqueId, decision)
        return EnqueueResult(decision.id, actionCode, count)
    }

    fun getPersistentPending(targetUuid: UUID): List<PersistentPendingView> {
        return plugin.pendingInteractionRepository.findByTarget(targetUuid).map {
            PersistentPendingView(
                id = it.id,
                type = it.type,
                worldUuid = it.worldUuid,
                actorUuid = it.actorUuid,
                createdAt = it.createdAt,
                actionCode = it.actionCode
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
                actionCode = it.actionCode,
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

    fun getLatestPendingCreatedAt(targetUuid: UUID): Long? {
        return getPendingEntries(targetUuid).maxOfOrNull(PendingEntryView::createdAt)
    }

    fun resolvePersistentById(target: Player, decisionId: UUID, accept: Boolean): Boolean {
        val lang = plugin.languageManager
        val interaction = plugin.pendingInteractionRepository.findById(decisionId)
        if (interaction == null || interaction.targetUuid != target.uniqueId) {
            target.sendMessage(lang.getMessage(target, MyworldMessagesKeys.MESSAGES_MYWORLD_PENDING_NONE))
            return false
        }

        plugin.pendingInteractionRepository.remove(interaction.id)
        handlePersistentResolve(target, interaction, accept)

        val remaining = getPendingCount(target.uniqueId)
        if (remaining > 0) {
            target.sendMessage(
                lang.getMessage(
                    target,
                    MyworldMessagesKeys.MESSAGES_MYWORLD_PENDING_REMAINING,
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
            target.sendMessage(plugin.languageManager.getMessage(target, MyworldMessagesKeys.MESSAGES_MYWORLD_PENDING_NONE))
            return false
        }

        handleTransientResolve(target, decision, accept)
        val remaining = getPendingCount(target.uniqueId)
        if (remaining > 0) {
            target.sendMessage(
                plugin.languageManager.getMessage(
                    target,
                    MyworldMessagesKeys.MESSAGES_MYWORLD_PENDING_REMAINING,
                    mapOf("count" to remaining)
                )
            )
        }
        return true
    }

    fun resolveByActionCode(target: Player, actionCode: String, accept: Boolean): ResolveCodeResult {
        if (!PendingActionCodeAllocator.CODE_PATTERN.matches(actionCode)) {
            return ResolveCodeResult.INVALID_FORMAT
        }
        val persistent = plugin.pendingInteractionRepository.findByTargetAndActionCode(target.uniqueId, actionCode)
        if (persistent != null) {
            resolvePersistentById(target, persistent.id, accept)
            return ResolveCodeResult.RESOLVED
        }
        val transient = getTransientPendingEntries(target.uniqueId)
            .firstOrNull { it.actionCode == actionCode }
        if (transient != null) {
            resolveById(target, transient.id, accept)
            return ResolveCodeResult.RESOLVED
        }
        cleanupExpiredCodeRecords(target.uniqueId)
        return if (expiredCodesByTarget[target.uniqueId]?.containsKey(actionCode) == true) {
            ResolveCodeResult.EXPIRED
        } else {
            ResolveCodeResult.NOT_FOUND
        }
    }

    fun resolveLatest(target: Player, accept: Boolean): Boolean {
        val latest = getPendingEntries(target.uniqueId).firstOrNull() ?: run {
            target.sendMessage(plugin.languageManager.getMessage(target, MyworldMessagesKeys.MESSAGES_MYWORLD_PENDING_NONE))
            return false
        }
        return resolveById(target, latest.id, accept)
    }

    /**
     * 一括招待の送信時に記録したIDだけを取り消します。
     * 招待者・招待先も照合するため、同じ対象へ後から送られた別の招待を巻き込みません。
     */
    @Synchronized
    fun cancelWorldInviteBatch(
        actorUuid: UUID,
        worldUuid: UUID,
        decisionsByTarget: Map<UUID, UUID>,
    ): Int = decisionsByTarget.count { (targetUuid, decisionId) ->
        val queue = transientByTarget[targetUuid] ?: return@count false
        synchronized(queue) {
            cleanupExpiredLocked(targetUuid, queue)
            val iterator = queue.iterator()
            while (iterator.hasNext()) {
                val decision = iterator.next()
                if (
                    decision is WorldInviteDecision &&
                    worldInviteCancellationMatches(
                        actualDecisionId = decision.id,
                        actualActorUuid = decision.actorUuid,
                        actualWorldUuid = decision.worldUuid,
                        expectedDecisionId = decisionId,
                        expectedActorUuid = actorUuid,
                        expectedWorldUuid = worldUuid,
                    )
                ) {
                    iterator.remove()
                    if (queue.isEmpty()) transientByTarget.remove(targetUuid)
                    return@synchronized true
                }
            }
            false
        }
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
                    target.sendMessage(lang.getMessage(target, MyworldMessagesKeys.MESSAGES_MEMBER_INVITE_DECLINED))
                    Bukkit.getPlayer(interaction.actorUuid)?.let { sender ->
                        sender.sendMessage(
                            lang.getMessage(
                                sender,
                                MyworldMessagesKeys.MESSAGES_MEMBER_INVITE_DECLINED_SENDER,
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
                    val worldData = plugin.worldConfigRepository.findByUuid(decision.worldUuid)
                    if (
                        worldData == null ||
                        !me.awabi2048.myworldmanager.api.MyWorldManagerApi.getWorldAccessPolicy()
                            .canAcceptWorldInvite(target, worldData)
                    ) {
                        target.sendMessage(plugin.languageManager.getMessage(target, MyworldMessagesKeys.MESSAGES_WORLDWARP_ACCESS_DENIED))
                        return
                    }
                    plugin.worldService.teleportToWorld(target, decision.worldUuid) {
                        target.sendMessage(plugin.languageManager.getMessage(target, MyworldMessagesKeys.MESSAGES_WARP_INVITE_SUCCESS))
                    }
                } else {
                    target.sendMessage(plugin.languageManager.getMessage(target, MyworldMessagesKeys.MESSAGES_INVITE_DECLINED))
                }
            }

            is MeetRequestDecision -> {
                if (accept) {
                    handleMeetRequestAccept(target, decision.requesterUuid)
                } else {
                    target.sendMessage(plugin.languageManager.getMessage(target, MyworldMessagesKeys.MESSAGES_MEET_REQUEST_DENIED))
                    Bukkit.getPlayer(decision.requesterUuid)?.let { requester ->
                        requester.sendMessage(
                            plugin.languageManager.getMessage(
                                requester,
                                MyworldMessagesKeys.MESSAGES_MEET_REQUEST_DENIED_BY_TARGET,
                                mapOf("player" to target.name)
                            )
                        )
                    }
                }
            }

            is VisitRequestDecision -> {
                if (accept) {
                    handleVisitRequestAccept(target, decision.requesterUuid, decision.worldUuid)
                } else {
                    val requester = Bukkit.getPlayer(decision.requesterUuid)
                    requester?.sendMessage(
                        plugin.languageManager.getMessage(
                            requester,
                            MyworldMessagesKeys.MESSAGES_VISIT_REQUEST_DENIED,
                            mapOf("owner" to target.name)
                        )
                    )
                    target.sendMessage(
                        plugin.languageManager.getMessage(
                            target,
                            MyworldMessagesKeys.MESSAGES_VISIT_REQUEST_DENIED_BY_TARGET,
                            mapOf("player" to (requester?.name ?: plugin.languageManager.getMessage(target, CommonKeys.GENERAL_UNKNOWN)))
                        )
                    )
                }
            }
        }
    }

    private fun getTransientPendingCount(targetUuid: UUID): Int {
        val queue = transientByTarget[targetUuid] ?: return 0
        synchronized(queue) {
            cleanupExpiredLocked(targetUuid, queue)
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
            cleanupExpiredLocked(targetUuid, queue)
            queue.addFirst(decision)
            return queue.size
        }
    }

    private fun getTransientPendingEntries(targetUuid: UUID): List<PendingEntryView> {
        val queue = transientByTarget[targetUuid] ?: return emptyList()
        synchronized(queue) {
            cleanupExpiredLocked(targetUuid, queue)
            val entries = queue.map {
                PendingEntryView(
                    id = it.id,
                    type = it.type,
                    worldUuid = it.worldUuid,
                    actorUuid = it.actorUuid,
                    createdAt = it.createdAt,
                    actionCode = it.actionCode,
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
            cleanupExpiredLocked(targetUuid, queue)
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
            cleanupExpiredLocked(targetUuid, queue)
            val latest = if (queue.isEmpty()) null else queue.removeFirst()
            if (queue.isEmpty()) {
                transientByTarget.remove(targetUuid)
            }
            return latest
        }
    }

    private fun cleanupExpiredLocked(targetUuid: UUID, queue: ArrayDeque<PendingDecision>) {
        val now = System.currentTimeMillis()
        val iter = queue.iterator()
        while (iter.hasNext()) {
            val item = iter.next()
            if (item.expiresAt < now) {
                expiredCodesByTarget
                    .computeIfAbsent(targetUuid) { ConcurrentHashMap() }[item.actionCode] = now
                iter.remove()
            }
        }
        cleanupExpiredCodeRecords(targetUuid)
    }

    private fun hasPendingVisitRequest(targetUuid: UUID, requesterUuid: UUID, worldUuid: UUID): Boolean {
        val queue = transientByTarget[targetUuid] ?: return false
        synchronized(queue) {
            cleanupExpiredLocked(targetUuid, queue)
            return queue.any {
                it is VisitRequestDecision &&
                    it.requesterUuid == requesterUuid &&
                    it.worldUuid == worldUuid
            }
        }
    }

    private fun allocateActionCode(targetUuid: UUID): String? {
        val used = plugin.pendingInteractionRepository.findByTarget(targetUuid)
            .mapTo(mutableSetOf()) { it.actionCode }
        val queue = transientByTarget[targetUuid]
        if (queue != null) {
            synchronized(queue) {
                cleanupExpiredLocked(targetUuid, queue)
                queue.mapTo(used) { it.actionCode }
            }
        }
        return actionCodeAllocator.allocate(used)
    }

    private fun cleanupExpiredCodeRecords(targetUuid: UUID) {
        val records = expiredCodesByTarget[targetUuid] ?: return
        val threshold = System.currentTimeMillis() - EXPIRED_CODE_RETENTION_MILLIS
        records.entries.removeIf { it.value < threshold }
        if (records.isEmpty()) {
            expiredCodesByTarget.remove(targetUuid)
        }
    }

    companion object {
        private const val EXPIRED_CODE_RETENTION_MILLIS = 5 * 60 * 1000L
    }

    private fun handleMeetRequestAccept(target: Player, requesterUuid: UUID) {
        val requester = Bukkit.getPlayer(requesterUuid)
        if (requester == null || !requester.isOnline) {
            target.sendMessage(plugin.languageManager.getMessage(target, MyworldMessagesKeys.MESSAGES_MEET_REQUESTER_OFFLINE))
            return
        }

        val worldData = plugin.worldConfigRepository.findByWorldName(target.world.name)
        if (worldData == null) {
            target.sendMessage(plugin.languageManager.getMessage(target, MyworldMessagesKeys.MESSAGES_MEET_NOT_IN_VALID_WORLD))
            return
        }

        plugin.worldService.teleportToWorld(requester, worldData.uuid) {
            target.sendMessage(
                plugin.languageManager.getMessage(
                    target,
                    MyworldMessagesKeys.MESSAGES_MEET_REQUEST_ACCEPTED,
                    mapOf("player" to requester.name)
                )
            )
            requester.sendMessage(
                plugin.languageManager.getMessage(
                    requester,
                    MyworldMessagesKeys.MESSAGES_MEET_REQUEST_ACCEPTED_BY_TARGET,
                    mapOf("player" to target.name)
                )
            )

            // 訪問者統計はAccessControlListenerで一元管理
        }
    }

    private fun handleVisitRequestAccept(target: Player, requesterUuid: UUID, worldUuid: UUID) {
        val requester = Bukkit.getPlayer(requesterUuid)
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
        if (requester == null || !requester.isOnline) {
            target.sendMessage(plugin.languageManager.getMessage(target, MyworldMessagesKeys.MESSAGES_VISIT_REQUEST_TARGET_OFFLINE))
            return
        }
        if (worldData == null || worldData.isArchived) {
            target.sendMessage(plugin.languageManager.getMessage(target, CommonKeys.GENERAL_WORLD_NOT_FOUND))
            return
        }

        plugin.worldService.teleportToWorld(requester, worldUuid) {
            requester.sendMessage(
                plugin.languageManager.getMessage(
                    requester,
                    MyworldMessagesKeys.MESSAGES_VISIT_REQUEST_ACCEPTED,
                    mapOf("owner" to target.name, "world" to worldData.name)
                )
            )
            target.sendMessage(
                plugin.languageManager.getMessage(
                    target,
                    MyworldMessagesKeys.MESSAGES_VISIT_REQUEST_ACCEPTED_BY_TARGET,
                    mapOf("player" to requester.name, "world" to worldData.name)
                )
            )
        }
    }
}

/** 一括取消は3つの識別子すべてが一致する招待だけを対象にします。 */
internal fun worldInviteCancellationMatches(
    actualDecisionId: UUID,
    actualActorUuid: UUID,
    actualWorldUuid: UUID,
    expectedDecisionId: UUID,
    expectedActorUuid: UUID,
    expectedWorldUuid: UUID,
): Boolean =
    actualDecisionId == expectedDecisionId &&
        actualActorUuid == expectedActorUuid &&
        actualWorldUuid == expectedWorldUuid
