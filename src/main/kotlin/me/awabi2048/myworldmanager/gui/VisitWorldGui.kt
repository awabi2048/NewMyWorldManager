package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.StructuredLore
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import java.util.Locale

class VisitWorldGui(private val plugin: MyWorldManager) {

    fun hasSearchResult(player: Player, query: String): Boolean {
        val normalizedQuery = normalize(query)
        return searchWorlds(player, normalizedQuery).isNotEmpty()
    }

    fun open(player: Player, query: String, page: Int = 0, showBackButton: Boolean = false) {
        val normalizedQuery = normalize(query)
        val worlds = searchWorlds(player, normalizedQuery)

        val pageLayout = CCSystem.getAPI().getGuiLayoutService().sevenColumnPage(worlds.size, page)
        val currentPage = pageLayout.page
        val layout = pageLayout.layout
        val pageWorlds = worlds.drop(pageLayout.startIndex).take(pageLayout.itemCount)

        val lang = plugin.languageManager
        val title = GuiHelper.inventoryTitle(lang.getComponent(player, "gui.visitworld.title", mapOf("query" to query)))

        GuiHelper.playMenuOpen(player, "visit")

        val holder = VisitWorldGuiHolder(query, showBackButton, currentPage)
        val inventory = Bukkit.createInventory(holder, layout.size, title)
        holder.inv = inventory

        GuiItemFactory.applyStandardFrame(inventory, emptyMaterial = null)

        val worldItemSlots = layout.itemSlots
        pageWorlds.forEachIndexed { index, worldData ->
            inventory.setItem(worldItemSlots[index], createWorldItem(player, worldData))
        }

        if (showBackButton) {
            inventory.setItem(layout.backSlot, GuiHelper.createReturnItem(plugin, player, "visit"))
        }
        inventory.setItem(
            layout.actionSlot,
            createInfoItem(player, query, worlds.size, pageWorlds.size, currentPage + 1, pageLayout.totalPages)
        )

        if (currentPage > 0) {
            inventory.setItem(layout.previousPageSlot, GuiHelper.createPrevPageItem(plugin, player, "visit", currentPage - 1))
        }
        if (currentPage < pageLayout.totalPages - 1) {
            inventory.setItem(layout.nextPageSlot, GuiHelper.createNextPageItem(plugin, player, "visit", currentPage + 1))
        }

        player.openInventory(inventory)
    }

    private fun searchWorlds(player: Player, normalizedQuery: String): List<WorldData> {
        if (normalizedQuery.isEmpty()) return emptyList()
        val queryTokens = normalizedQuery.split(Regex("\\s+")).filter { it.isNotBlank() }

        return plugin.worldConfigRepository.findAll()
            .asSequence()
            .filter { MyWorldManagerApi.getWorldAccessPolicy().canShowInVisitWorldList(player, it) }
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
        val grayPane = GuiItemFactory.decoration(Material.GRAY_STAINED_GLASS_PANE)
        GuiItemFactory.applyStandardFrame(inventory, emptyMaterial = Material.WHITE_STAINED_GLASS_PANE)

        for (row in 1..4) {
            val rowStart = row * 9
            inventory.setItem(rowStart, grayPane)
            inventory.setItem(rowStart + 8, grayPane)
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
        meta.lore(GuiItemFactory.componentMenuLore(lore))

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

        val warpAction = lang.getMessage(viewer, "gui.visit.world_item.warp")

        val stats = plugin.playerStatsRepository.findByUuid(viewer.uniqueId)
        val viewerPlayerUuid = viewer.uniqueId
        val isMember = world.owner == viewerPlayerUuid ||
            world.moderators.contains(viewerPlayerUuid) ||
            world.members.contains(viewerPlayerUuid)

        val favoriteAction = if (!isMember) {
            if (stats.favoriteWorlds.containsKey(world.uuid)) {
                lang.getMessage(viewer, "gui.visit.world_item.fav_remove")
            } else {
                lang.getMessage(viewer, "gui.visit.world_item.fav_add")
            }
        } else {
            ""
        }

        meta.lore(CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(buildList {
            if (formattedDesc.isNotBlank()) add(GuiLoreBlock(listOf(GuiLoreLine.Raw(formattedDesc))))
            add(GuiLoreBlock(
                (listOf(ownerLine, favoriteLine, visitorLine) + listOfNotNull(tagLine.takeIf(String::isNotBlank)))
                    .map(GuiLoreLine::Raw)
            ))
            add(GuiLoreBlock(buildList {
                add(GuiLoreLine.Action(lang.getMessage(viewer, "gui.settings.click.left"), warpAction))
                if (favoriteAction.isNotBlank()) {
                    add(GuiLoreLine.Action(lang.getMessage(viewer, "gui.settings.click.right"), favoriteAction))
                }
            }))
        })))

        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_WORLD_ITEM)
        ItemTag.setWorldUuid(item, world.uuid)
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
