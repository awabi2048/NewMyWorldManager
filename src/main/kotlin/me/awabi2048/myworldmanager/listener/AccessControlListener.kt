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
class AccessControlListener(private val repository: WorldConfigRepository) : Listener {

    @EventHandler
    fun onTeleport(event: PlayerTeleportEvent) {
        val toWorld = event.to.world
        val fromWorld = event.from.world

        // 同一ワールド内の移動は無視
        if (toWorld == null || toWorld == fromWorld) return

        // 管理対象ワールドかチェック
        val worldData = repository.findByWorldName(toWorld.name) ?: return
        val player = event.player
        val playerUuid = player.uniqueId

        // 管理者は常に許可 (PermissionManager.checkPermission を使用)
        if (PermissionManager.checkPermission(player, PermissionManager.ADMIN)) return

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
            player.sendMessage("§cこのワールドへの入場権限がありません。")
            return
        }

        // 訪問通知の処理
        if (worldData.notificationEnabled) {
            val isMember = worldData.owner == playerUuid || 
                          worldData.moderators.contains(playerUuid) || 
                          worldData.members.contains(playerUuid)
            
            if (!isMember) {
                val notification = net.kyori.adventure.text.Component.text()
                    .append(net.kyori.adventure.text.Component.text("[", net.kyori.adventure.text.format.NamedTextColor.GRAY))
                    .append(net.kyori.adventure.text.Component.text("!", net.kyori.adventure.text.format.NamedTextColor.RED))
                    .append(net.kyori.adventure.text.Component.text("] ", net.kyori.adventure.text.format.NamedTextColor.GRAY))
                    .append(net.kyori.adventure.text.Component.text(player.name, net.kyori.adventure.text.format.NamedTextColor.YELLOW))
                    .append(net.kyori.adventure.text.Component.text(" さんがあなたのワールド「", net.kyori.adventure.text.format.NamedTextColor.WHITE))
                    .append(net.kyori.adventure.text.Component.text(worldData.name, net.kyori.adventure.text.format.NamedTextColor.GREEN))
                    .append(net.kyori.adventure.text.Component.text("」に訪問しました。", net.kyori.adventure.text.format.NamedTextColor.WHITE))
                    .build()

                // オーナー、モデレーター、メンバーに通知
                val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(me.awabi2048.myworldmanager.MyWorldManager::class.java)
                val allMembers = (worldData.members + worldData.moderators + worldData.owner).toSet()
                allMembers.forEach { memberUuid ->
                    val memberPlayer = org.bukkit.Bukkit.getPlayer(memberUuid) ?: return@forEach
                    val stats = plugin.playerStatsRepository.findByUuid(memberUuid)
                    if (stats.visitorNotificationEnabled) {
                        memberPlayer.sendMessage(notification)
                    }
                }
            }
        }
    }
}
