package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.gui.MemberRequestConfirmGui
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.util.ItemTag
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent

class MemberRequestConfirmListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        event.view.topInventory.holder as? MemberRequestConfirmGui.MemberRequestConfirmGuiHolder ?: return

        event.isCancelled = true
        if (event.clickedInventory != event.view.topInventory) return

        val item = event.currentItem ?: return
        val tag = ItemTag.getType(item) ?: return

        when (tag) {
            ItemTag.TYPE_GUI_CONFIRM -> {
                val worldUuid = ItemTag.getWorldUuid(item) ?: return
                player.closeInventory()
                plugin.memberRequestManager.sendRequest(player, worldUuid)
                plugin.soundManager.playClickSound(player, item)
            }
            ItemTag.TYPE_GUI_CANCEL -> {
                player.closeInventory()
                plugin.soundManager.playActionSound(player, "member_request", "cancel")
            }
        }
    }

    @EventHandler
    fun onDialogResponse(event: io.papermc.paper.event.player.PlayerCustomClickEvent) {
        val identifierStr = event.identifier.asString()
        val conn = event.commonConnection as? io.papermc.paper.connection.PlayerGameConnection ?: return
        val player = conn.player

        if (identifierStr.startsWith("mwm:confirm/member_request_send/")) {
            me.awabi2048.myworldmanager.gui.DialogConfirmManager.safeCloseDialog(player)
            val uuidStr = identifierStr.substringAfter("mwm:confirm/member_request_send/")
            val uuid = try { java.util.UUID.fromString(uuidStr) } catch (e: Exception) { return }
            plugin.memberRequestManager.sendRequest(player, uuid)
            plugin.soundManager.playClickSound(player, null)
        } else if (identifierStr == "mwm:confirm/cancel") {
            me.awabi2048.myworldmanager.gui.DialogConfirmManager.safeCloseDialog(player)
            plugin.soundManager.playActionSound(player, "member_request", "cancel")
        }
    }
}
