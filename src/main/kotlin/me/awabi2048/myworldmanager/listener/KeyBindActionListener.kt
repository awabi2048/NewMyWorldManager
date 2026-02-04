package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.util.ItemTag
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerSwapHandItemsEvent

/**
 * Fキー（オフハンド切り替え）の検出リスナー
 * 
 * PlayerWorldGui 表示中にワールドアイコン上でFキーを押下すると、
 * そのワールドを並び順の一番前に移動する
 */
class KeyBindActionListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPlayerSwapHandItems(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        val openInventory = player.openInventory
        
        // PlayerWorldGui が開かれているか確認
        if (openInventory.topInventory.holder !is me.awabi2048.myworldmanager.gui.PlayerWorldGui.PlayerWorldGuiHolder) {
            return
        }

        // Fキーの標準動作をキャンセル
        event.isCancelled = true
        
        // セッションから最後にホバーしたアイテムを取得
        val session = plugin.playerWorldSessionManager.getSession(player.uniqueId)
        val hoveredItem = session.lastHoveredItem
        
        if (hoveredItem == null || hoveredItem.type.isAir) {
            player.sendMessage("§7ワールドアイコンの上でFキーを押してください。")
            return
        }
        
        handleWorldReorder(player, hoveredItem, session.currentPage)
    }

    /**
     * ワールド順序の変更処理
     */
    private fun handleWorldReorder(player: Player, item: org.bukkit.inventory.ItemStack, currentPage: Int) {
        val type = ItemTag.getType(item)
        
        // ワールドアイコンでない場合は無視（装飾アイテムなど）
        if (type == ItemTag.TYPE_GUI_DECORATION || 
            type == ItemTag.TYPE_GUI_NAV_PREV || 
            type == ItemTag.TYPE_GUI_NAV_NEXT ||
            type == ItemTag.TYPE_GUI_BACK ||
            type == ItemTag.TYPE_GUI_CREATION_BUTTON ||
            type == ItemTag.TYPE_GUI_PLAYER_STATS ||
            type == ItemTag.TYPE_GUI_USER_SETTING_CRITICAL_VISIBILITY ||
            type == ItemTag.TYPE_GUI_RETURN ||
            type == ItemTag.TYPE_GUI_INVITE) {
            player.sendMessage("§7ワールドアイコンの上でFキーを押してください。")
            return
        }
        
        val worldUuid = ItemTag.getWorldUuid(item)
        if (worldUuid == null) {
            player.sendMessage("§cワールド情報の取得に失敗しました。")
            return
        }
        
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
        if (worldData == null) {
            player.sendMessage("§cワールドが存在しません。")
            return
        }
        
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        
        // worldDisplayOrder から該当UUIDを削除
        stats.worldDisplayOrder.remove(worldUuid)
        
        // 先頭に追加
        stats.worldDisplayOrder.add(0, worldUuid)
        
        // 保存
        plugin.playerStatsRepository.save(stats)
        
        val lang = plugin.languageManager
        player.sendMessage("§a「${worldData.name}」を一番上に移動しました。")
        
        // サウンド再生
        plugin.soundManager.playClickSound(player, item, "player_world")
        
        // GUI を再表示（同じページ番号で）
        plugin.playerWorldGui.open(player, currentPage)
    }
}
