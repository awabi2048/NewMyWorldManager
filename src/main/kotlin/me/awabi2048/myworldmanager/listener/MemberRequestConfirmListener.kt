package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class MemberRequestConfirmListener(private val plugin: MyWorldManager) : Listener {

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
