package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.DiscoveryGui
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.WorldTag
import me.awabi2048.myworldmanager.session.DiscoverySort
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import java.util.*

class DiscoveryListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val view = event.view
        val title = PlainTextComponentSerializer.plainText().serialize(view.title())

        val lang = plugin.languageManager
        if (view.topInventory.holder !is me.awabi2048.myworldmanager.gui.DiscoveryGui.DiscoveryGuiHolder) return
        event.isCancelled = true

        val item = event.currentItem ?: return
        val tag = ItemTag.getType(item) ?: return
        val session = plugin.discoverySessionManager.getSession(player.uniqueId)

        when (tag) {
            "discovery_world_item", ItemTag.TYPE_GUI_WORLD_ITEM -> {
                val uuid = ItemTag.getWorldUuid(item) ?: return
                val worldData = plugin.worldConfigRepository.findByUuid(uuid) ?: return

                val isMember = worldData.owner == player.uniqueId || 
                              worldData.moderators.contains(player.uniqueId) ||
                              worldData.members.contains(player.uniqueId)

                if (event.isLeftClick) {
                    // アクセス判定
                    if (worldData.publishLevel != PublishLevel.PUBLIC && !isMember) {
                        player.sendMessage(lang.getMessage(player, "messages.world_not_public"))
                        plugin.soundManager.playActionSound(player, "discovery", "access_denied")
                        player.closeInventory()
                        return
                    }

                    // ワープ
                    plugin.soundManager.playClickSound(player, item, "discovery")
                    plugin.worldService.teleportToWorld(player, uuid)
                    player.sendMessage(lang.getMessage(player, "messages.warp_success", mapOf("world" to worldData.name)))
                    plugin.worldService.sendAnnouncementMessage(player, worldData)
                    
                    val isWorldMember = worldData.owner == player.uniqueId || 
                                  worldData.moderators.contains(player.uniqueId) ||
                                  worldData.members.contains(player.uniqueId)
                    if (!isWorldMember) {
                        worldData.recentVisitors[0]++
                        plugin.worldConfigRepository.save(worldData)
                    }
                    
                    player.closeInventory()
                } else if (event.isRightClick) {
                    if (event.isShiftClick) {
                        // Spotlight削除 (Shift + 右クリック)
                        if (session.sort == DiscoverySort.SPOTLIGHT && player.hasPermission("myworldmanager.admin")) {
                            plugin.spotlightRepository.remove(uuid)
                            player.sendMessage(lang.getMessage(player, "messages.spotlight_removed", mapOf("world" to worldData.name)))
                            plugin.soundManager.playClickSound(player, item, "discovery")
                            plugin.discoveryGui.open(player)
                            return
                        }

                        // お気に入り (Shift + 右クリック)
                        val isWorldMember = worldData.owner == player.uniqueId || 
                                      worldData.moderators.contains(player.uniqueId) ||
                                      worldData.members.contains(player.uniqueId)
                                      
                        if (isWorldMember) return // 所属ワールドはお気に入り不可

                        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                        if (stats.favoriteWorlds.containsKey(uuid)) {
                            stats.favoriteWorlds.remove(uuid)
                            worldData.favorite = (worldData.favorite - 1).coerceAtLeast(0)
                            player.sendMessage(lang.getMessage(player, "messages.favorite_removed"))
                            plugin.soundManager.playActionSound(player, "discovery", "favorite_remove")
                        } else {
                            val maxFav = plugin.config.getInt("favorite.max_count", 1000)
                            if (stats.favoriteWorlds.size >= maxFav) {
                                player.sendMessage(lang.getMessage(player, "messages.favorite_limit_reached", mapOf("max" to maxFav)))
                                return
                            }
                            val now = java.time.LocalDate.now()
                            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                            stats.favoriteWorlds[uuid] = now.format(formatter)
                            worldData.favorite++
                            player.sendMessage(lang.getMessage(player, "messages.favorite_added"))
                            plugin.soundManager.playActionSound(player, "discovery", "favorite_add")
                        }
                        plugin.playerStatsRepository.save(stats)
                        plugin.worldConfigRepository.save(worldData)
                        plugin.discoveryGui.open(player)
                    } else {
                        // プレビュー (通常右クリック)
                        player.closeInventory()
                        plugin.previewSessionManager.startWorldPreview(player, worldData)
                    }
                }
            }
            ItemTag.TYPE_GUI_DISCOVERY_TAG -> {
                val allTags = WorldTag.values()
                if (event.isRightClick) {
                    session.selectedTag = null
                } else if (event.isLeftClick) {
                    val currentIndex = if (session.selectedTag == null) -1 else allTags.indexOf(session.selectedTag)
                    val nextIndex = (currentIndex + 1) % (allTags.size + 1)
                    
                    session.selectedTag = if (nextIndex == allTags.size) {
                        null
                    } else {
                        allTags[nextIndex]
                    }
                }
                
                plugin.soundManager.playClickSound(player, item, "discovery")
                plugin.discoveryGui.open(player)
            }
            ItemTag.TYPE_GUI_DISCOVERY_SORT -> {
                session.sort = GuiHelper.getNextValue(session.sort, DiscoverySort.values(), event.isLeftClick)
                plugin.soundManager.playClickSound(player, item, "discovery")
                plugin.discoveryGui.open(player)
            }
            "discovery_spotlight_empty" -> {
                if (event.isLeftClick && player.hasPermission("myworldmanager.admin")) {
                    val world = player.world
                    val worldData = plugin.worldConfigRepository.findByWorldName(world.name)
                    if (worldData == null) {
                        player.sendMessage(lang.getMessage(player, "messages.spotlight_not_in_myworld"))
                        return
                    }
                    
                    // オーナーチェック (管理者なら他人のワールドも可とする)
                    val isAdmin = player.hasPermission("myworldmanager.admin")
                    if (!isAdmin && worldData.owner != player.uniqueId && !player.isOp) {
                        player.sendMessage(lang.getMessage(player, "messages.spotlight_not_owner"))
                        return
                    }
                    
                    plugin.spotlightConfirmGui.open(player, worldData)
                    plugin.soundManager.playClickSound(player, item, "discovery")
                }
            }
        }
    }
}
