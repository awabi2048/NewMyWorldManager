package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.entity.Player

/**
 * オフライン中に届いたメンバー招待を、ログイン時に想起させる実行部です。
 *
 * 判定自体は [OfflineMemberInviteNotificationPolicy] の純粋規則に委譲し、
 * ここでは既存の保留通知文面（MEMBER_INVITE系キー）による送信と、
 * 送信成功時のみの通知済み記録・期限切れIDの整理だけを行います。
 */
class OfflineMemberInviteNotifier(private val plugin: MyWorldManager) {
    fun notifyOnJoin(player: Player) {
        val interactions = runCatching {
            plugin.pendingInteractionRepository.findByTarget(player.uniqueId)
        }.getOrElse { error ->
            plugin.logger.warning("オフラインメンバー招待の取得に失敗しました: player=${player.uniqueId}, error=${error.message}")
            return
        }
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        // 空の結果も正常な確認結果として扱い、期限切れIDを整理します。
        val currentIds = interactions.mapTo(mutableSetOf()) { it.id }
        stats.notifiedOfflineMemberInviteIds.retainAll(currentIds)

        val notifications = OfflineMemberInviteNotificationPolicy.selectUnnotified(
            pendingInteractions = interactions,
            targetUuid = player.uniqueId,
            notifiedInviteIds = stats.notifiedOfflineMemberInviteIds.toSet(),
            worldNameResolver = { worldUuid ->
                runCatching { plugin.worldConfigRepository.findByUuid(worldUuid)?.name }.getOrNull()
            },
        )
        if (notifications.isEmpty()) {
            plugin.playerStatsRepository.save(stats)
            return
        }
        var notified = false
        notifications.forEach { notification ->
            runCatching {
                plugin.pendingNotificationService.send(
                    target = player,
                    type = PendingDecisionManager.PendingType.MEMBER_INVITE,
                    actionCode = notification.actionCode,
                    actorUuid = notification.actorUuid,
                    worldUuid = notification.worldUuid,
                )
            }.onSuccess {
                stats.notifiedOfflineMemberInviteIds.add(notification.inviteId)
                notified = true
            }.onFailure { error ->
                plugin.logger.warning("オフラインメンバー招待通知の送信に失敗しました: invite=${notification.inviteId}, error=${error.message}")
            }
        }
        if (notified) {
            runCatching { plugin.playerStatsRepository.save(stats) }.onFailure { error ->
                plugin.logger.warning("オフラインメンバー招待の通知済み記録に失敗しました: player=${player.uniqueId}, error=${error.message}")
            }
        } else {
            runCatching { plugin.playerStatsRepository.save(stats) }
        }
    }
}
