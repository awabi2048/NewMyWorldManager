package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import java.util.Locale

class VisitWorldGui(private val plugin: MyWorldManager) {

    private val worldsPerRow = 7
    private val dataRowsPerPage = 4
    private val itemsPerPage = worldsPerRow * dataRowsPerPage

    fun hasSearchResult(query: String): Boolean {
        val normalizedQuery = normalize(query)
        return searchWorlds(normalizedQuery).isNotEmpty()
    }

    fun open(player: Player, query: String, page: Int = 0, showBackButton: Boolean = false) {
        val normalizedQuery = normalize(query)
        val worlds = searchWorlds(normalizedQuery)

        val totalPages = if (worlds.isEmpty()) 1 else (worlds.size + itemsPerPage - 1) / itemsPerPage
        val currentPage = page.coerceIn(0, totalPages - 1)
        val startIndex = currentPage * itemsPerPage
        val pageWorlds = worlds.drop(startIndex).take(itemsPerPage)

        val lang = plugin.languageManager
        val title = GuiHelper.inventoryTitle(lang.getComponent(player, "gui.visitworld.title", mapOf("query" to query)))

        GuiHelper.playMenuSoundIfTitleChanged(
            plugin,
            player,
            "visit",
            title,
            VisitWorldGuiHolder::class.java
        )

        val holder = VisitWorldGuiHolder(query, showBackButton, currentPage)
        val inventory = Bukkit.createInventory(holder, 54, title)
        holder.inv = inventory

        fillBaseLayout(inventory)

        val worldItemSlots = buildWorldItemSlots()
        pageWorlds.forEachIndexed { index, worldData ->
            inventory.setItem(worldItemSlots[index], createWorldItem(player, worldData))
        }

        if (showBackButton) {
            inventory.setItem(45, GuiHelper.createReturnItem(plugin, player, "visit"))
        }
        inventory.setItem(49, createInfoItem(player, query, worlds.size, pageWorlds.size, currentPage + 1, totalPages))

        if (currentPage > 0) {
            inventory.setItem(47, GuiHelper.createPrevPageItem(plugin, player, "visit", currentPage - 1))
        }
        if (currentPage < totalPages - 1) {
            inventory.setItem(51, GuiHelper.createNextPageItem(plugin, player, "visit", currentPage + 1))
        }

        player.openInventory(inventory)
    }

    private fun searchWorlds(normalizedQuery: String): List<WorldData> {
        if (normalizedQuery.isEmpty()) return emptyList()
        val queryTokens = normalizedQuery.split(Regex("\\s+")).filter { it.isNotBlank() }

        return plugin.worldConfigRepository.findAll()
            .asSequence()
            .filter { it.publishLevel == PublishLevel.PUBLIC && !it.isArchived }
            .map { world ->
                val normalizedName = normalize(world.name)
                val exact = normalizedName == normalizedQuery
                val partial = queryTokens.isNotEmpty() && queryTokens.all { normalizedName.contains(it) }
                SearchResult(world, exact, partial)
            }
            .filter { it.exact || it.partial }
            .sortedWith(
                compareByDescending<SearchResult> { it.exact }
                    .thenByDescending { it.world.favorite }
                    .thenByDescending { it.world.recentVisitors.sum() }
                    .thenByDescending { it.world.createdAt }
            )
            .map { it.world }
            .toList()
    }

    private fun fillBaseLayout(inventory: Inventory) {
        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
        val whitePane = createDecorationItem(Material.WHITE_STAINED_GLASS_PANE)

        for (i in 0..8) inventory.setItem(i, blackPane)
        for (i in 45..53) inventory.setItem(i, blackPane)

        for (row in 1..4) {
            val rowStart = row * 9
            inventory.setItem(rowStart, grayPane)
            inventory.setItem(rowStart + 8, grayPane)
            for (column in 1..7) {
                inventory.setItem(rowStart + column, whitePane)
            }
        }
    }

    private fun buildWorldItemSlots(): List<Int> {
        val result = mutableListOf<Int>()
        for (row in 1..4) {
            val rowStart = row * 9
            for (column in 1..7) {
                result += rowStart + column
            }
        }
        return result
    }

    private fun createInfoItem(
        player: Player,
        query: String,
        totalHit: Int,
        shownCount: Int,
        currentPage: Int,
        totalPages: Int
    ): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.BOOK)
        val meta = item.itemMeta ?: return item

        val lore = mutableListOf(
            lang.getComponent(player, "gui.visitworld.info.query", mapOf("query" to query)).decoration(TextDecoration.ITALIC, false),
            lang.getComponent(player, "gui.visitworld.info.hit", mapOf("hit" to totalHit)).decoration(TextDecoration.ITALIC, false),
            lang.getComponent(player, "gui.visitworld.info.shown", mapOf("shown" to shownCount)).decoration(TextDecoration.ITALIC, false)
        )
        if (totalPages > 1) {
            lore += lang.getComponent(
                player,
                "gui.visitworld.info.page",
                mapOf("current" to currentPage, "total" to totalPages)
            ).decoration(TextDecoration.ITALIC, false)
        }

        meta.displayName(lang.getComponent(player, "gui.visitworld.info.name").decoration(TextDecoration.ITALIC, false))
        meta.lore(lore)

        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_INFO)
        return item
    }

    private fun createWorldItem(viewer: Player, world: WorldData): ItemStack {
        val item = ItemStack(world.icon)
        val meta = item.itemMeta ?: return item
        val lang = plugin.languageManager

        meta.displayName(
            lang.getComponent(
                viewer,
                "gui.common.world_item_name",
                mapOf("world" to world.name)
            )
        )

        val formattedDesc = if (world.description.isNotEmpty()) {
            lang.getMessage(viewer, "gui.common.world_desc", mapOf("description" to world.description))
        } else {
            ""
        }

        val ownerRef = Bukkit.getOfflinePlayer(world.owner)
        val onlineColor = lang.getMessage(viewer, "publish_level.color.online")
        val offlineColor = lang.getMessage(viewer, "publish_level.color.offline")
        val statusColor = if (ownerRef.isOnline) onlineColor else offlineColor
        val ownerLine = lang.getMessage(
            viewer,
            "gui.visit.world_item.owner",
            mapOf(
                "owner" to PlayerNameUtil.getNameOrDefault(world.owner, lang.getMessage(viewer, "general.unknown")),
                "status_color" to statusColor
            )
        )

        val favoriteLine = lang.getMessage(viewer, "gui.visit.world_item.favorite", mapOf("count" to world.favorite))
        val visitorLine = lang.getMessage(viewer, "gui.visit.world_item.recent_visitors", mapOf("count" to world.recentVisitors.sum()))

        val tagLine = if (world.tags.isNotEmpty()) {
            val tagNames = world.tags.joinToString(", ") {
                plugin.worldTagManager.getDisplayName(viewer, it)
            }
            lang.getMessage(viewer, "gui.visit.world_item.tag", mapOf("tags" to tagNames))
        } else {
            ""
        }

        val isBedrock = plugin.playerPlatformResolver.isBedrock(viewer)
        val warpLine = if (isBedrock) {
            lang.getMessage(viewer, "gui.visit.world_item.warp_bedrock")
        } else {
            lang.getMessage(viewer, "gui.visit.world_item.warp")
        }

        val stats = plugin.playerStatsRepository.findByUuid(viewer.uniqueId)
        val viewerPlayerUuid = viewer.uniqueId
        val isMember = world.owner == viewerPlayerUuid ||
            world.moderators.contains(viewerPlayerUuid) ||
            world.members.contains(viewerPlayerUuid)

        val favActionLine = if (isBedrock) {
            ""
        } else if (!isMember) {
            if (stats.favoriteWorlds.containsKey(world.uuid)) {
                lang.getMessage(viewer, "gui.visit.world_item.fav_remove")
            } else {
                lang.getMessage(viewer, "gui.visit.world_item.fav_add")
            }
        } else {
            ""
        }

        val separator = lang.getComponent(viewer, "gui.common.separator")
        meta.lore(
            me.awabi2048.myworldmanager.util.GuiHelper.cleanupLore(
                lang.getComponentList(
                    viewer,
                    "gui.visit.world_item.lore",
                    mapOf(
                        "description" to formattedDesc,
                        "owner_line" to ownerLine,
                        "favorite_line" to favoriteLine,
                        "visitor_line" to visitorLine,
                        "tag_line" to tagLine,
                        "warp_line" to warpLine,
                        "fav_action_line" to favActionLine
                    )
                ),
                separator
            )
        )

        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_WORLD_ITEM)
        ItemTag.setWorldUuid(item, world.uuid)
        return item
    }

    private fun createDecorationItem(material: Material): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.empty())
        meta.isHideTooltip = true
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_DECORATION)
        return item
    }

    private fun normalize(text: String): String {
        return text.trim().lowercase(Locale.ROOT)
    }

    private data class SearchResult(
        val world: WorldData,
        val exact: Boolean,
        val partial: Boolean
    )

    class VisitWorldGuiHolder(
        val query: String,
        val showBackButton: Boolean,
        val page: Int
    ) : InventoryHolder {
        lateinit var inv: Inventory
        override fun getInventory(): Inventory = inv
    }
}
