package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.VisitGui
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent

class VisitListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val view = event.view
        val title = PlainTextComponentSerializer.plainText().serialize(view.title())
        // InventoryHolderによる判定に変更
        if (view.topInventory.holder is VisitGui.VisitGuiHolder) {
            val player = event.whoClicked as? Player ?: return
            val lang = plugin.languageManager

            event.isCancelled = true
            val currentItem = event.currentItem ?: return
            val type = ItemTag.getType(currentItem) ?: return
            if (currentItem.type == Material.AIR || type == ItemTag.TYPE_GUI_DECORATION) return

            when (type) {
                ItemTag.TYPE_GUI_RETURN -> {
                    val uuid = ItemTag.getWorldUuid(currentItem) ?: return
                    plugin.soundManager.playClickSound(player, currentItem)
                    val worldData = plugin.worldConfigRepository.findByUuid(uuid)
                    if (worldData != null) {
                        plugin.favoriteMenuGui.open(player, worldData)
                    }
                }
                ItemTag.TYPE_GUI_WORLD_ITEM -> {
                    val uuid = ItemTag.getWorldUuid(currentItem) ?: return

                    if (event.isLeftClick) {
                        val worldData = plugin.worldConfigRepository.findByUuid(uuid)
                        val isMember = worldData != null && (worldData.owner == player.uniqueId || 
                                      worldData.moderators.contains(player.uniqueId) ||
                                      worldData.members.contains(player.uniqueId))

                        if (worldData == null || (worldData.publishLevel != PublishLevel.PUBLIC && !isMember)) {
                            player.sendMessage(lang.getMessage(player, "error.world_not_public"))
                            plugin.soundManager.playActionSound(player, "visit", "access_denied")
                            player.closeInventory()
                            return
                        }

                        plugin.soundManager.playClickSound(player, currentItem)
                        plugin.worldService.teleportToWorld(player, uuid)
                        player.sendMessage(lang.getMessage(player, "messages.warp_success", mapOf("world" to worldData.name)))
                        
                        player.closeInventory()
                    } else if (event.isRightClick) {
                        // お気に入り処理
                        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                        val worldData = plugin.worldConfigRepository.findByUuid(uuid) ?: return

                        if (stats.favoriteWorlds.containsKey(uuid)) {
                            stats.favoriteWorlds.remove(uuid)
                            worldData.favorite = (worldData.favorite - 1).coerceAtLeast(0)
                            
                            plugin.playerStatsRepository.save(stats)
                            plugin.worldConfigRepository.save(worldData)
                            player.sendMessage(lang.getMessage(player, "messages.favorite_removed"))
                            plugin.soundManager.playActionSound(player, "visit", "favorite_remove")
                        } else {
                            if (worldData.owner == player.uniqueId || worldData.moderators.contains(player.uniqueId) || worldData.members.contains(player.uniqueId)) {
                                return
                            }
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
                        
                        // 表示更新
                        val targetPlayerName = title.substringBefore(lang.getMessage(player, "gui.visit.title").replace("{player}", ""))
                        val targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName)
                        VisitGui(plugin).open(player, targetPlayer)
                    }
                }
            }
            return
        }
    }
}
