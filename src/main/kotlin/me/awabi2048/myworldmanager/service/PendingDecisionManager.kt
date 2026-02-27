package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PendingDecisionManager(private val plugin: MyWorldManager) {

    private sealed interface PendingDecision {
        val expiresAt: Long
    }

    private data class WorldInviteDecision(
        val worldUuid: UUID,
        val senderUuid: UUID?,
        override val expiresAt: Long
    ) : PendingDecision

    private data class MemberInviteDecision(
        val worldUuid: UUID,
        val senderUuid: UUID,
        override val expiresAt: Long
    ) : PendingDecision

    private data class MemberRequestDecision(
        val worldUuid: UUID,
        val requestorUuid: UUID,
        override val expiresAt: Long
    ) : PendingDecision

    private data class MeetRequestDecision(
        val requesterUuid: UUID,
        override val expiresAt: Long
    ) : PendingDecision

    private val pendingByTarget = ConcurrentHashMap<UUID, ArrayDeque<PendingDecision>>()

    fun enqueueWorldInvite(target: Player, worldUuid: UUID, senderUuid: UUID?, timeoutSeconds: Long): Int {
        return enqueue(
            target.uniqueId,
            WorldInviteDecision(
                worldUuid = worldUuid,
                senderUuid = senderUuid,
                expiresAt = System.currentTimeMillis() + (timeoutSeconds * 1000L)
            )
        )
    }

    fun enqueueMemberInvite(target: Player, worldUuid: UUID, senderUuid: UUID, timeoutSeconds: Long): Int {
        return enqueue(
            target.uniqueId,
            MemberInviteDecision(
                worldUuid = worldUuid,
                senderUuid = senderUuid,
                expiresAt = System.currentTimeMillis() + (timeoutSeconds * 1000L)
            )
        )
    }

    fun enqueueMemberRequest(owner: Player, worldUuid: UUID, requestorUuid: UUID, timeoutSeconds: Long): Int {
        return enqueue(
            owner.uniqueId,
            MemberRequestDecision(
                worldUuid = worldUuid,
                requestorUuid = requestorUuid,
                expiresAt = System.currentTimeMillis() + (timeoutSeconds * 1000L)
            )
        )
    }

    fun enqueueMeetRequest(target: Player, requesterUuid: UUID, timeoutSeconds: Long): Int {
        return enqueue(
            target.uniqueId,
            MeetRequestDecision(
                requesterUuid = requesterUuid,
                expiresAt = System.currentTimeMillis() + (timeoutSeconds * 1000L)
            )
        )
    }

    fun resolveLatest(target: Player, accept: Boolean): Boolean {
        val lang = plugin.languageManager
        val decision = pollLatestValid(target.uniqueId)
        if (decision == null) {
            target.sendMessage(lang.getMessage(target, "messages.myworld_pending_none"))
            return false
        }

        when (decision) {
            is WorldInviteDecision -> {
                if (accept) {
                    plugin.worldService.teleportToWorld(target, decision.worldUuid)
                    target.sendMessage(lang.getMessage(target, "messages.warp_invite_success"))
                } else {
                    target.sendMessage(lang.getMessage(target, "messages.invite_declined"))
                }
            }

            is MemberInviteDecision -> {
                if (accept) {
                    plugin.memberInviteManager.handleMemberInviteAcceptDirect(
                        target,
                        decision.worldUuid,
                        decision.senderUuid
                    )
                } else {
                    target.sendMessage(lang.getMessage(target, "messages.member_invite_declined"))
                    Bukkit.getPlayer(decision.senderUuid)?.let { sender ->
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

            is MemberRequestDecision -> {
                if (accept) {
                    plugin.memberRequestManager.handleApprovalDirect(
                        target,
                        decision.requestorUuid,
                        decision.worldUuid
                    )
                } else {
                    plugin.memberRequestManager.handleRejectionDirect(
                        target,
                        decision.requestorUuid,
                        decision.worldUuid
                    )
                }
            }

            is MeetRequestDecision -> {
                if (accept) {
                    handleMeetRequestAccept(target, decision.requesterUuid)
                } else {
                    target.sendMessage(lang.getMessage(target, "messages.meet.request_denied"))
                    Bukkit.getPlayer(decision.requesterUuid)?.let { requester ->
                        requester.sendMessage(
                            lang.getMessage(
                                requester,
                                "messages.meet.request_denied_by_target",
                                mapOf("player" to target.name)
                            )
                        )
                    }
                }
            }
        }

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

    fun sendPendingHint(target: Player, count: Int) {
        target.sendMessage(
            plugin.languageManager.getMessage(
                target,
                "messages.myworld_pending_hint",
                mapOf("count" to count)
            )
        )
    }

    fun getPendingCount(targetUuid: UUID): Int {
        val queue = pendingByTarget[targetUuid] ?: return 0
        synchronized(queue) {
            cleanupExpiredLocked(queue)
            val size = queue.size
            if (size == 0) {
                pendingByTarget.remove(targetUuid)
            }
            return size
        }
    }

    private fun enqueue(targetUuid: UUID, decision: PendingDecision): Int {
        val queue = pendingByTarget.computeIfAbsent(targetUuid) { ArrayDeque() }
        synchronized(queue) {
            cleanupExpiredLocked(queue)
            queue.addFirst(decision)
            return queue.size
        }
    }

    private fun pollLatestValid(targetUuid: UUID): PendingDecision? {
        val queue = pendingByTarget[targetUuid] ?: return null
        synchronized(queue) {
            cleanupExpiredLocked(queue)
            val latest = if (queue.isEmpty()) null else queue.removeFirst()
            if (queue.isEmpty()) {
                pendingByTarget.remove(targetUuid)
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

        plugin.worldService.teleportToWorld(requester, worldData.uuid)
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
