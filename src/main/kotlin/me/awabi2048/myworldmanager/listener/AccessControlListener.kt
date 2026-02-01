package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PublishLevel
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import me.awabi2048.myworldmanager.util.PlayerTag

class AccessControlListener(private val plugin: MyWorldManager) : Listener {

    private val repository = plugin.worldConfigRepository

    @EventHandler
    fun onWorldTeleport(event: PlayerTeleportEvent) {
        val player = event.player
        val toWorld = event.to.world ?: return
        val worldData = repository.findByWorldName(toWorld.name) ?: return
        val lang = plugin.languageManager
        val previewSessionManager = plugin.previewSessionManager

        // プレビュー中の場合は通知・アナウンスをスキップ
        if (previewSessionManager.isInPreview(player)) return

        // アーカイブ中チェック
        if (worldData.isArchived) {
            player.sendMessage(lang.getMessage(player, "messages.archive_access_denied"))
            event.isCancelled = true
            return
        }

        // メンバー判定（所有者、メンバー、モデレーター）
        val isMember = worldData.owner == player.uniqueId || 
                       worldData.members.contains(player.uniqueId) || 
                       worldData.moderators.contains(player.uniqueId)

        // 権限チェック（管理者・メンバー以外）
        if (!player.hasPermission("myworldmanager.admin") && !isMember) {
            if (worldData.publishLevel == PublishLevel.LOCKED) {
                player.sendMessage(lang.getMessage(player, "error.portal_dest_locked"))
                event.isCancelled = true
                return
            } else if (worldData.publishLevel == PublishLevel.PRIVATE) {
                // プライベート設定の場合、明示的な招待がないと入れない
                // 現状の仕様では「訪問ワープ自体に招待チェックを入れる」か「ここで弾く」か。
                // SettingsSessionの方でワープを許可する仕組みが必要。
                // ここではシンプルにPublishLevelの基本挙動のみを扱う。
            }
        }

    }

    private fun handleWorldEntry(player: Player, worldData: me.awabi2048.myworldmanager.model.WorldData) {
        val isMember = worldData.owner == player.uniqueId || 
                       worldData.members.contains(player.uniqueId) || 
                       worldData.moderators.contains(player.uniqueId)

        if (!isMember) {
            // 訪問者統計の更新 (同日の重複カウント防止)
            if (PlayerTag.shouldCountVisit(player, worldData.uuid)) {
                worldData.recentVisitors[0]++
                plugin.worldConfigRepository.save(worldData)
            }

            // アナウンスメッセージ送信
            plugin.worldService.sendAnnouncementMessage(player, worldData)

            // 所有者への通知
            if (worldData.notificationEnabled) {
                val owner = org.bukkit.Bukkit.getPlayer(worldData.owner)
                if (owner != null && owner.isOnline) {
                    val lang = plugin.languageManager
                    owner.sendMessage(lang.getMessage(owner, "messages.visitor_notified", mapOf(
                        "player" to player.name,
                        "world" to worldData.name
                    )))
                }
            }
        }
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        val player = event.player
        if (plugin.previewSessionManager.isInPreview(player)) return
        val worldData = repository.findByWorldName(player.world.name) ?: return
        
        // 共通処理呼び出し
        handleWorldEntry(player, worldData)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        if (plugin.previewSessionManager.isInPreview(player)) return
        val worldData = repository.findByWorldName(player.world.name) ?: return
        val lang = plugin.languageManager

        // 再ログイン時もアーカイブ中ならスポーンへ飛ばす等の処理が必要かもしれない
        if (worldData.isArchived && !player.hasPermission("myworldmanager.admin")) {
            val evacLoc = plugin.worldService.getEvacuationLocation()
            player.teleport(evacLoc)
            player.sendMessage(lang.getMessage(player, "messages.archive_access_denied"))
        } else {
            // 共通処理呼び出し
            handleWorldEntry(player, worldData)
        }
    }
}
