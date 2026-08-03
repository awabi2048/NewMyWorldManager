package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiCycle
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiInteractionGuidance
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuDisplaySpec
import com.awabi2048.ccsystem.api.gui.GuiMenuActionIntent
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryOption
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiValueTone
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuActionSafety
import com.awabi2048.ccsystem.api.gui.MenuGesture
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
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player

class FavoriteGui(private val plugin: MyWorldManager) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()
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
        val pageLayout = CCSystem.getAPI().getGuiLayoutService()
            .sevenColumnPage(worlds.size, route.payload[PAGE]?.toIntOrNull() ?: 0)
        val currentPage = pageLayout.page
        val layout = pageLayout.layout
        val elements = mutableListOf<MenuElement>()

        worlds.drop(pageLayout.startIndex).take(pageLayout.itemCount).forEachIndexed { index, data ->
            elements += createWorldEntry(player, data, layout.itemSlots[index])
        }
        if (worlds.isEmpty()) {
            val key = if (resolved.isEmpty()) {
                "gui.favorite.empty_message_no_favorites"
            } else {
                "gui.favorite.empty_message"
            }
            elements += CCSystem.getAPI().getGuiElementService().menuDisplay(
                GuiMenuDisplaySpec(
                    22,
                    GuiItemSpec(
                        Material.QUARTZ,
                        GuiNameSpec.FixedLabel(lang.getComponent(player, key).decoration(TextDecoration.ITALIC, false)),
                        GuiLoreSpec.None,
                        GuiElementRole.CONTENT,
                        1,
                    ),
                ),
            )
        }
        if (currentPage > 0) {
            elements += navigationEntry(player, layout.previousPageSlot, false, currentPage - 1)
        }
        elements += createPlayerHeadEntry(player, worlds.size, layout.actionSlot)
        if (currentPage < pageLayout.totalPages - 1) {
            elements += navigationEntry(player, layout.nextPageSlot, true, currentPage + 1)
        }
        elements += createTagFilterEntry(player, session.selectedTag, layout.size - 2)
        if (GuiHelper.canGoBack(player)) {
            elements += backEntry(player, layout.backSlot)
        }
        return InventoryMenuView(
            size = layout.size,
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
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun removeFavorite(player: Player, worldData: WorldData): MenuActionResult {
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        if (worldData.owner == player.uniqueId || worldData.uuid !in stats.favoriteWorlds) {
            return MenuActionResult.Ignored
        }
        showFavoriteRemovalConfirmation(player, worldData)
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun showFavoriteRemovalConfirmation(player: Player, worldData: WorldData) {
        plugin.menuEntryRouter.openFavoriteRemoveConfirm(player, worldData)
    }

    private fun createWorldEntry(player: Player, data: WorldData, slot: Int): MenuElement {
        val lang = plugin.languageManager
        val worldName = lang.getMessageStrict(player, data.name) ?: data.name
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
        val payload = mapOf(WORLD_UUID to data.uuid.toString())
        val actions = if (data.isArchived) {
            emptyList()
        } else {
            buildList {
                if (canWarp) {
                    add(menuGestureAction(ACTION_WORLD, MenuGesture.PLAIN_LEFT, lang.getMessage(player, "gui.favorite.world_item.warp"), payload, safety = MenuActionSafety.EXTERNAL_SIDE_EFFECT))
                    add(menuGestureAction(ACTION_WORLD, MenuGesture.PLAIN_RIGHT, lang.getMessage(player, "gui.favorite.world_item.preview"), payload, safety = MenuActionSafety.EXTERNAL_SIDE_EFFECT))
                }
                if (canUnfavorite) {
                    add(menuGestureAction(ACTION_WORLD, MenuGesture.SHIFT_RIGHT, lang.getMessage(player, "gui.favorite.world_item.unfavorite"), payload, safety = MenuActionSafety.CONFIRM_ENTRY))
                }
            }
        }
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = data.icon,
                name = GuiNameSpec.TargetIdentity(lang.getComponent(player, "gui.common.world_item_name", mapOf("world" to worldName))),
                role = if (actions.isEmpty()) GuiElementRole.CONTENT else GuiElementRole.ACTION,
                description = listOfNotNull(data.description.takeIf(String::isNotBlank)),
                data = buildList {
                    add(GuiMenuEntryData(lang.getMessage(player, "gui.common.world_item.owner"), ownerName, GuiValueTone.INFO))
                    add(GuiMenuEntryData(lang.getMessage(player, "gui.common.world_item.favorite"), data.favorite, GuiValueTone.DANGER))
                    add(GuiMenuEntryData(
                        lang.getMessage(player, "gui.common.world_item.recent_visitors"),
                        lang.getMessage(player, "gui.common.world_item.recent_visitors_value", mapOf("count" to data.recentVisitors.sum())),
                        GuiValueTone.SUCCESS,
                    ))
                    tagNames?.let { add(GuiMenuEntryData(lang.getMessage(player, "gui.common.world_item.tags"), it, GuiValueTone.PRIMARY)) }
                },
                warnings = if (data.isArchived) listOf(lang.getMessage(player, "gui.favorite.world_item.archived_label")) else emptyList(),
                actions = actions,
            ),
        )
    }

    private fun createPlayerHeadEntry(player: Player, totalCount: Int, slot: Int): MenuElement {
        val lang = plugin.languageManager
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = Material.PLAYER_HEAD,
                name = GuiNameSpec.FixedLabel(lang.getComponent(
                player,
                "gui.favorite.player_icon.name",
                mapOf(
                    "player" to PlayerNameUtil.getNameOrDefault(
                        player.uniqueId,
                        lang.getMessage(player, "general.unknown"),
                    ),
                ),
            )),
                role = GuiElementRole.CONTENT,
                data = listOf(GuiMenuEntryData(lang.getMessage(player, "gui.favorite.player_icon.lore_count"), totalCount, GuiValueTone.SUCCESS)),
                playerHeadOwner = player.uniqueId,
            ),
        )
    }

    private fun createTagFilterEntry(player: Player, selectedTag: String?, slot: Int): MenuElement {
        val lang = plugin.languageManager
        val options = listOf("" to lang.getMessage(player, "gui.discovery.tag_filter.no_selection")) +
            plugin.worldTagManager.getEnabledTagIds().map {
                it to plugin.worldTagManager.getDisplayName(player, it)
            }
        val selected = options.firstOrNull { it.first == selectedTag.orEmpty() } ?: options.first()
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = plugin.menuConfigManager.getIconMaterial("favorite", "tag_filter", Material.NAME_TAG),
                name = GuiNameSpec.FixedLabel(lang.getComponent(player, "gui.discovery.tag_filter.name")),
                role = GuiElementRole.ACTION,
                data = listOf(GuiMenuEntryData(lang.getMessage(player, "gui.discovery.tag_filter.label"), selected.second, GuiValueTone.PRIMARY)),
                options = options.map { (id, displayName) -> GuiMenuEntryOption(displayName, id == selected.first) },
                actions = listOf(
                    menuGestureAction(
                        ACTION_TAG,
                        MenuGesture.ANY,
                        lang.getMessage(player, "gui.common.action.cycle"),
                        safety = MenuActionSafety.REVERSIBLE,
                        reversibleContract = MwmMenuActionSemantics.contract("favorite-tag"),
                    ),
                ),
                interactionGuidance = GuiInteractionGuidance.LIST_SETTING,
            ),
        )
    }

    private fun navigationEntry(player: Player, slot: Int, next: Boolean, targetPage: Int): MenuElement {
        val key = if (next) "gui.common.next_page" else "gui.common.prev_page"
        val iconId = if (next) "next_page" else "prev_page"
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = plugin.menuConfigManager.getIconMaterial("favorite", iconId, Material.ARROW),
                name = GuiNameSpec.FixedLabel(plugin.languageManager.getComponent(player, key)),
                role = GuiElementRole.NAVIGATION,
                actions = listOf(
                    menuGestureAction(
                        ACTION_PAGE,
                        MenuGesture.LEFT_RIGHT,
                        plugin.languageManager.getMessage(player, key),
                        mapOf(PAGE to targetPage.toString()),
                        safety = MenuActionSafety.NAVIGATION_ONLY,
                    ),
                ),
                interactionGuidance = GuiInteractionGuidance.SINGLE_ACTION_CLICK,
            ),
        )
    }

    private fun backEntry(player: Player, slot: Int): MenuElement =
        CCSystem.getAPI().getGuiElementService().backEntry(
            player,
            slot,
            plugin.menuConfigManager.getIconMaterial("world_settings", "back", Material.REDSTONE),
        )

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
