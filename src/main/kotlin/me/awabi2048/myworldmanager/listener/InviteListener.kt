package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.ui.ManagedMenuPresenter

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.util.ItemTag
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import me.awabi2048.myworldmanager.util.cancelWithDebug
import java.util.UUID

class InviteListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler(ignoreCancelled = false)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val view = event.view

        if (view.topInventory.holder !is me.awabi2048.myworldmanager.gui.InviteGui.InviteGuiHolder) {
            return
        }

        event.cancelWithDebug("InviteListener.onInventoryClick: invite GUI click")
        if (event.clickedInventory != view.topInventory) return
        val currentItem = event.currentItem ?: return
        val type = ItemTag.getType(currentItem)
        if (currentItem.type == Material.AIR || type == ItemTag.TYPE_GUI_DECORATION) {
            return
        }

        if (type == ItemTag.TYPE_GUI_RETURN) {
            me.awabi2048.myworldmanager.util.GuiHelper.handleReturnClick(plugin, player, currentItem)
            return
        }

        if (type != ItemTag.TYPE_GUI_INVITE_TARGET_HEAD) {
            return
        }

        val targetUuid = ItemTag.getString(currentItem, "invite_target_uuid")?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        } ?: return

        val targetName = ItemTag.getString(currentItem, "invite_target_name") ?: "?"
        val target = Bukkit.getPlayer(targetUuid)
        if (target == null || !target.isOnline) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.invite_target_offline", mapOf("player" to targetName)))
            ManagedMenuPresenter.close(player)
            return
        }

        plugin.soundManager.playClickSound(player, currentItem, "meet")
        ManagedMenuPresenter.close(player)
        player.performCommand("invite ${target.name}")
    }
}
