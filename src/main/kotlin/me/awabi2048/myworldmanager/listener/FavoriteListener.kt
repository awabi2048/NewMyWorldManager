package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.DialogConfirmManager
import io.papermc.paper.event.player.PlayerCustomClickEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class FavoriteListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler
    fun onCustomClick(event: PlayerCustomClickEvent) {
        val conn = event.commonConnection as? io.papermc.paper.connection.PlayerGameConnection ?: return
        val player = conn.player
        val id = event.identifier.asString()

        if (id == "mwm:confirm/favorite_cancel") {
            DialogConfirmManager.safeCloseDialog(player)
            plugin.soundManager.playClickSound(player, null, "favorite")
            plugin.menuEntryRouter.openFavoriteList(
                player,
                0,
                returnToFavoriteMenu = plugin.favoriteSessionManager.getSession(player.uniqueId).returnToFavoriteMenu
            )
            return
        }

        if (!id.startsWith("mwm:confirm/favorite_remove/")) return

        DialogConfirmManager.safeCloseDialog(player)
        val uuid = try {
            java.util.UUID.fromString(id.substringAfter("mwm:confirm/favorite_remove/"))
        } catch (e: Exception) {
            return
        }
        val worldData = plugin.worldConfigRepository.findByUuid(uuid) ?: return
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)

        if (stats.favoriteWorlds.containsKey(uuid)) {
            stats.favoriteWorlds.remove(uuid)
            worldData.favorite = (worldData.favorite - 1).coerceAtLeast(0)
            plugin.playerStatsRepository.save(stats)
            plugin.worldConfigRepository.save(worldData)
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.favorite_removed"))
            plugin.soundManager.playActionSound(player, "favorite", "favorite_remove")
        }
        plugin.menuEntryRouter.openFavoriteList(
            player,
            0,
            returnToFavoriteMenu = plugin.favoriteSessionManager.getSession(player.uniqueId).returnToFavoriteMenu
        )
    }
}
