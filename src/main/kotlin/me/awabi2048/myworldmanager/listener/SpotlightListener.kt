package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent

class SpotlightListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val view = event.view
        val title = PlainTextComponentSerializer.plainText().serialize(view.title())

        val lang = plugin.languageManager
        if (!lang.isKeyMatch(title, "gui.spotlight_confirm.title") && !lang.isKeyMatch(title, "gui.discovery.spotlight_remove_confirm.title")) return
        event.isCancelled = true

        val item = event.currentItem ?: return
        val tag = ItemTag.getType(item) ?: return

        when (tag) {
            "spotlight_confirm_yes" -> {
                val uuid = ItemTag.getWorldUuid(item) ?: return
                val worldData = plugin.worldConfigRepository.findByUuid(uuid) ?: return
                
                if (plugin.spotlightRepository.isSpotlight(uuid)) {
                    player.sendMessage(lang.getMessage(player, "messages.spotlight_already_registered"))
                } else {
                    val success = plugin.spotlightRepository.add(uuid)
                    if (success) {
                        player.sendMessage(lang.getMessage(player, "messages.spotlight_added", mapOf("world" to worldData.name)))
                        plugin.soundManager.playClickSound(player, item)
                    } else {
                        player.sendMessage(lang.getMessage(player, "messages.spotlight_limit_reached"))
                    }
                }
                plugin.discoveryGui.open(player)
            }
            "spotlight_confirm_no" -> {
                plugin.soundManager.playClickSound(player, item)
                plugin.discoveryGui.open(player)
            }
            "spotlight_remove_confirm_yes" -> {
                val uuid = ItemTag.getWorldUuid(item) ?: return
                val worldData = plugin.worldConfigRepository.findByUuid(uuid) ?: return

                plugin.spotlightRepository.remove(uuid)
                player.sendMessage(lang.getMessage(player, "messages.spotlight_removed", mapOf("world" to worldData.name)))
                plugin.soundManager.playClickSound(player, item)
                plugin.discoveryGui.open(player)
            }
            "spotlight_remove_confirm_no" -> {
                plugin.soundManager.playClickSound(player, item)
                plugin.discoveryGui.open(player)
            }
        }
    }
}
