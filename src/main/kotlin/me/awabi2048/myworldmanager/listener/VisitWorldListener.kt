package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.VisitWorldGui
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.ItemTag
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent

class VisitWorldListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val holder = event.view.topInventory.holder as? VisitWorldGui.VisitWorldGuiHolder ?: return
        val lang = plugin.languageManager
        val isBedrock = plugin.playerPlatformResolver.isBedrock(player)

        event.isCancelled = true
        val currentItem = event.currentItem ?: return
        val type = ItemTag.getType(currentItem) ?: return
        if (currentItem.type == Material.AIR || type == ItemTag.TYPE_GUI_DECORATION || type == ItemTag.TYPE_GUI_INFO) return

        when (type) {
            ItemTag.TYPE_GUI_RETURN -> {
                GuiHelper.handleReturnClick(plugin, player, currentItem)
            }

            ItemTag.TYPE_GUI_NAV_PREV,
            ItemTag.TYPE_GUI_NAV_NEXT -> {
                val targetPage = ItemTag.getTargetPage(currentItem) ?: return
                plugin.soundManager.playClickSound(player, currentItem, "visit")
                plugin.visitWorldGui.open(player, holder.query, targetPage, holder.showBackButton)
            }

            ItemTag.TYPE_GUI_WORLD_ITEM -> {
                val uuid = ItemTag.getWorldUuid(currentItem) ?: return
                val worldData = plugin.worldConfigRepository.findByUuid(uuid)
                val isMember = worldData != null && (
                    worldData.owner == player.uniqueId ||
                        worldData.moderators.contains(player.uniqueId) ||
                        worldData.members.contains(player.uniqueId)
                    )

                if (worldData == null || (worldData.publishLevel != PublishLevel.PUBLIC && !isMember)) {
                    player.sendMessage(lang.getMessage(player, "error.world_not_public"))
                    plugin.soundManager.playActionSound(player, "visit", "access_denied")
                    player.closeInventory()
                    return
                }

                if (isBedrock) {
                    plugin.soundManager.playClickSound(player, currentItem, "visit")
                    plugin.worldService.teleportToWorld(player, uuid)
                    player.sendMessage(lang.getMessage(player, "messages.warp_success", mapOf("world" to worldData.name)))
                    player.closeInventory()
                    return
                }

                if (event.isLeftClick) {
                    plugin.soundManager.playClickSound(player, currentItem, "visit")
                    plugin.worldService.teleportToWorld(player, uuid)
                    player.sendMessage(lang.getMessage(player, "messages.warp_success", mapOf("world" to worldData.name)))
                    player.closeInventory()
                    return
                }

                if (event.isRightClick) {
                    val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                    if (worldData.owner == player.uniqueId || worldData.moderators.contains(player.uniqueId) || worldData.members.contains(player.uniqueId)) {
                        return
                    }

                    if (stats.favoriteWorlds.containsKey(uuid)) {
                        stats.favoriteWorlds.remove(uuid)
                        worldData.favorite = (worldData.favorite - 1).coerceAtLeast(0)
                        plugin.playerStatsRepository.save(stats)
                        plugin.worldConfigRepository.save(worldData)
                        player.sendMessage(lang.getMessage(player, "messages.favorite_removed"))
                        plugin.soundManager.playActionSound(player, "visit", "favorite_remove")
                    } else {
                        val maxFav = plugin.config.getInt("favorite.max_count", 1000)
                        if (stats.favoriteWorlds.size >= maxFav) {
                            player.sendMessage(lang.getMessage(player, "error.favorite_limit_reached", mapOf("max" to maxFav)))
                            return
                        }

                        val today = java.time.LocalDate.now().toString()
                        stats.favoriteWorlds[uuid] = today
                        worldData.favorite++
                        plugin.playerStatsRepository.save(stats)
                        plugin.worldConfigRepository.save(worldData)
                        player.sendMessage(lang.getMessage(player, "messages.favorite_added"))
                        plugin.soundManager.playActionSound(player, "visit", "favorite_add")
                    }

                    plugin.visitWorldGui.open(player, holder.query, holder.page, holder.showBackButton)
                }
            }
        }
    }
}
