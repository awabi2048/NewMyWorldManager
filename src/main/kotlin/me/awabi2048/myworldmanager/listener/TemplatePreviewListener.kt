package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.plugin.java.JavaPlugin

/**
 * テンプレートプレビュー中のイベントを制御するリスナー
 */
class TemplatePreviewListener : Listener {

    private val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)

    /**
     * スニーク（Shift）でプレビューをキャンセル
     */
    @EventHandler(priority = EventPriority.HIGH)
    fun onSneak(event: PlayerToggleSneakEvent) {
        val player = event.player
        
        if (!event.isSneaking) return
        if (!plugin.previewSessionManager.isInPreview(player)) return

        plugin.previewSessionManager.endPreview(player, true)
    }

    /**
     * ログアウト時にプレビューを終了（復元情報をファイルに保存）
     */
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        
        if (plugin.previewSessionManager.isInPreview(player)) {
            plugin.previewSessionManager.handlePlayerQuit(player.uniqueId)
        }
    }

    /**
     * ログイン時に復元処理を実行
     */
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        
        // 少し遅延させてから復元処理を実行（プレイヤーの初期化完了を待つ）
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            plugin.previewSessionManager.handlePlayerJoin(player)
        }, 20L) // 1秒後
    }
}
