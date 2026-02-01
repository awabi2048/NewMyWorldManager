package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.EquipmentSlot

class WorldSeedListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val view = event.view
        
        // Match Title using LanguageManager key matching
        // Note: WorldSeedConfirmGui uses "gui.world_seed_confirm.title"
        if (!plugin.languageManager.isKeyMatch(PlainTextComponentSerializer.plainText().serialize(view.title()), "gui.world_seed_confirm.title")) return
        
        event.isCancelled = true

        val item = event.currentItem ?: return
        val tag = ItemTag.getType(item) ?: return

        when (tag) {
            "world_seed_confirm_yes" -> {
                // Execute Expansion
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                
                // Double check limit just in case
                val defaultSlots = plugin.config.getInt("creation.max_create_count_default")
                val limit = plugin.config.getInt("creation.max_world_slots_limit", 10)
                if (defaultSlots + stats.unlockedWorldSlot >= limit) {
                    player.sendMessage(plugin.languageManager.getMessage(player, "error.custom_item.world_seed_limit_reached", mapOf("limit" to limit)))
                    player.closeInventory()
                    return
                }

                // Consume Item
                var consumed = false
                val mainHand = player.inventory.itemInMainHand
                val offHand = player.inventory.itemInOffHand

                if (ItemTag.isType(mainHand, ItemTag.TYPE_WORLD_SEED)) {
                    mainHand.amount -= 1
                    consumed = true
                } else if (ItemTag.isType(offHand, ItemTag.TYPE_WORLD_SEED)) {
                    offHand.amount -= 1
                    consumed = true
                }

                if (!consumed) {
                    player.sendMessage("§cItem not found in hand.") // Should ideally use message key or be silent if weird state
                    player.closeInventory()
                    return
                }

                // Update Stats
                stats.unlockedWorldSlot += 1
                plugin.playerStatsRepository.save(stats)

                // Message & Sound
                val newTotal = defaultSlots + stats.unlockedWorldSlot
                player.sendMessage(plugin.languageManager.getMessage(player, "messages.custom_item.world_seed_expanded", mapOf("slots" to newTotal)))
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f) // Or specific sound
                
                player.closeInventory()
            }
            "world_seed_confirm_no" -> {
                plugin.soundManager.playClickSound(player, item)
                player.closeInventory()
            }
        }
    }
}
