package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PlayerDataListener : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        
        // データを取得
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        
        // 最新の名前を記録。移行待ちの場合は保存をスキップして表示を優先する。
        val originalName = stats.lastName
        stats.lastName = player.name
        try {
            plugin.playerStatsRepository.save(stats)
        } catch (failure: IllegalStateException) {
            if (me.awabi2048.myworldmanager.util.MigrationFeedback.isMigrationRequired(failure)) {
                stats.lastName = originalName
                plugin.logger.warning("[PlayerDataListener] プレイヤー名の保存をスキップしました（移行待ち）: ${player.uniqueId} ${failure.message}")
            } else {
                throw failure
            }
        }

        // 管理者向け移行通知: 古いデータが残っている場合はログイン時に案内する
        if (player.hasPermission("myworldmanager.admin") && MyWorldManagerApi.isMigrationPending()) {
            player.sendMessage(plugin.languageManager.getMessage(player, com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys.MESSAGES_MIGRATION_ADMIN_NOTICE))
        }

        plugin.pendingNotificationService.resendPersistent(player)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        
        // 最終オンライン情報はMWMのプレイヤー統計へ秒精度で集約し、GUIも同じ値を参照する。
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val originalLastOnline = stats.lastOnline
        stats.lastOnline = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        try {
            plugin.playerStatsRepository.save(stats)
        } catch (failure: IllegalStateException) {
            if (me.awabi2048.myworldmanager.util.MigrationFeedback.isMigrationRequired(failure)) {
                stats.lastOnline = originalLastOnline
                plugin.logger.warning("[PlayerDataListener] 最終オンラインの保存をスキップしました（移行待ち）: ${player.uniqueId} ${failure.message}")
            } else {
                throw failure
            }
        }
        
        // キャッシュから削除してメモリを節約
        plugin.playerStatsRepository.uncache(player.uniqueId)
        plugin.clearTransientPlayerMenuState(player.uniqueId)
    }
}
