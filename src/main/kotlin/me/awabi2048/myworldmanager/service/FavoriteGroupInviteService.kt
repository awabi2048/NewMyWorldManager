package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** お気に入りワールドへの同行者招待を、対象抽出から一括取消まで一つの境界で管理します。 */
class FavoriteGroupInviteService(private val plugin: MyWorldManager) {
    data class Batch(
        val id: UUID,
        val senderUuid: UUID,
        val worldUuid: UUID,
        val decisionsByTarget: Map<UUID, UUID>,
        val expiresAt: Long,
    )

    sealed interface SendResult {
        data class Sent(val batch: Batch, val recipients: List<Player>) : SendResult
        data object NotAllowed : SendResult
        data object NoRecipients : SendResult
    }

    sealed interface CancelResult {
        data class Cancelled(val count: Int) : CancelResult
        data object NotFound : CancelResult
    }

    private val batches = ConcurrentHashMap<UUID, Batch>()

    fun eligibleRecipients(sender: Player, worldData: WorldData): List<Player> {
        if (!canSend(sender, worldData)) return emptyList()
        val visible = plugin.playerVisibilityService.getVisibleOnlinePlayers(sender)
            .mapTo(hashSetOf()) { it.uniqueId }
        return sender.world.players.asSequence()
            .filter { target ->
                favoriteGroupInviteCandidateIsEligible(
                    FavoriteGroupInviteCandidateEligibility(
                        isSender = target.uniqueId == sender.uniqueId,
                        visibleToSender = target.uniqueId in visible,
                        receptionEnabled = plugin.playerStatsRepository.findByUuid(target.uniqueId)
                            .favoriteGroupInvitesEnabled,
                        allowedByPolicy = MyWorldManagerApi.getWorldAccessPolicy()
                            .canReceiveFavoriteGroupInvite(sender, worldData, target),
                        alreadyMember = target.uniqueId == worldData.owner ||
                            target.uniqueId in worldData.moderators ||
                            target.uniqueId in worldData.members,
                        alreadyAtDestination = plugin.worldConfigRepository
                            .findByWorldName(target.world.name)?.uuid == worldData.uuid,
                        alreadyPending = plugin.pendingDecisionManager.getPendingEntries(target.uniqueId).any {
                    it.type == PendingDecisionManager.PendingType.WORLD_INVITE && it.worldUuid == worldData.uuid
                        },
                    ),
                )
            }
            .sortedBy(Player::getName)
            .toList()
    }

    fun send(sender: Player, worldData: WorldData): SendResult {
        cleanupExpired()
        if (!canSend(sender, worldData)) return SendResult.NotAllowed
        val recipients = eligibleRecipients(sender, worldData)
        if (recipients.isEmpty()) return SendResult.NoRecipients
        val timeoutSeconds = plugin.config.getLong("invite.timeout_seconds", 60)
        val decisions = linkedMapOf<UUID, UUID>()
        recipients.forEach { target ->
            val result = plugin.pendingDecisionManager.enqueueWorldInvite(
                target,
                worldData.uuid,
                sender.uniqueId,
                timeoutSeconds,
            )
            decisions[target.uniqueId] = result.id
            plugin.pendingNotificationService.sendFavoriteGroupWorldInvite(
                target,
                result.actionCode,
                sender.uniqueId,
                worldData.uuid,
            )
        }
        val batch = Batch(
            id = UUID.randomUUID(),
            senderUuid = sender.uniqueId,
            worldUuid = worldData.uuid,
            decisionsByTarget = decisions,
            expiresAt = System.currentTimeMillis() + timeoutSeconds * 1000L,
        )
        batches[batch.id] = batch
        plugin.pendingNotificationService.sendFavoriteGroupInviteSummary(sender, batch.id, recipients)
        return SendResult.Sent(batch, recipients)
    }

    fun cancel(sender: Player, batchId: UUID): CancelResult {
        cleanupExpired()
        val batch = batches[batchId]?.takeIf { it.senderUuid == sender.uniqueId }
            ?: return CancelResult.NotFound
        batches.remove(batchId, batch)
        return CancelResult.Cancelled(
            plugin.pendingDecisionManager.cancelWorldInviteBatch(
                batch.senderUuid,
                batch.worldUuid,
                batch.decisionsByTarget,
            ),
        )
    }

    fun clear() = batches.clear()

    fun canSend(sender: Player, worldData: WorldData): Boolean {
        val stats = plugin.playerStatsRepository.findByUuid(sender.uniqueId)
        if (worldData.uuid !in stats.favoriteWorlds || worldData.isArchived) return false
        val isMember = sender.uniqueId == worldData.owner ||
            sender.uniqueId in worldData.moderators || sender.uniqueId in worldData.members
        return MyWorldManagerApi.getWorldAccessPolicy().canInviteFavoriteGroup(sender, worldData, isMember)
    }

    private fun cleanupExpired() {
        val now = System.currentTimeMillis()
        batches.entries.removeIf { it.value.expiresAt < now }
    }
}

internal data class FavoriteGroupInviteCandidateEligibility(
    val isSender: Boolean,
    val visibleToSender: Boolean,
    val receptionEnabled: Boolean,
    val allowedByPolicy: Boolean,
    val alreadyMember: Boolean,
    val alreadyAtDestination: Boolean,
    val alreadyPending: Boolean,
)

/** 対象除外条件を一箇所に固定し、通常招待のSAME_WORLD/BUSY判定が混入するのを防ぎます。 */
internal fun favoriteGroupInviteCandidateIsEligible(state: FavoriteGroupInviteCandidateEligibility): Boolean =
    !state.isSender &&
        state.visibleToSender &&
        state.receptionEnabled &&
        state.allowedByPolicy &&
        !state.alreadyMember &&
        !state.alreadyAtDestination &&
        !state.alreadyPending
