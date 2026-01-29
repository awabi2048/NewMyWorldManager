package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.repository.WorldConfigRepository
import me.awabi2048.myworldmanager.util.PermissionManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerTeleportEvent
import java.util.UUID

/**
 * プレイヤーのワールド間移動を監視し、入場制限を行うリスナー
 */
class AccessControlListener(private val plugin: me.awabi2048.myworldmanager.MyWorldManager) : Listener {

    @EventHandler
    fun onTeleport(event: PlayerTeleportEvent) {
        val toWorld = event.to.world
        val fromWorld = event.from.world

        // 同一ワールド内の移動は無視
        if (toWorld == null || toWorld == fromWorld) return

        // 管理対象ワールドかチェック
        val repository = plugin.worldConfigRepository
        val worldData = repository.findByWorldName(toWorld.name) ?: return
        val player = event.player
        val playerUuid = player.uniqueId
        val lang = plugin.languageManager

        // 管理者は常に許可 (PermissionManager.checkPermission を使用)
        if (me.awabi2048.myworldmanager.util.PermissionManager.checkPermission(player, me.awabi2048.myworldmanager.util.PermissionManager.ADMIN)) return

        // 所有者は常に許可
        if (worldData.owner == playerUuid) return

        // 公開レベルに基づいた入場許可判定
        val allowed = when (worldData.publishLevel) {
            PublishLevel.PUBLIC -> true
            PublishLevel.FRIEND -> {
                // メンバーまたはモデレーターなら許可
                worldData.members.contains(playerUuid) || worldData.moderators.contains(playerUuid)
            }
            PublishLevel.PRIVATE -> {
                // メンバーまたはモデレーターのみ許可
                worldData.members.contains(playerUuid) || worldData.moderators.contains(playerUuid)
            }
            PublishLevel.LOCKED -> {
                // メンバーまたはモデレーターなら許可
                worldData.members.contains(playerUuid) || worldData.moderators.contains(playerUuid)
            }
        }

        if (!allowed) {
            event.isCancelled = true
            player.sendMessage(lang.getMessage(player, "messages.access_denied_world"))
            return
        }

        // 訪問通知の処理
        if (worldData.notificationEnabled) {
            val isMember = worldData.owner == playerUuid || 
                          worldData.moderators.contains(playerUuid) || 
                          worldData.members.contains(playerUuid)
            
            if (!isMember) {
                // オーナー、モデレーター、メンバーに通知
                val allMembers = (worldData.members + worldData.moderators + worldData.owner).toSet()
                allMembers.forEach { memberUuid ->
                    val memberPlayer = org.bukkit.Bukkit.getPlayer(memberUuid) ?: return@forEach
                    val stats = plugin.playerStatsRepository.findByUuid(memberUuid)
                    if (stats.visitorNotificationEnabled) {
                        memberPlayer.sendMessage(lang.getMessage(memberPlayer, "messages.visitor_notified", mapOf("player" to player.name, "world" to worldData.name)))
                    }
                }
            }
        }
    }
}
