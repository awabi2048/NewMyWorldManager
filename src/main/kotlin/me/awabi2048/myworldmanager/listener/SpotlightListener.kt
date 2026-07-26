package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class SpotlightListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler
    fun onDialogResponse(event: io.papermc.paper.event.player.PlayerCustomClickEvent) {
        val identifierStr = event.identifier.asString()
        val conn = event.commonConnection as? io.papermc.paper.connection.PlayerGameConnection ?: return
        val player = conn.player
        val lang = plugin.languageManager

        if (identifierStr.startsWith("mwm:confirm/spotlight_add/")) {
            me.awabi2048.myworldmanager.gui.DialogConfirmManager.safeCloseDialog(player)
            val uuidStr = identifierStr.substringAfter("mwm:confirm/spotlight_add/")
            val uuid = try { java.util.UUID.fromString(uuidStr) } catch (e: Exception) { return }
            val worldData = plugin.worldConfigRepository.findByUuid(uuid) ?: return

            if (plugin.spotlightRepository.isSpotlight(uuid)) {
                player.sendMessage(lang.getMessage(player, "error.spotlight_already_registered"))
            } else {
                val success = plugin.spotlightRepository.add(uuid)
                if (success) {
                    player.sendMessage(lang.getMessage(player, "messages.spotlight_added", mapOf("world" to worldData.name)))
                    plugin.soundManager.playClickSound(player, null)
                } else {
                    player.sendMessage(lang.getMessage(player, "error.spotlight_limit_reached"))
                }
            }
            // Dialog closed automatically, maybe reopen discovery? 
            // Dialog closing is handled by client/server handshake usually, but safeCloseDialog ensures state.
            // Opening another GUI immediately after dialog might be tricky or fine.
            // Let's open discovery GUI as per original flow.
            plugin.menuEntryRouter.openDiscovery(player)

        } else if (identifierStr.startsWith("mwm:confirm/spotlight_remove/")) {
            me.awabi2048.myworldmanager.gui.DialogConfirmManager.safeCloseDialog(player)
            val uuidStr = identifierStr.substringAfter("mwm:confirm/spotlight_remove/")
            val uuid = try { java.util.UUID.fromString(uuidStr) } catch (e: Exception) { return }
            val worldData = plugin.worldConfigRepository.findByUuid(uuid) ?: return

            plugin.spotlightRepository.remove(uuid)
            player.sendMessage(lang.getMessage(player, "messages.spotlight_removed", mapOf("world" to worldData.name)))
            plugin.soundManager.playClickSound(player, null)
            plugin.menuEntryRouter.openDiscovery(player)
        }
    }
}
