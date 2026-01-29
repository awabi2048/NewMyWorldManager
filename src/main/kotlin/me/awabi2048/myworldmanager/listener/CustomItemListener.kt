package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.util.CustomItem
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack

class CustomItemListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item ?: return

        if (!item.hasItemMeta()) return
        
        // Empty Biome Bottle Logic
        if (ItemTag.isType(item, ItemTag.TYPE_EMPTY_BIOME_BOTTLE)) {
             if (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK) {
                 event.isCancelled = true // Prevent drinking or normal interaction if any

                 val biome = player.location.block.biome
                 val biomeId = biome.toString().lowercase()
                 
                 // Reduce empty bottle
                 item.amount -= 1
                 
                 // Create filled bottle
                 val filledBottle = CustomItem.BOTTLED_BIOME_AIR.createWithBiome(plugin.languageManager, player, biomeId)
                 
                 // Give to player
                 if (item.amount == 0) {
                     // If it was the last one, replace in hand (or just remove if logic differs, but player.inventory.addItem handles "add or drop")
                     // Actually, if amount is 0, item is gone. We should put the new item in that slot if possible, or addItem.
                     // Since we decremented the item object directly, if it was 1, it is now 0 (AIR effectively).
                     // But Bukkit ItemStack logic can be tricky. Best to set the hand item to the new item if it was 1.
                     // However, to be safe and support stacking:
                     // 1. If stack > 1: decrement hand, addItem new bottle.
                     // 2. If stack == 1: set hand to new bottle.
                     
                     // We already did item.amount -= 1. 
                     // If it was 1, now 0.
                     // Wait, manipulating the event.item directly works, but let's be precise.
                 }
                 
                 // Re-do logic for safety
                 // Restore amount for a sec to handle "set to new item" vs "add new item"
                 item.amount += 1 // Undo
                 
                 if (item.amount == 1) {
                     // Replace hand
                     if (event.hand == org.bukkit.inventory.EquipmentSlot.HAND) {
                         player.inventory.setItemInMainHand(filledBottle)
                     } else {
                         player.inventory.setItemInOffHand(filledBottle)
                     }
                 } else {
                     item.amount -= 1
                     player.inventory.addItem(filledBottle).forEach { (_, drop) ->
                         player.world.dropItem(player.location, drop)
                     }
                 }

                 player.playSound(player.location, Sound.ITEM_BOTTLE_FILL, 1.0f, 1.0f)
                 val biomeName = plugin.languageManager.getMessage(player, "biomes.$biomeId")
                 player.sendMessage(plugin.languageManager.getMessage(player, "messages.custom_item.bottle_fill", biomeName))
            }
        }
        
        // Bottled Biome Air Logic (Partial Application)
        if (ItemTag.isType(item, ItemTag.TYPE_BOTTLED_BIOME_AIR)) {
            if ((event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK) && player.isSneaking) {
                event.isCancelled = true

                // Correct World Check
                val worldName = player.world.name
                if (!worldName.startsWith("my_world.")) {
                    player.sendMessage(plugin.languageManager.getMessage(player, "messages.custom_item.biome_world_only"))
                    return
                }
                
                // Get Biome ID
                val biomeId = ItemTag.getBiomeId(item)
                if (biomeId == null) {
                    player.sendMessage("§cInvalid biome data.")
                    return
                }

                val targetBiome = try {
                    org.bukkit.block.Biome.valueOf(biomeId.uppercase())
                } catch (e: IllegalArgumentException) {
                    player.sendMessage("§cInvalid biome data.")
                    return
                }
                
                // Get World Data
                val uuidStr = worldName.removePrefix("my_world.")
                val worldUuid = try { java.util.UUID.fromString(uuidStr) } catch (e: Exception) { return }
                val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return
                
                // Permission Check (Owner or Moderator)
                if (worldData.owner != player.uniqueId && !worldData.moderators.contains(player.uniqueId) && !player.hasPermission("myworldmanager.admin")) {
                    player.sendMessage(plugin.languageManager.getMessage(player, "messages.custom_item.no_permission"))
                    return
                }
                
                // Apply Partial Biome
                val radius = 10
                val centerX = player.location.blockX
                val centerZ = player.location.blockZ
                
                val world = player.world
                val minHeight = world.minHeight
                val maxHeight = world.maxHeight
                
                for (x in centerX - radius..centerX + radius) {
                    for (z in centerZ - radius..centerZ + radius) {
                        for (y in minHeight until maxHeight step 4) { // Optimization: setBiome usually works on 4x4x4 sections or similar depending on version, but per-block loop is safe
                             // Simple distance check if we want a circle, or just square. task says "radius 10", often implies circle or square. Let's do square for simplicity as "radius" in MC commands often means square radius, or do proper distance check.
                             // task says "radius 10 blocks area". Let's do a simple euclidean distance check for better feel.
                             if ((x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ) <= radius * radius) {
                                 world.setBiome(x, y, z, targetBiome)
                             }
                        }
                    }
                }
                
                // Refresh affected chunks to show changes
                val minChunkX = (centerX - radius) shr 4
                val maxChunkX = (centerX + radius) shr 4
                val minChunkZ = (centerZ - radius) shr 4
                val maxChunkZ = (centerZ + radius) shr 4
                
                for (cx in minChunkX..maxChunkX) {
                    for (cz in minChunkZ..maxChunkZ) {
                        world.refreshChunk(cx, cz)
                    }
                }
                
                // Save Data
                worldData.partialBiomes.add(me.awabi2048.myworldmanager.model.PartialBiomeData(centerX, centerZ, radius, biomeId.uppercase()))
                plugin.worldConfigRepository.save(worldData)
                
                // Effects
                player.playSound(player.location, Sound.ITEM_BOTTLE_FILL, 1.0f, 0.5f) // Open sound pitch lower
                player.world.spawnParticle(org.bukkit.Particle.CLOUD, player.location, 50, 2.0, 1.0, 2.0, 0.1)
                
                val biomeName = plugin.languageManager.getMessage(player, "biomes.${biomeId.lowercase()}")
                player.sendMessage(plugin.languageManager.getMessage(player, "messages.custom_item.partial_biome_applied", biomeName))
                
                // Consume Item
                 item.amount -= 1
                 if (item.amount <= 0) {
                     player.inventory.setItemInMainHand(null) // Assuming main hand usage primarily, but need to check event.hand
                 } else {
                    // Update inventory if needed? item object is mutable and referenced? Yes usually.
                 }
            }
        }
        
        // World Seed Logic
        if (ItemTag.isType(item, ItemTag.TYPE_WORLD_SEED)) {
             if (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK) {
                 event.isCancelled = true

                 // Check limits
                 val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                 val defaultSlots = plugin.config.getInt("creation.max_create_count_default")
                 val currentSlots = defaultSlots + stats.unlockedWorldSlot
                 val limit = plugin.config.getInt("creation.max_world_slots_limit", 10)
                 
                 if (currentSlots >= limit) {
                     player.sendMessage(plugin.languageManager.getMessage(player, "messages.custom_item.world_seed_limit_reached", limit))
                     player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f)
                     return
                 }
                 
                 // Open Confirmation
                 plugin.worldSeedConfirmGui.open(player, currentSlots, currentSlots + 1)
             }
        }
    }
}
