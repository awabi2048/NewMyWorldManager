package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.util.ItemConverter
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerJoinEvent

class ItemConversionListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        // プレイヤーのインベントリを走査して変換
        ItemConverter.convertInventory(player.inventory, plugin)
    }

    @EventHandler
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val player = event.player
        val inventory = event.inventory
        
        // プレイヤー自身のインベントリを走査
        ItemConverter.convertInventory(player.inventory, plugin)
        
        // 開いた先のインベントリを走査（チェストなど）
        ItemConverter.convertInventory(inventory, plugin)
    }
}
