package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
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
        
        // 最新の名前を記録
        stats.lastName = player.name
        plugin.playerStatsRepository.save(stats)

        val pendingCount = plugin.pendingDecisionManager.getPersistentPendingCount(player.uniqueId)
        if (pendingCount > 0) {
            val latest = plugin.pendingDecisionManager.getLatestPersistentCreatedAt(player.uniqueId)
            val latestText = latest?.let {
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(it))
            } ?: plugin.languageManager.getMessage(player, "general.unknown")

            player.sendMessage(
                plugin.languageManager.getMessage(
                    player,
                    "messages.myworld_pending_login_notice",
                    mapOf("count" to pendingCount, "datetime" to latestText)
                )
            )
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        
        // 最終オンライン日を更新して保存
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val now = java.time.LocalDate.now()
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        stats.lastOnline = now.format(formatter)
        plugin.playerStatsRepository.save(stats)
        
        // キャッシュから削除してメモリを節約
        plugin.playerStatsRepository.uncache(player.uniqueId)
    }
}
