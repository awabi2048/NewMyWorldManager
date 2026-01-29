package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.FavoriteGui
import me.awabi2048.myworldmanager.gui.VisitGui
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent

class FavoriteListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val view = event.view
        val title = PlainTextComponentSerializer.plainText().serialize(view.title())
        val player = event.whoClicked as? Player ?: return
        val lang = plugin.languageManager

        // お気に入り一覧
        if (lang.isKeyMatch(title, "gui.favorite.title")) {
            event.isCancelled = true
            val currentItem = event.currentItem ?: return

            val type = ItemTag.getType(currentItem)
            if (type == ItemTag.TYPE_GUI_NAV_NEXT || type == ItemTag.TYPE_GUI_NAV_PREV) {
                val page = ItemTag.getTargetPage(currentItem) ?: 0
                val worldUuid = ItemTag.getWorldUuid(currentItem)
                val worldData = if (worldUuid != null) plugin.worldConfigRepository.findByUuid(worldUuid) else null
                plugin.soundManager.playClickSound(player, currentItem)
                plugin.favoriteGui.open(player, page, worldData)
                return
            }

            if (type == ItemTag.TYPE_GUI_RETURN) {
                val uuid = ItemTag.getWorldUuid(currentItem) ?: return
                val worldData = plugin.worldConfigRepository.findByUuid(uuid) ?: return
                plugin.soundManager.playClickSound(player, currentItem)
                plugin.favoriteMenuGui.open(player, worldData)
                return
            }
            
            if (type == ItemTag.TYPE_GUI_DECORATION || type == ItemTag.TYPE_GUI_INFO) return
            
            // ワールドアイテム処理
            if (type != ItemTag.TYPE_GUI_WORLD_ITEM) return

            val uuid = ItemTag.getWorldUuid(currentItem) ?: return
            val worldData = plugin.worldConfigRepository.findByUuid(uuid) ?: return

            if (event.isLeftClick) {
                if (worldData.isArchived) {
                    // アーカイブ済みの場合はクリックで削除（お気に入り解除）
                    if (event.isShiftClick) {
                        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId) // Explicitly fetch stats
                        if (stats.favoriteWorlds.containsKey(uuid)) {
                            plugin.favoriteConfirmGui.open(player, worldData)
                        }
                    } else {
                        // Regular click behavior (if any) or hint message
                         plugin.soundManager.playClickSound(player, currentItem)
                    }
                    return
                }

                val isMember = worldData.owner == player.uniqueId || 
                              worldData.moderators.contains(player.uniqueId) ||
                              worldData.members.contains(player.uniqueId)
                
                // 公開・限定公開以外はワープ不可 (メンバーは例外)
                if (!isMember && worldData.publishLevel != me.awabi2048.myworldmanager.model.PublishLevel.PUBLIC && worldData.publishLevel != me.awabi2048.myworldmanager.model.PublishLevel.FRIEND) {
                    plugin.soundManager.playClickSound(player, currentItem)
                    return
                }
                
                plugin.soundManager.playClickSound(player, currentItem)
                plugin.worldService.teleportToWorld(player, uuid)
                player.sendMessage(lang.getMessage(player, "messages.warp_success", mapOf("world" to worldData.name)))
                plugin.worldService.sendAnnouncementMessage(player, worldData)
                
                if (!isMember) {
                    worldData.recentVisitors[0]++
                    plugin.worldConfigRepository.save(worldData)
                }
                
                player.closeInventory()
            } else if (event.isRightClick) {
                if (event.isShiftClick) {
                    val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                    if (stats.favoriteWorlds.containsKey(uuid)) {
                        // オーナー以外なら解除可能
                        if (worldData.owner == player.uniqueId) return

                        plugin.favoriteConfirmGui.open(player, worldData)
                    }
                } else {
                    // プレビュー
                    player.closeInventory()
                    plugin.previewSessionManager.startWorldPreview(player, worldData)
                }
            }
            return
        }

        // お気に入りメニュー
        if (lang.isKeyMatch(title, "gui.favorite.favorite_menu.title")) {
            event.isCancelled = true
            val currentItem = event.currentItem ?: return
            val type = ItemTag.getType(currentItem)
            if (currentItem.type == Material.AIR || type == ItemTag.TYPE_GUI_DECORATION) return

            when (type) {
                ItemTag.TYPE_GUI_FAVORITE_OTHER_WORLDS -> {
                    val uuid = ItemTag.getWorldUuid(currentItem) ?: return
                    val worldData = plugin.worldConfigRepository.findByUuid(uuid) ?: return
                    val owner = Bukkit.getOfflinePlayer(worldData.owner)
                    plugin.soundManager.playClickSound(player, currentItem)
                    VisitGui(plugin).open(player, owner, 0, worldData)
                }
                ItemTag.TYPE_GUI_FAVORITE_TOGGLE -> {
                    val uuid = ItemTag.getWorldUuid(currentItem) ?: return
                    val worldData = plugin.worldConfigRepository.findByUuid(uuid) ?: return
                    
                    // オーナーはお気に入り登録できない
                    if (worldData.owner == player.uniqueId) return
                    
                    val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)

                    if (stats.favoriteWorlds.containsKey(uuid)) {
                        stats.favoriteWorlds.remove(uuid)
                        worldData.favorite = (worldData.favorite - 1).coerceAtLeast(0)
                        player.sendMessage(lang.getMessage(player, "messages.favorite_removed"))
                        plugin.soundManager.playActionSound(player, "favorite", "favorite_remove")
                    } else {
                        val maxFav = plugin.config.getInt("favorite.max_count", 1000)
                        if (stats.favoriteWorlds.size >= maxFav) {
                            player.sendMessage(lang.getMessage(player, "messages.favorite_limit_reached", mapOf("max" to maxFav)))
                            return
                        }
                        stats.favoriteWorlds[uuid] = java.time.LocalDate.now().toString()
                        worldData.favorite++
                        player.sendMessage(lang.getMessage(player, "messages.favorite_added"))
                        plugin.soundManager.playActionSound(player, "favorite", "favorite_add")
                    }
                    plugin.playerStatsRepository.save(stats)
                    plugin.worldConfigRepository.save(worldData)
                    plugin.favoriteMenuGui.open(player, worldData)
                }
                ItemTag.TYPE_GUI_FAVORITE_LIST -> {
                    val uuid = ItemTag.getWorldUuid(currentItem)
                    val worldData = if (uuid != null) plugin.worldConfigRepository.findByUuid(uuid) else null
                    plugin.soundManager.playClickSound(player, currentItem)
                    plugin.favoriteGui.open(player, 0, worldData)
                }
                ItemTag.TYPE_GUI_RETURN -> {
                    val uuid = ItemTag.getWorldUuid(currentItem) ?: return
                    val worldData = plugin.worldConfigRepository.findByUuid(uuid) ?: return
                    plugin.soundManager.playClickSound(player, currentItem)
                    plugin.favoriteMenuGui.open(player, worldData)
                }
            }
            return
        }

        
        // お気に入り解除確認メニュー
        if (lang.isKeyMatch(title, "gui.favorite.remove_confirm.title")) {
            event.isCancelled = true
            val currentItem = event.currentItem ?: return
            val type = ItemTag.getType(currentItem)
            val uuid = ItemTag.getWorldUuid(currentItem) ?: return
            val worldData = plugin.worldConfigRepository.findByUuid(uuid) ?: return

            if (type == ItemTag.TYPE_GUI_CONFIRM) {
                // 解除実行
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                if (stats.favoriteWorlds.containsKey(uuid)) {
                    stats.favoriteWorlds.remove(uuid)
                    worldData.favorite = (worldData.favorite - 1).coerceAtLeast(0)
                    plugin.playerStatsRepository.save(stats)
                    plugin.worldConfigRepository.save(worldData)
                    player.sendMessage(lang.getMessage(player, "messages.favorite_removed"))
                    plugin.soundManager.playActionSound(player, "favorite", "favorite_remove")
                }
                plugin.favoriteGui.open(player, 0)
            } else if (type == ItemTag.TYPE_GUI_CANCEL) {
                // キャンセルしてリストに戻る
                plugin.soundManager.playClickSound(player, currentItem)
                plugin.favoriteGui.open(player, 0)
            }
        }
    }
}
