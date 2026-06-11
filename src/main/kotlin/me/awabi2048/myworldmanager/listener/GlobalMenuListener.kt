package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.util.GuiHelper
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent

class GlobalMenuListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = plugin.settingsSessionManager.getSession(player) ?: return

        if (!GuiHelper.isPluginGuiInventory(event.view.topInventory)) {
            session.isGuiTransition = false
            return
        }

        if (session.isGuiTransition) {
            session.isGuiTransition = false
        }
    }
}
