package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import me.awabi2048.myworldmanager.util.WorldAccessMessageResolver
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

class VisitWorldGui(private val plugin: MyWorldManager) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()

    init {
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = ROUTE_ID,
                renderer = { context -> render(context.player, context.route) },
                actions = mapOf(
                    ACTION_BACK to MenuActionHandler(::back),
                    ACTION_PAGE to MenuActionHandler(::page),
                    ACTION_WORLD to MenuActionHandler(::world),
                ),
            ),
        )
    }

    fun hasSearchResult(player: Player, query: String): Boolean {
        val normalizedQuery = normalize(query)
        return searchWorlds(player, normalizedQuery).isNotEmpty()
    }

    fun open(player: Player, query: String, page: Int = 0, showBackButton: Boolean = false) {
        runtime.open(player, route(query, page, showBackButton))
    }

    private fun render(player: Player, route: MenuRoute): InventoryMenuView {
        val query = route.payload[QUERY].orEmpty()
        val showBackButton = route.payload[SHOW_BACK]?.toBooleanStrictOrNull() ?: false
        val requestedPage = route.payload[PAGE]?.toIntOrNull() ?: 0
        val normalizedQuery = normalize(query)
        val worlds = searchWorlds(player, normalizedQuery)

        val pageLayout = CCSystem.getAPI().getGuiLayoutService().sevenColumnPage(worlds.size, requestedPage)
        val currentPage = pageLayout.page
        val layout = pageLayout.layout
        val pageWorlds = worlds.drop(pageLayout.startIndex).take(pageLayout.itemCount)

        val lang = plugin.languageManager
        val title = GuiHelper.inventoryTitle(lang.getComponent(player, "gui.visitworld.title", mapOf("query" to query)))
        val elements = mutableListOf<MenuElement>()
        pageWorlds.forEachIndexed { index, worldData ->
            elements += MenuElement(
                layout.itemSlots[index],
                createWorldItem(player, worldData),
                GuiElementRole.ACTION,
                ACTION_WORLD,
                mapOf(WORLD_UUID to worldData.uuid.toString()),
            )
        }

        if (showBackButton) {
            elements += MenuElement(
                layout.backSlot,
                GuiHelper.createReturnItem(plugin, player, "visit"),
                GuiElementRole.BACK,
                ACTION_BACK,
            )
        }
        elements += MenuElement(
            layout.actionSlot,
            createInfoItem(player, query, worlds.size, pageWorlds.size, currentPage + 1, pageLayout.totalPages),
            GuiElementRole.CONTENT,
        )

        if (currentPage > 0) {
            elements += MenuElement(
                layout.previousPageSlot,
                GuiHelper.createPrevPageItem(plugin, player, "visit", currentPage - 1),
                GuiElementRole.NAVIGATION,
                ACTION_PAGE,
                mapOf(PAGE to (currentPage - 1).toString()),
            )
        }
        if (currentPage < pageLayout.totalPages - 1) {
            elements += MenuElement(
                layout.nextPageSlot,
                GuiHelper.createNextPageItem(plugin, player, "visit", currentPage + 1),
                GuiElementRole.NAVIGATION,
                ACTION_PAGE,
                mapOf(PAGE to (currentPage + 1).toString()),
            )
        }

        return InventoryMenuView(layout.size, title, elements)
    }

    private fun back(context: MenuActionContext): MenuActionResult {
        val player = context.player
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!CCSystem.getAPI().getMenuRuntimeService().back(player)) {
                plugin.settingsSessionManager.endSession(player)
            }
        })
        return MenuActionResult.Success(MenuUpdate.Close)
    }

    private fun page(context: MenuActionContext): MenuActionResult {
        val target = context.payload[PAGE]?.toIntOrNull() ?: return MenuActionResult.Rejected()
        return MenuActionResult.Success(
            MenuUpdate.Replace(
                route(
                    context.route.payload[QUERY].orEmpty(),
                    target,
                    context.route.payload[SHOW_BACK]?.toBooleanStrictOrNull() ?: false,
                ),
            ),
        )
    }

    private fun world(context: MenuActionContext): MenuActionResult {
        val player = context.player
        val uuid = context.payload[WORLD_UUID]
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return MenuActionResult.Rejected()
        val worldData = plugin.worldConfigRepository.findByUuid(uuid)
        val isMember = worldData != null && (
            worldData.owner == player.uniqueId ||
                worldData.moderators.contains(player.uniqueId) ||
                worldData.members.contains(player.uniqueId)
            )
        if (worldData == null || !MyWorldManagerApi.getWorldAccessPolicy().canUseVisitEntry(player, worldData, isMember)) {
            player.sendMessage(WorldAccessMessageResolver.visit(plugin.languageManager, player, worldData, isMember))
            return MenuActionResult.Rejected()
        }
        if (context.click.isLeftClick) {
            plugin.worldService.teleportToWorld(player, uuid) {
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        player,
                        "messages.warp_success",
                        mapOf("world" to worldData.name),
                    ),
                )
            }
            return MenuActionResult.Success(MenuUpdate.Close)
        }
        if (!context.click.isRightClick || isMember) return MenuActionResult.Ignored

        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        if (stats.favoriteWorlds.containsKey(uuid)) {
            stats.favoriteWorlds.remove(uuid)
            worldData.favorite = (worldData.favorite - 1).coerceAtLeast(0)
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.favorite_removed"))
        } else {
            val maxFavoriteCount = plugin.config.getInt("favorite.max_count", 1000)
            if (stats.favoriteWorlds.size >= maxFavoriteCount) {
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        player,
                        "error.favorite_limit_reached",
                        mapOf("limit" to maxFavoriteCount),
                    ),
                )
                return MenuActionResult.Rejected()
            }
            stats.favoriteWorlds[uuid] = LocalDate.now().toString()
            worldData.favorite++
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.favorite_added"))
        }
        plugin.playerStatsRepository.save(stats)
        plugin.worldConfigRepository.save(worldData)
        return MenuActionResult.Success(MenuUpdate.Refresh)
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

        val lore = mutableListOf<GuiLoreLine>(
            GuiLoreLine.Data(lang.getMessage(player, "gui.visitworld.info.query_label"), query, "§f"),
            GuiLoreLine.Data(lang.getMessage(player, "gui.visitworld.info.hit_label"), totalHit, "§b"),
            GuiLoreLine.Data(lang.getMessage(player, "gui.visitworld.info.shown_label"), shownCount, "§a")
        )
        if (totalPages > 1) {
            lore += GuiLoreLine.Data(lang.getMessage(player, "gui.visitworld.info.page_label"), "$currentPage/$totalPages", "§a")
        }

        meta.displayName(lang.getComponent(player, "gui.visitworld.info.name").decoration(TextDecoration.ITALIC, false))
        meta.lore(GuiItemFactory.menuLore(lore))

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

        val ownerName = PlayerNameUtil.getNameOrDefault(world.owner, lang.getMessage(viewer, "general.unknown"))
        val tagNames = if (world.tags.isNotEmpty()) {
            world.tags.joinToString(", ") {
                plugin.worldTagManager.getDisplayName(viewer, it)
            }
        } else {
            null
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
            if (world.description.isNotBlank()) add(GuiLoreBlock(listOf(GuiLoreLine.UserText(world.description))))
            add(GuiLoreBlock(buildList {
                add(GuiLoreLine.Data(lang.getMessage(viewer, "gui.common.world_item.owner"), ownerName, "§b"))
                add(GuiLoreLine.Data(lang.getMessage(viewer, "gui.common.world_item.favorite"), world.favorite, "§c"))
                add(GuiLoreLine.Data(
                    lang.getMessage(viewer, "gui.common.world_item.recent_visitors"),
                    lang.getMessage(viewer, "gui.common.world_item.recent_visitors_value", mapOf("count" to world.recentVisitors.sum())),
                    "§a"
                ))
                if (tagNames != null) add(GuiLoreLine.Data(lang.getMessage(viewer, "gui.common.world_item.tags"), tagNames, "§e"))
            }))
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

    private fun route(query: String, page: Int, showBackButton: Boolean) =
        MenuRoute(
            OWNER,
            ROUTE_ID,
            mapOf(
                QUERY to query,
                PAGE to page.toString(),
                SHOW_BACK to showBackButton.toString(),
            ),
        )

    companion object {
        private const val OWNER = "myworldmanager"
        private const val ROUTE_ID = "visit_world"
        private const val QUERY = "query"
        private const val PAGE = "page"
        private const val SHOW_BACK = "show_back"
        private const val WORLD_UUID = "world_uuid"
        private const val ACTION_BACK = "back"
        private const val ACTION_PAGE = "page"
        private const val ACTION_WORLD = "world"
    }
}
