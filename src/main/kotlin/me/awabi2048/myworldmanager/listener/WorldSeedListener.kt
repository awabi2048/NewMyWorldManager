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

    companion object {
        fun expandWorldSlot(plugin: MyWorldManager, player: Player): Boolean {
            val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)

            val defaultSlots = plugin.config.getInt("creation.max_create_count_default")
            val limit = plugin.config.getInt("creation.max_world_slots_limit", 10)
            if (defaultSlots + stats.unlockedWorldSlot >= limit) {
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        player,
                        "error.custom_item.world_seed_limit_reached",
                        mapOf("limit" to limit)
                    )
                )
                return false
            }

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
                player.sendMessage(plugin.languageManager.getMessage(player, "error.custom_item.item_not_found_hand"))
                return false
            }

            stats.unlockedWorldSlot += 1
            plugin.playerStatsRepository.save(stats)

            val newTotal = defaultSlots + stats.unlockedWorldSlot
            player.sendMessage(
                plugin.languageManager.getMessage(
                    player,
                    "messages.custom_item.world_seed_expanded",
                    mapOf("slots" to newTotal)
                )
            )
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
            return true
        }
    }

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
                expandWorldSlot(plugin, player)
                player.closeInventory()
            }
            "world_seed_confirm_no" -> {
                plugin.soundManager.playClickSound(player, item)
                player.closeInventory()
            }
        }
    }


    @EventHandler
    fun onDialogResponse(event: io.papermc.paper.event.player.PlayerCustomClickEvent) {
        val identifier = event.identifier
        if (identifier == net.kyori.adventure.key.Key.key("mwm:confirm/world_seed")) {
            val conn = event.commonConnection as? io.papermc.paper.connection.PlayerGameConnection ?: return
            val player = conn.player
            
            me.awabi2048.myworldmanager.gui.DialogConfirmManager.safeCloseDialog(player)

            expandWorldSlot(plugin, player)
            
        } else if (identifier == net.kyori.adventure.key.Key.key("mwm:confirm/cancel")) {
             // For generic cancel, we might want to ensure it's closing ONLY if it was related to this flow? 
             // But CustomClickEvent is global. We should check if we opened it?
             // Actually, safeCloseDialog is usually enough. Or do nothing.
             // But if we want sound:
             // We don't know if this cancel was for WorldSeed or Environment unless we encode it in ID, which we didn't (just "mwm:confirm/cancel").
             // So let's leave generic cancel handling to caller or generic listener, OR just ignore here.
        }
    }
}
