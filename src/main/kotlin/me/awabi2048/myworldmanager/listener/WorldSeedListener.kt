package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PermissionManager
import me.awabi2048.myworldmanager.util.WorldRuntimePolicies
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot

class WorldSeedListener {

    companion object {
        fun expandWorldSlot(plugin: MyWorldManager, player: Player): Boolean {
            if (!MyWorldManagerApi.isWorldSlotSystemEnabled()) return false
            val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
            val bypassLimits = PermissionManager.canBypassWorldLimits(player)

            val defaultSlots = WorldRuntimePolicies.maxCreateCountDefault(plugin.config)
            val limit = WorldRuntimePolicies.maxWorldSlotLimit(plugin.config)
            if (!bypassLimits && defaultSlots + stats.unlockedWorldSlot >= limit) {
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
}
