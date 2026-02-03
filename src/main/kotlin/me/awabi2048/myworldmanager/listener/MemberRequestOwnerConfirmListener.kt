package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.util.ItemTag
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent

class MemberRequestOwnerConfirmListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val item = event.currentItem ?: return
        val tag = ItemTag.getType(item) ?: return

        when (tag) {
            "member_request_owner_yes" -> {
                event.isCancelled = true
                val key = ItemTag.getString(item, "key") ?: return
                val request = plugin.memberRequestManager.getRequest(key)
                if (request != null) {
                    plugin.memberRequestManager.handleApproval(player, request.requestorUuid, request.worldUuid)
                }
                player.closeInventory()
                plugin.soundManager.playClickSound(player, item)
            }
            "member_request_owner_no" -> {
                event.isCancelled = true
                val key = ItemTag.getString(item, "key") ?: return
                val request = plugin.memberRequestManager.getRequest(key)
                if (request != null) {
                    plugin.memberRequestManager.handleRejection(player, request.requestorUuid, request.worldUuid)
                }
                player.closeInventory()
                plugin.soundManager.playActionSound(player, "member_request", "rejected")
            }
        }
    }

    @EventHandler
    fun onDialogResponse(event: io.papermc.paper.event.player.PlayerCustomClickEvent) {
        val identifierStr = event.identifier.asString()
        val conn = event.commonConnection as? io.papermc.paper.connection.PlayerGameConnection ?: return
        val player = conn.player

        if (identifierStr.startsWith("mwm:confirm/member_request_owner_approve/")) {
            me.awabi2048.myworldmanager.gui.DialogConfirmManager.safeCloseDialog(player)
            val key = identifierStr.substringAfter("mwm:confirm/member_request_owner_approve/")
            val request = plugin.memberRequestManager.getRequest(key)
            if (request != null) {
                plugin.memberRequestManager.handleApproval(player, request.requestorUuid, request.worldUuid)
            }
            plugin.soundManager.playClickSound(player, null)
        } else if (identifierStr.startsWith("mwm:confirm/member_request_owner_reject/")) {
            me.awabi2048.myworldmanager.gui.DialogConfirmManager.safeCloseDialog(player)
            val key = identifierStr.substringAfter("mwm:confirm/member_request_owner_reject/")
            val request = plugin.memberRequestManager.getRequest(key)
            if (request != null) {
                plugin.memberRequestManager.handleRejection(player, request.requestorUuid, request.worldUuid)
            }
            plugin.soundManager.playActionSound(player, "member_request", "rejected")
        }
    }
}
