package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.gui.ConfirmationMenuGui
import me.awabi2048.myworldmanager.util.ItemTag
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent

class ConfirmationMenuListener : Listener {

    private val pendingMessage = "§7保留しました。/myworld メニューから、保留中の申請などを確認できます。"

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val holder = event.view.topInventory.holder as? ConfirmationMenuGui.ConfirmationMenuHolder ?: return

        event.isCancelled = true
        if (event.clickedInventory != event.view.topInventory) return

        val item = event.currentItem ?: return
        when (ItemTag.getType(item)) {
            ItemTag.TYPE_GUI_CONFIRM -> {
                holder.resolved = true
                player.closeInventory()
                holder.onConfirm()
            }
            ItemTag.TYPE_GUI_CANCEL -> {
                holder.resolved = true
                player.closeInventory()
                holder.onCancel()
            }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val holder = event.view.topInventory.holder as? ConfirmationMenuGui.ConfirmationMenuHolder ?: return
        if (holder.resolved) return
        event.player.sendMessage(pendingMessage)
    }
}
