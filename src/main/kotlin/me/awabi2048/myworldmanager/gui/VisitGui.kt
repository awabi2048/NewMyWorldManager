package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

class VisitGui(private val plugin: MyWorldManager) {

    private val repository = plugin.worldConfigRepository
    private val worldsPerRow = 7
    private val dataRowsPerPage = 4
    private val itemsPerPage = dataRowsPerPage * worldsPerRow // 28

    fun open(player: Player, targetPlayer: OfflinePlayer, page: Int = 0, returnToWorld: WorldData? = null) {

        val allWorlds = repository.findAll()
        val targetWorlds = allWorlds.filter { world ->
            if (world.owner != targetPlayer.uniqueId || world.isArchived) return@filter false
            
            val isMember = world.owner == player.uniqueId || 
                          world.moderators.contains(player.uniqueId) || 
                          world.members.contains(player.uniqueId)
            
            world.publishLevel == PublishLevel.PUBLIC || isMember
        }

        val worldCount = targetWorlds.size
        val lang = plugin.languageManager
        val titleKey = "gui.visit.title"
        if (!lang.hasKey(player, titleKey)) {
            player.sendMessage("§c[MyWorldManager] Error: Missing translation key: $titleKey")
            return
        }
        
        val neededDataRows = if (worldCount == 0) 1 else kotlin.math.min(dataRowsPerPage, (worldCount + worldsPerRow - 1) / worldsPerRow)
        val rowCount = neededDataRows + 2
        val targetName = targetPlayer.name ?: "Unknown"
        val titleComp = lang.getComponent(player, titleKey, mapOf("player" to targetName))
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "visit", titleComp, VisitGuiHolder::class.java)
        
        val holder = VisitGuiHolder()
        val inventory = Bukkit.createInventory(holder, rowCount * 9, titleComp)
        holder.inv = inventory

        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE, returnToWorld)
        val greyPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE, returnToWorld)

        for (i in 0..8) inventory.setItem(i, blackPane)
        for (i in (rowCount - 1) * 9 until rowCount * 9) inventory.setItem(i, blackPane)

        val startIndex = page * itemsPerPage
        for (i in 0 until neededDataRows) {
            val rowStart = (i + 1) * 9
            inventory.setItem(rowStart, greyPane) // 左端
            inventory.setItem(rowStart + 8, greyPane) // 右端

            for (j in 0 until 7) {
                val worldIndex = startIndex + i * 7 + j
                if (worldIndex < targetWorlds.size) {
                    val world = targetWorlds[worldIndex]
                    inventory.setItem(rowStart + 1 + j, createWorldItem(player, world))
                } else {
                    inventory.setItem(rowStart + 1 + j, greyPane)
                }
            }
        }

        // 戻るボタン
        if (returnToWorld != null) {
            val footerStart = (rowCount - 1) * 9
            inventory.setItem(footerStart, createBackButton(player, returnToWorld))
        }

        player.openInventory(inventory)
    }

    private fun createWorldItem(viewer: Player, world: WorldData): ItemStack {
        val item = ItemStack(world.icon)
        val meta = item.itemMeta ?: return item
        val lang = plugin.languageManager
        meta.displayName(lang.getComponent(viewer, "gui.common.world_item_name", mapOf("world" to world.name)))
        
        val lore = mutableListOf<Component>()
        lore.add(lang.getComponent(viewer, "gui.common.separator"))
        
        if (world.description.isNotEmpty()) {
            lore.add(lang.getComponent(viewer, "gui.common.world_desc", mapOf("description" to world.description)))
            lore.add(lang.getComponent(viewer, "gui.common.separator"))
        }

        val owner = Bukkit.getOfflinePlayer(world.owner)
        val onlineColor = lang.getMessage(viewer, "publish_level.color.online")
        val offlineColor = lang.getMessage(viewer, "publish_level.color.offline")
        val ownerColor = if (owner.isOnline) onlineColor else offlineColor
        lore.add(lang.getComponent(viewer, "gui.visit.world_item.owner", mapOf("owner" to (owner.name ?: lang.getMessage(viewer, "general.unknown")), "status_color" to ownerColor)))
        
        // タグ表示
        if (world.tags.isNotEmpty()) {
            val tagNames = world.tags.joinToString(", ") { lang.getMessage(viewer, "gui.discovery.tag_names.${it.name.lowercase()}") }
            lore.add(lang.getComponent(viewer, "gui.visit.world_item.tag", mapOf("tags" to tagNames)))
        }

        lore.add(lang.getComponent(viewer, "gui.visit.world_item.favorite", mapOf("count" to world.favorite)))
        val totalRecent = world.recentVisitors.sum()
        lore.add(lang.getComponent(viewer, "gui.visit.world_item.recent_visitors", mapOf("count" to totalRecent)))

        lore.add(lang.getComponent(viewer, "gui.common.separator"))
        lore.add(lang.getComponent(viewer, "gui.visit.world_item.warp"))
        
        val stats = plugin.playerStatsRepository.findByUuid(viewer.uniqueId)
        
        val viewerPlayerUuid = viewer.uniqueId
        val isMember = world.owner == viewerPlayerUuid || 
                      world.moderators.contains(viewerPlayerUuid) || 
                      world.members.contains(viewerPlayerUuid)

        if (!isMember) {
            if (stats.favoriteWorlds.containsKey(world.uuid)) {
                lore.add(lang.getComponent(viewer, "gui.visit.world_item.fav_remove"))
            } else {
                lore.add(lang.getComponent(viewer, "gui.visit.world_item.fav_add"))
            }
            lore.add(lang.getComponent(viewer, "gui.common.separator"))
        }
        lore.add(lang.getComponent(viewer, "gui.common.separator"))
        
        meta.lore(lore)
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_WORLD_ITEM)
        ItemTag.setWorldUuid(item, world.uuid)
        return item
    }

    private fun createBackButton(player: Player, returnToWorld: WorldData): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.REDSTONE)
        val meta = item.itemMeta ?: return item
        meta.displayName(lang.getComponent(player, "gui.common.return").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
        meta.lore(listOf(lang.getComponent(player, "gui.common.return_desc")))
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_RETURN)
        ItemTag.setWorldUuid(item, returnToWorld.uuid)
        return item
    }

    private fun createDecorationItem(material: Material, returnToWorld: WorldData? = null): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.empty())
        meta.isHideTooltip = true
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_DECORATION)
        if (returnToWorld != null) ItemTag.setWorldUuid(item, returnToWorld.uuid)
        return item
    }

    class VisitGuiHolder : org.bukkit.inventory.InventoryHolder {
        lateinit var inv: org.bukkit.inventory.Inventory
        override fun getInventory(): org.bukkit.inventory.Inventory = inv
    }
}
