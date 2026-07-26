package me.awabi2048.myworldmanager.listener

import io.papermc.paper.connection.PlayerGameConnection
import io.papermc.paper.event.player.PlayerCustomClickEvent
import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class PlayerWorldListener(private val plugin: MyWorldManager) : Listener {
    @EventHandler
    fun onInviteDialogResponse(event: PlayerCustomClickEvent) {
        val connection = event.commonConnection as? PlayerGameConnection ?: return
        plugin.pendingInteractionGui.handleDialogResponse(connection.player, event.identifier)
    }
}
