package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiCycle
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
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
import java.util.UUID
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.session.PreviewSessionManager
import me.awabi2048.myworldmanager.session.PreviewSource
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.GuiLoreBuilder
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class FavoriteGui(private val plugin: MyWorldManager) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()
    private val itemsPerPage = 27

    init {
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = ROUTE_ID,
                renderer = { context -> render(context.player, context.route) },
                actions = mapOf(
                    ACTION_PAGE to MenuActionHandler(::page),
                    ACTION_BACK to MenuActionHandler(::back),
                    ACTION_TAG to MenuActionHandler(::tag),
                    ACTION_WORLD to MenuActionHandler(::world),
                ),
            ),
        )
    }

    fun open(
        player: Player,
        page: Int = 0,
        returnToWorld: WorldData? = null,
        returnToFavoriteMenu: Boolean = false,
        showBackButton: Boolean? = null,
    ) {
        val session = plugin.favoriteSessionManager.getSession(player.uniqueId)
        showBackButton?.let { session.showBackButton = it }
        session.returnToFavoriteMenu = returnToFavoriteMenu
        plugin.settingsSessionManager.updateSessionAction(
            player,
            player.uniqueId,
            SettingsAction.FAVORITE_GUI,
            isGui = true,
        )
        runtime.navigate(player, route(page, returnToWorld?.uuid, returnToFavoriteMenu))
    }

    private fun render(player: Player, route: MenuRoute): InventoryMenuView {
        val lang = plugin.languageManager
        val session = plugin.favoriteSessionManager.getSession(player.uniqueId)
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val favoriteIds = stats.favoriteWorlds.keys.toList()
        val selectedTag = session.selectedTag?.takeIf { it in plugin.worldTagManager.getEnabledTagIds() }
        if (selectedTag != session.selectedTag) session.selectedTag = null

        val resolved = favoriteIds.mapNotNull { uuid ->
            plugin.worldConfigRepository.findByUuid(uuid).also {
                if (it == null) stats.favoriteWorlds.remove(uuid)
            }
        }
        if (resolved.size != favoriteIds.size) plugin.playerStatsRepository.save(stats)
        val worlds = resolved.filter { selectedTag == null || selectedTag in it.tags }
        val totalPages = ((worlds.size + itemsPerPage - 1) / itemsPerPage).coerceAtLeast(1)
        val currentPage = route.payload[PAGE]?.toIntOrNull()?.coerceIn(0, totalPages - 1) ?: 0
        val elements = mutableListOf<MenuElement>()

        worlds.drop(currentPage * itemsPerPage).take(itemsPerPage).forEachIndexed { index, data ->
            elements += MenuElement(
                slot = index + 9,
                item = createWorldItem(player, data),
                role = GuiElementRole.ACTION,
                actionId = ACTION_WORLD,
                actionPayload = mapOf(WORLD_UUID to data.uuid.toString()),
            )
        }
        if (worlds.isEmpty()) {
            val empty = ItemStack(Material.QUARTZ)
            empty.itemMeta = empty.itemMeta?.also {
                val key = if (resolved.isEmpty()) {
                    "gui.favorite.empty_message_no_favorites"
                } else {
                    "gui.favorite.empty_message"
                }
                it.displayName(lang.getComponent(player, key).decoration(TextDecoration.ITALIC, false))
            }
            ItemTag.tagItem(empty, ItemTag.TYPE_GUI_INFO)
            elements += MenuElement(22, empty, GuiElementRole.CONTENT)
        }
        if (currentPage > 0) {
            elements += MenuElement(
                37,
                GuiHelper.createPrevPageItem(plugin, player, "favorite", currentPage - 1),
                GuiElementRole.NAVIGATION,
                ACTION_PAGE,
                mapOf(PAGE to (currentPage - 1).toString()),
            )
        }
        elements += MenuElement(40, createPlayerHead(player, worlds.size), GuiElementRole.CONTENT)
        if (currentPage < totalPages - 1) {
            elements += MenuElement(
                44,
                GuiHelper.createNextPageItem(plugin, player, "favorite", currentPage + 1),
                GuiElementRole.NAVIGATION,
                ACTION_PAGE,
                mapOf(PAGE to (currentPage + 1).toString()),
            )
        }
        elements += MenuElement(
            43,
            createTagFilterButton(player, session.selectedTag),
            GuiElementRole.ACTION,
            ACTION_TAG,
        )
        if (GuiHelper.canGoBack(player)) {
            elements += MenuElement(
                36,
                GuiHelper.createReturnItem(plugin, player, "favorite"),
                GuiElementRole.BACK,
                ACTION_BACK,
            )
        }
        return InventoryMenuView(
            size = 45,
            title = GuiHelper.inventoryTitle(lang.getMessage(player, "gui.favorite.title")),
            elements = elements,
        )
    }

    private fun page(context: MenuActionContext): MenuActionResult {
        val targetPage = context.payload[PAGE]?.toIntOrNull() ?: return MenuActionResult.Rejected()
        return MenuActionResult.Success(
            MenuUpdate.Replace(route(targetPage, returnWorldUuid(context.route), returnsToFavoriteMenu(context.route))),
        )
    }

    private fun back(context: MenuActionContext): MenuActionResult {
        return MenuActionResult.Success(MenuUpdate.Back)
    }

    private fun tag(context: MenuActionContext): MenuActionResult {
        val session = plugin.favoriteSessionManager.getSession(context.player.uniqueId)
        val options = plugin.worldTagManager.getEnabledTagIds() + null
        val direction = GuiCycle.direction(context.click) ?: return MenuActionResult.Ignored
        session.selectedTag = GuiCycle.selectNullable(session.selectedTag, options, direction)
        return MenuActionResult.Success(
            MenuUpdate.Replace(route(0, returnWorldUuid(context.route), returnsToFavoriteMenu(context.route))),
        )
    }

    private fun world(context: MenuActionContext): MenuActionResult {
        val uuid = context.payload[WORLD_UUID]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return MenuActionResult.Rejected()
        val worldData = plugin.worldConfigRepository.findByUuid(uuid) ?: return MenuActionResult.Rejected()
        if (worldData.isArchived) return MenuActionResult.Ignored
        return when {
            context.click.isLeftClick -> warp(context.player, worldData)
            context.click.isShiftClick && context.click.isRightClick -> removeFavorite(context.player, worldData)
            context.click.isRightClick -> preview(context.player, worldData)
            else -> MenuActionResult.Ignored
        }
    }

    private fun warp(player: Player, worldData: WorldData): MenuActionResult {
        val isMember = player.uniqueId == worldData.owner ||
            player.uniqueId in worldData.moderators ||
            player.uniqueId in worldData.members
        if (!MyWorldManagerApi.getWorldAccessPolicy().canUseSharedEntry(player, worldData, isMember)) {
            return MenuActionResult.Ignored
        }
        plugin.worldService.teleportToWorld(player, worldData.uuid) {
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

    private fun preview(player: Player, worldData: WorldData): MenuActionResult {
        plugin.previewSessionManager.startPreview(
            player,
            PreviewSessionManager.PreviewTarget.World(worldData),
            PreviewSource.FAVORITE_MENU,
        )
        return MenuActionResult.Success(MenuUpdate.Close)
    }

    private fun removeFavorite(player: Player, worldData: WorldData): MenuActionResult {
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        if (worldData.owner == player.uniqueId || worldData.uuid !in stats.favoriteWorlds) {
            return MenuActionResult.Ignored
        }
        Bukkit.getScheduler().runTask(
            plugin,
            Runnable { showFavoriteRemovalConfirmation(player, worldData) },
        )
        return MenuActionResult.Success(MenuUpdate.Close)
    }

    private fun showFavoriteRemovalConfirmation(player: Player, worldData: WorldData) {
        val lang = plugin.languageManager
        val title = LegacyComponentSerializer.legacySection().deserialize(
            lang.getMessage(player, "gui.favorite.remove_confirm.title"),
        )
        val body = lang.getMessageList(
            player,
            "gui.favorite.remove_confirm.lore",
            mapOf("world" to worldData.name),
        ).map(LegacyComponentSerializer.legacySection()::deserialize)
        DialogConfirmManager.showConfirmationByPreference(
            player,
            plugin,
            title,
            body,
            "mwm:confirm/favorite_remove/${worldData.uuid}",
            "mwm:confirm/favorite_cancel",
            lang.getMessage(player, "gui.favorite.remove_confirm.confirm"),
            lang.getMessage(player, "gui.common.cancel"),
        ) {
            plugin.menuEntryRouter.openFavoriteRemoveConfirm(
                player,
                worldData,
                onBedrockConfirm = { confirmFavoriteRemoval(player, worldData) },
                onBedrockCancel = {
                    plugin.menuEntryRouter.openFavoriteList(
                        player,
                        0,
                        returnToFavoriteMenu = plugin.favoriteSessionManager
                            .getSession(player.uniqueId)
                            .returnToFavoriteMenu,
                    )
                },
            )
        }
    }

    private fun confirmFavoriteRemoval(player: Player, worldData: WorldData) {
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        if (stats.favoriteWorlds.remove(worldData.uuid) != null) {
            worldData.favorite = (worldData.favorite - 1).coerceAtLeast(0)
            plugin.playerStatsRepository.save(stats)
            plugin.worldConfigRepository.save(worldData)
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.favorite_removed"))
            plugin.soundManager.playActionSound(player, "favorite", "favorite_remove")
        }
        plugin.menuEntryRouter.openFavoriteList(
            player,
            0,
            returnToFavoriteMenu = plugin.favoriteSessionManager
                .getSession(player.uniqueId)
                .returnToFavoriteMenu,
        )
    }

    private fun createWorldItem(player: Player, data: WorldData): ItemStack {
        val item = ItemStack(data.icon)
        val meta = item.itemMeta ?: return item
        val lang = plugin.languageManager
        val worldName = lang.getMessageStrict(player, data.name) ?: data.name
        meta.displayName(
            lang.getComponent(player, "gui.common.world_item_name", mapOf("world" to worldName))
                .decoration(TextDecoration.ITALIC, false),
        )
        val ownerName = PlayerNameUtil.getNameOrDefault(
            data.owner,
            lang.getMessage(player, "general.unknown"),
        )
        val tagNames = data.tags.takeIf { it.isNotEmpty() }?.joinToString(", ") {
            plugin.worldTagManager.getDisplayName(player, it)
        }
        val isMember = data.owner == player.uniqueId ||
            player.uniqueId in data.moderators ||
            player.uniqueId in data.members
        val canWarp = MyWorldManagerApi.getWorldAccessPolicy().canUseSharedEntry(player, data, isMember)
        val canUnfavorite = !data.isArchived && !isMember
        meta.lore(CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(buildList {
            if (data.description.isNotBlank()) {
                add(GuiLoreBlock(listOf(GuiLoreLine.UserText(data.description))))
            }
            add(GuiLoreBlock(buildList {
                add(GuiLoreLine.Data(lang.getMessage(player, "gui.common.world_item.owner"), ownerName, "§b"))
                add(GuiLoreLine.Data(lang.getMessage(player, "gui.common.world_item.favorite"), data.favorite, "§c"))
                add(GuiLoreLine.Data(
                    lang.getMessage(player, "gui.common.world_item.recent_visitors"),
                    lang.getMessage(
                        player,
                        "gui.common.world_item.recent_visitors_value",
                        mapOf("count" to data.recentVisitors.sum()),
                    ),
                    "§a",
                ))
                if (tagNames != null) {
                    add(GuiLoreLine.Data(lang.getMessage(player, "gui.common.world_item.tags"), tagNames, "§e"))
                }
            }))
            if (data.isArchived) {
                add(GuiLoreBlock(listOf(
                    GuiLoreLine.Warning(lang.getMessage(player, "gui.favorite.world_item.archived_label")),
                )))
            }
            val actions = buildList {
                if (canWarp) {
                    add(GuiLoreLine.Action(
                        lang.getMessage(player, "gui.settings.click.left"),
                        lang.getMessage(player, "gui.favorite.world_item.warp"),
                    ))
                }
                if (canUnfavorite) {
                    add(GuiLoreLine.Action(
                        lang.getMessage(player, "lore.click.shift_right"),
                        lang.getMessage(player, "gui.favorite.world_item.unfavorite"),
                    ))
                }
            }
            if (actions.isNotEmpty()) add(GuiLoreBlock(actions))
        })))
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_WORLD_ITEM)
        ItemTag.setWorldUuid(item, data.uuid)
        return item
    }

    private fun createPlayerHead(player: Player, totalCount: Int): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? org.bukkit.inventory.meta.SkullMeta ?: return item
        meta.owningPlayer = player
        meta.displayName(
            lang.getComponent(
                player,
                "gui.favorite.player_icon.name",
                mapOf(
                    "player" to PlayerNameUtil.getNameOrDefault(
                        player.uniqueId,
                        lang.getMessage(player, "general.unknown"),
                    ),
                ),
            ).decoration(TextDecoration.ITALIC, false),
        )
        meta.lore(GuiLoreBuilder(lang, player).block(listOf(
            GuiLoreLine.Data(
                lang.getMessage(player, "gui.favorite.player_icon.lore_count"),
                totalCount,
                "§a",
            ),
        )).build())
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_INFO)
        return item
    }

    private fun createTagFilterButton(player: Player, selectedTag: String?): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(
            plugin.menuConfigManager.getIconMaterial("favorite", "tag_filter", Material.NAME_TAG),
        )
        val meta = item.itemMeta ?: return item
        meta.displayName(
            lang.getComponent(player, "gui.discovery.tag_filter.name")
                .decoration(TextDecoration.ITALIC, false),
        )
        val options = listOf("" to lang.getMessage(player, "gui.discovery.tag_filter.no_selection")) +
            plugin.worldTagManager.getEnabledTagIds().map {
                it to plugin.worldTagManager.getDisplayName(player, it)
            }
        val selected = options.firstOrNull { it.first == selectedTag.orEmpty() } ?: options.first()
        meta.lore(CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Rich(buildList {
            add(GuiLoreLine.Data(
                lang.getMessage(player, "gui.discovery.tag_filter.label"),
                selected.second,
                "§e",
            ))
            add(GuiLoreLine.Spacer)
            options.forEach { (id, displayName) ->
                add(GuiLoreLine.Option(displayName, id == selected.first, "§e", "§7"))
            }
            add(GuiLoreLine.Spacer)
            add(GuiLoreLine.Action(
                lang.getMessage(player, "lore.click.left"),
                lang.getMessage(player, "gui.discovery.tag_filter.action.next"),
            ))
            add(GuiLoreLine.Action(
                lang.getMessage(player, "lore.click.right"),
                lang.getMessage(player, "gui.discovery.tag_filter.action.clear"),
            ))
        }, GuiLoreFrame.BOTH)))
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_FAVORITE_TAG)
        return item
    }

    private fun route(page: Int, returnWorld: UUID?, returnToFavoriteMenu: Boolean): MenuRoute =
        MenuRoute(
            OWNER,
            ROUTE_ID,
            buildMap {
                put(PAGE, page.toString())
                returnWorld?.let { put(RETURN_WORLD_UUID, it.toString()) }
                put(RETURN_TO_FAVORITE_MENU, returnToFavoriteMenu.toString())
            },
        )

    private fun returnWorldUuid(route: MenuRoute): UUID? =
        route.payload[RETURN_WORLD_UUID]?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private fun returnsToFavoriteMenu(route: MenuRoute): Boolean =
        route.payload[RETURN_TO_FAVORITE_MENU].toBoolean()

    companion object {
        private const val OWNER = "myworldmanager"
        private const val ROUTE_ID = "favorite-list"
        private const val PAGE = "page"
        private const val WORLD_UUID = "worldUuid"
        private const val RETURN_WORLD_UUID = "returnWorldUuid"
        private const val RETURN_TO_FAVORITE_MENU = "returnToFavoriteMenu"
        private const val ACTION_PAGE = "page"
        private const val ACTION_BACK = "back"
        private const val ACTION_TAG = "tag"
        private const val ACTION_WORLD = "world"
    }
}
