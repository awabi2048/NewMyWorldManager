package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiCommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiDiscoveryKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiFavoriteKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiCycle
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiInteractionGuidance
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuDisplaySpec
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
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import me.awabi2048.myworldmanager.util.FavoriteRegistrationTimestamp
import net.kyori.adventure.text.format.TextDecoration
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
                    ACTION_OTHER_WORLDS to MenuActionHandler(::openOtherWorlds),
                    ACTION_TOGGLE_CURRENT to MenuActionHandler(::toggleCurrentFavorite),
                ),
            ),
        )
    }

    fun open(
        player: Player,
        page: Int = 0,
        showBackButton: Boolean? = null,
    ) {
        val session = plugin.favoriteSessionManager.getSession(player.uniqueId)
        showBackButton?.let { session.showBackButton = it }
        plugin.settingsSessionManager.updateSessionAction(
            player,
            player.uniqueId,
            SettingsAction.FAVORITE_GUI,
            isGui = true,
        )
        runtime.navigate(player, route(page))
    }

    private fun render(player: Player, route: MenuRoute): InventoryMenuView {
        val lang = plugin.languageManager
        val session = plugin.favoriteSessionManager.getSession(player.uniqueId)
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        // 保存Mapの実装順に依存せず、登録時刻の古い順から新しい順へ明示的に並べます。
        val favoriteIds = stats.favoriteWorlds.entries
            .sortedBy { FavoriteRegistrationTimestamp.sortValue(it.value) }
            .map { it.key }
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
        elements += createPlayerHeadEntry(player, worlds.size, FavoriteMenuLayout.HEADER_CENTER_SLOT)
        val footer = FavoriteMenuLayout.footer(layout.size)
        if (currentPage > 0) {
            elements += navigationEntry(player, footer.previousPage, false, currentPage - 1)
        }
        if (currentPage < pageLayout.totalPages - 1) {
            elements += navigationEntry(player, footer.nextPage, true, currentPage + 1)
        }
        val currentWorld = plugin.worldConfigRepository.findByWorldName(player.world.name)
        currentWorld?.let {
            elements += createOtherWorldsEntry(player, footer.otherWorlds)
        }
        elements += createCurrentFavoriteEntry(player, currentWorld, footer.toggleCurrent)
        elements += plugin.currentWorldMenuElementFactory.create(player, footer.currentWorld)
        elements += createTagFilterEntry(player, session.selectedTag, footer.tagFilter)
        if (GuiHelper.canGoBack(player)) {
            elements += backEntry(player, layout.backSlot)
        }
        return InventoryMenuView(
            size = layout.size,
            title = GuiHelper.inventoryTitle(lang.getMessage(player, MyworldGuiFavoriteKeys.GUI_FAVORITE_TITLE)),
            elements = elements,
        )
    }

    private fun page(context: MenuActionContext): MenuActionResult {
        val targetPage = context.payload[PAGE]?.toIntOrNull() ?: return MenuActionResult.Rejected()
        return MenuActionResult.Success(
            MenuUpdate.Replace(route(targetPage)),
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
            MenuUpdate.Replace(route(0)),
        )
    }

    private fun world(context: MenuActionContext): MenuActionResult {
        val uuid = context.payload[WORLD_UUID]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return MenuActionResult.Rejected()
        val worldData = plugin.worldConfigRepository.findByUuid(uuid) ?: return MenuActionResult.Rejected()
        plugin.menuEntryRouter.openFavoriteMenu(context.player, worldData)
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun openOtherWorlds(context: MenuActionContext): MenuActionResult {
        val worldData = plugin.worldConfigRepository.findByWorldName(context.player.world.name)
            ?: return MenuActionResult.Rejected()
        plugin.menuEntryRouter.openVisitMenu(
            context.player,
            Bukkit.getOfflinePlayer(worldData.owner),
            0,
            worldData,
            guestAccessibleOnly = worldData.owner == context.player.uniqueId,
        )
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun toggleCurrentFavorite(context: MenuActionContext): MenuActionResult {
        val player = context.player
        val worldData = context.payload[WORLD_UUID]
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.let(plugin.worldConfigRepository::findByUuid)
            ?: return MenuActionResult.Rejected()
        if (worldData.owner == player.uniqueId) return MenuActionResult.Ignored
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        if (worldData.uuid in stats.favoriteWorlds) {
            plugin.menuEntryRouter.openFavoriteRemoveConfirm(player, worldData)
            return MenuActionResult.Success(MenuUpdate.None)
        }
        return when (plugin.favoriteStateService.toggle(
            player,
            worldData,
            me.awabi2048.myworldmanager.api.event.MwmFavoriteAddSource.FAVORITE_MENU,
        )) {
            me.awabi2048.myworldmanager.service.FavoriteStateService.ToggleResult.Added -> {
                player.sendMessage(plugin.languageManager.getMessage(player, MyworldMessagesKeys.MESSAGES_FAVORITE_ADDED))
                MenuActionResult.Success(MenuUpdate.Refresh)
            }
            me.awabi2048.myworldmanager.service.FavoriteStateService.ToggleResult.LimitReached -> {
                player.sendMessage(plugin.languageManager.getMessage(
                    player,
                    CommonKeys.ERROR_FAVORITE_LIMIT_REACHED,
                    mapOf("limit" to plugin.config.getInt("favorite.max_count", 1000)),
                ))
                MenuActionResult.Rejected()
            }
            me.awabi2048.myworldmanager.service.FavoriteStateService.ToggleResult.Removed ->
                MenuActionResult.Rejected()
        }
    }

    private fun createWorldEntry(player: Player, data: WorldData, slot: Int): MenuElement {
        val lang = plugin.languageManager
        val worldName = lang.getMessageStrict(player, data.name) ?: data.name
        val ownerName = PlayerNameUtil.getNameOrDefault(
            data.owner,
            lang.getMessage(player, CommonKeys.GENERAL_UNKNOWN),
        )
        val tagNames = data.tags.takeIf { it.isNotEmpty() }?.joinToString(", ") {
            plugin.worldTagManager.getDisplayName(player, it)
        }
        val isMember = data.owner == player.uniqueId ||
            player.uniqueId in data.moderators ||
            player.uniqueId in data.members
        val canWarp = MyWorldManagerApi.getWorldAccessPolicy().canDirectWorldWarp(player, data, isMember)
        val payload = mapOf(WORLD_UUID to data.uuid.toString())
        val actions = listOf(
            menuGestureAction(
                ACTION_WORLD,
                MenuGesture.ANY,
                lang.getMessage(player, MyworldGuiFavoriteKeys.GUI_FAVORITE_WORLD_ITEM_OPEN_ACTIONS),
                payload,
                safety = MenuActionSafety.NAVIGATION_ONLY,
            ),
        )
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = data.icon,
                name = GuiNameSpec.TargetIdentity(lang.getComponent(player, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_NAME, mapOf("world" to worldName))),
                role = GuiElementRole.ACTION,
                description = listOfNotNull(data.description.takeIf(String::isNotBlank)),
                data = buildList {
                    add(GuiMenuEntryData(lang.getMessage(player, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_OWNER), ownerName, GuiValueTone.INFO))
                    add(GuiMenuEntryData(lang.getMessage(player, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_FAVORITE), data.favorite, GuiValueTone.DANGER))
                    add(GuiMenuEntryData(
                        lang.getMessage(player, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_RECENT_VISITORS),
                        lang.getMessage(player, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_RECENT_VISITORS_VALUE, mapOf("count" to data.recentVisitors.sum())),
                        GuiValueTone.SUCCESS,
                    ))
                    tagNames?.let { add(GuiMenuEntryData(lang.getMessage(player, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_TAGS), it, GuiValueTone.PRIMARY)) }
                },
                warnings = buildList {
                    if (data.isArchived) add(lang.getMessage(player, MyworldGuiFavoriteKeys.GUI_FAVORITE_WORLD_ITEM_ARCHIVED_LABEL))
                    else if (!canWarp) add(lang.getMessage(player, MyworldGuiFavoriteKeys.GUI_FAVORITE_WORLD_ITEM_DIRECT_WARP_UNAVAILABLE))
                },
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
                MyworldGuiFavoriteKeys.GUI_FAVORITE_PLAYER_ICON_NAME,
                mapOf(
                    "player" to PlayerNameUtil.getNameOrDefault(
                        player.uniqueId,
                        lang.getMessage(player, CommonKeys.GENERAL_UNKNOWN),
                    ),
                ),
            )),
                role = GuiElementRole.CONTENT,
                data = listOf(GuiMenuEntryData(lang.getMessage(player, MyworldGuiFavoriteKeys.GUI_FAVORITE_PLAYER_ICON_LORE_COUNT), totalCount, GuiValueTone.SUCCESS)),
                playerHeadOwner = player.uniqueId,
            ),
        )
    }

    private fun createOtherWorldsEntry(player: Player, slot: Int): MenuElement {
        val lang = plugin.languageManager
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = Material.COMPASS,
                name = GuiNameSpec.FixedLabel(lang.getComponent(player, MyworldGuiFavoriteKeys.GUI_FAVORITE_FAVORITE_MENU_OTHER_WORLDS_NAME)),
                role = GuiElementRole.ACTION,
                description = lang.getMessageList(player, MyworldGuiFavoriteKeys.GUI_FAVORITE_FAVORITE_MENU_OTHER_WORLDS_LORE),
                actions = listOf(menuGestureAction(
                    ACTION_OTHER_WORLDS,
                    MenuGesture.ANY,
                    lang.getMessage(player, MyworldGuiFavoriteKeys.GUI_FAVORITE_FAVORITE_MENU_OTHER_WORLDS_ACTION),
                    safety = MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE,
                )),
            ),
        )
    }

    private fun createCurrentFavoriteEntry(player: Player, worldData: WorldData?, slot: Int): MenuElement {
        val lang = plugin.languageManager
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val warningKey = when {
            worldData == null -> "gui.favorite.favorite_menu.toggle.lore_restricted_not_managed"
            worldData.owner == player.uniqueId -> "gui.favorite.favorite_menu.toggle.lore_restricted_owner"
            else -> null
        }
        if (warningKey != null) {
            return CCSystem.getAPI().getGuiElementService().menuEntry(
                player,
                GuiMenuEntrySpec(
                    slot = slot,
                    material = Material.BARRIER,
                    name = GuiNameSpec.FixedLabel(lang.getComponent(player, MyworldGuiFavoriteKeys.GUI_FAVORITE_FAVORITE_MENU_TOGGLE_NAME_RESTRICTED)),
                    role = GuiElementRole.CONTENT,
                    warnings = listOf(lang.getMessage(player, warningKey)),
                ),
            )
        }
        val isFavorite = worldData!!.uuid in stats.favoriteWorlds
        val nameKey = if (isFavorite) {
            "gui.favorite.favorite_menu.toggle.name_remove"
        } else {
            "gui.favorite.favorite_menu.toggle.name_add"
        }
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = if (isFavorite) Material.RED_DYE else Material.GRAY_DYE,
                name = GuiNameSpec.FixedLabel(lang.getComponent(player, nameKey)),
                role = GuiElementRole.ACTION,
                actions = listOf(menuGestureAction(
                    ACTION_TOGGLE_CURRENT,
                    MenuGesture.ANY,
                    lang.getMessage(player, MyworldGuiFavoriteKeys.GUI_FAVORITE_FAVORITE_MENU_TOGGLE_ACTION),
                    mapOf(WORLD_UUID to worldData.uuid.toString()),
                    safety = if (isFavorite) MenuActionSafety.CONFIRM_ENTRY else MenuActionSafety.REVERSIBLE,
                    reversibleContract = if (isFavorite) null else MwmMenuActionSemantics.contract("favorite-toggle"),
                )),
            ),
        )
    }

    private fun createTagFilterEntry(player: Player, selectedTag: String?, slot: Int): MenuElement {
        val lang = plugin.languageManager
        val options = listOf("" to lang.getMessage(player, MyworldGuiDiscoveryKeys.GUI_DISCOVERY_TAG_FILTER_NO_SELECTION)) +
            plugin.worldTagManager.getEnabledTagIds().map {
                it to plugin.worldTagManager.getDisplayName(player, it)
            }
        val selected = options.firstOrNull { it.first == selectedTag.orEmpty() } ?: options.first()
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = plugin.menuConfigManager.getIconMaterial("favorite", "tag_filter", Material.NAME_TAG),
                name = GuiNameSpec.FixedLabel(lang.getComponent(player, MyworldGuiDiscoveryKeys.GUI_DISCOVERY_TAG_FILTER_NAME)),
                role = GuiElementRole.ACTION,
                data = listOf(GuiMenuEntryData(lang.getMessage(player, MyworldGuiDiscoveryKeys.GUI_DISCOVERY_TAG_FILTER_LABEL), selected.second, GuiValueTone.PRIMARY)),
                options = options.map { (id, displayName) -> GuiMenuEntryOption(displayName, id == selected.first) },
                actions = listOf(
                    menuGestureAction(
                        ACTION_TAG,
                        MenuGesture.ANY,
                        lang.getMessage(player, MyworldGuiCommonKeys.GUI_COMMON_ACTION_CYCLE),
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

    private fun route(page: Int): MenuRoute =
        MenuRoute(
            OWNER,
            ROUTE_ID,
            buildMap {
                put(PAGE, page.toString())
            },
        )

    companion object {
        private const val OWNER = "myworldmanager"
        private const val ROUTE_ID = "favorite-list"
        private const val PAGE = "page"
        private const val WORLD_UUID = "worldUuid"
        private const val ACTION_PAGE = "page"
        private const val ACTION_BACK = "back"
        private const val ACTION_TAG = "tag"
        private const val ACTION_WORLD = "world"
        private const val ACTION_OTHER_WORLDS = "other_worlds"
        private const val ACTION_TOGGLE_CURRENT = "toggle_current"
    }
}
