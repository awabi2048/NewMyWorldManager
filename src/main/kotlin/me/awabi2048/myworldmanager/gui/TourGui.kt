package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiCommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiFavoriteKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuDisplaySpec
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import com.awabi2048.ccsystem.api.gui.GuiStructuredMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiValueTone
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiInteractionGuidance
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuActionSafety
import com.awabi2048.ccsystem.api.gui.MenuGesture
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuSoundPresets
import com.awabi2048.ccsystem.api.gui.MenuRuntimeActions
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import com.awabi2048.ccsystem.api.gui.MenuViewCategory
import com.awabi2048.ccsystem.api.gui.PlayerInventoryInteraction
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.TourData
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.GuiHelper
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import java.util.UUID

class TourGui(private val plugin: MyWorldManager) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()
    // ツアー一覧は共通の3～6行可変レイアウトを使い、ヘッダー中央と下段を操作領域として使う。
    init {
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = START_CONFIRM_ROUTE,
                renderer = { context -> renderStartConfirm(context.player, context.route) },
                actions = mapOf(
                    ACTION_START to MenuActionHandler(::startTour),
                    ACTION_START_CANCEL to MenuActionHandler {
                        MenuActionResult.Success(MenuUpdate.Back)
                    },
                ),
                openSound = MenuSoundPresets.CONFIRMATION_OPEN,
            ),
        )
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = STOP_CONFIRM_ROUTE,
                renderer = { context -> renderStopConfirm(context.player, context.route) },
                actions = mapOf(
                    ACTION_STOP_CONFIRM to MenuActionHandler(::confirmStopTour),
                    ACTION_STOP_CANCEL to MenuActionHandler {
                        MenuActionResult.Success(MenuUpdate.Back)
                    },
                ),
                openSound = MenuSoundPresets.CONFIRMATION_OPEN,
            ),
        )
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = BIND_SIGN_ROUTE,
                renderer = { context -> renderBindSignMenu(context.player, context.route) },
                actions = mapOf(
                    ACTION_PAGE to MenuActionHandler(::changeTourPage),
                    ACTION_BIND_SIGN to MenuActionHandler(::bindSign),
                    ACTION_BIND_CANCEL to MenuActionHandler(::cancelBindSign),
                ),
            ),
        )
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = SINGLE_EDIT_ROUTE,
                renderer = { context -> renderSingleEditMenu(context.player, context.route) },
                actions = mapOf(
                    ACTION_SINGLE_BACK to MenuActionHandler(::singleBack),
                    ACTION_EDIT_TEXT to MenuActionHandler(::editText),
                    ACTION_EDIT_ICON to MenuActionHandler(::editIcon),
                    ACTION_DELETE to MenuActionHandler(::openDelete),
                    ACTION_ADD_WAYPOINT to MenuActionHandler(::addWaypoint),
                    ACTION_OPEN_WAYPOINT to MenuActionHandler(::openWaypoint),
                    ACTION_REORDER_CANCEL to MenuActionHandler(::cancelWaypointReorder),
                    ACTION_REORDER_BEFORE to MenuActionHandler(::insertWaypointBefore),
                    ACTION_REORDER_END to MenuActionHandler(::insertWaypointAtEnd),
                    MenuRuntimeActions.PLAYER_INVENTORY_CLICK to
                        MenuActionHandler(::selectEditIcon),
                ),
            ),
        )
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = DISCARD_CONFIRM_ROUTE,
                renderer = { context -> renderDiscardConfirm(context.player, context.route) },
                actions = mapOf(
                    ACTION_DISCARD_CONFIRM to MenuActionHandler(::confirmDiscard),
                    ACTION_DISCARD_CANCEL to MenuActionHandler(::cancelDiscard),
                ),
                openSound = MenuSoundPresets.CONFIRMATION_OPEN,
            ),
        )
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = WAYPOINT_EDIT_ROUTE,
                renderer = { context -> renderWaypointEditMenu(context.player, context.route) },
                actions = mapOf(
                    ACTION_WAYPOINT_NAME to MenuActionHandler(::editWaypointName),
                    ACTION_WAYPOINT_DESCRIPTION to MenuActionHandler(::editWaypointDescription),
                    ACTION_WAYPOINT_ICON to MenuActionHandler(::editWaypointIcon),
                    ACTION_WAYPOINT_POSITION to MenuActionHandler(::editWaypointPosition),
                    ACTION_WAYPOINT_DELETE to MenuActionHandler(::openWaypointDelete),
                    ACTION_WAYPOINT_REORDER to MenuActionHandler(::startWaypointReorder),
                    MenuRuntimeActions.PLAYER_INVENTORY_CLICK to MenuActionHandler(::selectEditIcon),
                ),
            ),
        )
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = WAYPOINT_DELETE_CONFIRM_ROUTE,
                renderer = { context -> renderWaypointDeleteConfirm(context.player, context.route) },
                actions = mapOf(
                    ACTION_WAYPOINT_DELETE_CONFIRM to MenuActionHandler(::confirmWaypointDelete),
                    ACTION_WAYPOINT_DELETE_CANCEL to MenuActionHandler {
                        MenuActionResult.Success(MenuUpdate.Back)
                    },
                ),
                openSound = MenuSoundPresets.CONFIRMATION_OPEN,
            ),
        )
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = EDIT_ROUTE,
                renderer = { context -> renderEditMenu(context.player, context.route) },
                actions = mapOf(
                    ACTION_PAGE to MenuActionHandler(::changeTourPage),
                    ACTION_BACK to MenuActionHandler {
                        MenuActionResult.Success(MenuUpdate.Back)
                    },
                    ACTION_CREATE to MenuActionHandler(::createTour),
                    ACTION_EDIT to MenuActionHandler(::editTour),
                    ACTION_OPEN_VISITOR to MenuActionHandler(::openVisitorFromEdit),
                    ACTION_STOP to MenuActionHandler(::stopTour),
                    ACTION_SKIP to MenuActionHandler(::skipTourWaypoint),
                ),
            ),
        )
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = DELETE_CONFIRM_ROUTE,
                renderer = { context -> renderDeleteConfirm(context.player, context.route) },
                actions = mapOf(
                    ACTION_DELETE_CONFIRM to MenuActionHandler(::confirmDelete),
                    ACTION_DELETE_CANCEL to MenuActionHandler(::cancelDelete),
                ),
                openSound = MenuSoundPresets.CONFIRMATION_OPEN,
            ),
        )
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = VISITOR_ROUTE,
                renderer = { context -> renderPagedTours(context.player, context.route, true) },
                actions = mapOf(
                    ACTION_PAGE to MenuActionHandler(::changeTourPage),
                    ACTION_SELECT to MenuActionHandler(::selectTour),
                    ACTION_STOP to MenuActionHandler(::stopTour),
                    ACTION_SKIP to MenuActionHandler(::skipTourWaypoint),
                ),
            ),
        )
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = START_SELECTION_ROUTE,
                renderer = { context -> renderPagedTours(context.player, context.route, false) },
                actions = mapOf(
                    ACTION_PAGE to MenuActionHandler(::changeTourPage),
                    ACTION_SELECT to MenuActionHandler(::selectTour),
                ),
            ),
        )
    }

    fun openVisitorMenu(player: Player, worldData: WorldData, page: Int = 0) {
        runtime.open(
            player,
            MenuRoute(
                OWNER,
                VISITOR_ROUTE,
                mapOf("world" to worldData.uuid.toString(), "page" to page.coerceAtLeast(0).toString()),
            ),
        )
    }

    fun openStartSelectionMenu(player: Player, worldData: WorldData, signUuid: java.util.UUID) {
        runtime.open(
            player,
            MenuRoute(
                OWNER,
                START_SELECTION_ROUTE,
                mapOf(
                    "world" to worldData.uuid.toString(),
                    "page" to "0",
                    "sign" to signUuid.toString(),
                ),
            ),
        )
    }

    fun openStartConfirm(player: Player, worldData: WorldData, tour: TourData) {
        runtime.open(player, tourRoute(START_CONFIRM_ROUTE, worldData.uuid, tour.uuid))
    }

    private fun renderStartConfirm(player: Player, route: MenuRoute): InventoryMenuView {
        val worldData = world(route) ?: error("ツアー対象ワールドがありません")
        val tour = tour(worldData, route) ?: error("開始対象ツアーがありません")
        val ownerName = Bukkit.getOfflinePlayer(tour.createdBy ?: worldData.owner).name
            ?: plugin.languageManager.getMessage(player, CommonKeys.GENERAL_UNKNOWN)
        val replacingActiveTour = plugin.tourSessionManager.get(player.uniqueId) != null

        val previewLines = buildList {
            if (tour.description.isNotBlank()) add(GuiLoreLine.UserText(tour.description))
            add(GuiLoreLine.Metadata("by", ownerName))
            if (replacingActiveTour) {
                add(GuiLoreLine.Warning(
                    plugin.languageManager.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_START_CONFIRM_REPLACE_ACTIVE),
                ))
            }
        }
        val layout = GuiHelper.confirmationLayout()
        return InventoryMenuView(
            size = layout.size,
            title = GuiHelper.inventoryTitle(Component.text("§b【${tour.name}】")),
            elements = listOf(
                displayEntry(layout.previewSlot, Material.FILLED_MAP, "§b【${tour.name}】", previewLines, GuiElementRole.CONTENT, GuiLoreFrame.BOTH),
                actionEntry(
                layout.confirmSlot,
                player,
                Material.LIME_WOOL,
                "§eこのツアーをはじめる！",
                emptyList(),
                plugin.languageManager.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_TOUR_ITEM_ACTION_START),
                ACTION_START,
                GuiElementRole.CONFIRM),
                simpleActionEntry(player, layout.cancelSlot, Material.RED_WOOL, CommonKeys.GUI_COMMON_CANCEL, GuiElementRole.CANCEL, ACTION_START_CANCEL),
            ),
            category = MenuViewCategory.CONFIRMATION,
        )
    }

    private fun renderStopConfirm(player: Player, route: MenuRoute): InventoryMenuView {
        val layout = GuiHelper.confirmationLayout()
        return InventoryMenuView(
            size = layout.size,
            title = GuiHelper.inventoryTitle(
                Component.text(plugin.languageManager.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_STOP_CONFIRM_TITLE)),
            ),
            elements = listOf(
                displayEntry(
                    layout.previewSlot,
                    Material.BARRIER,
                    plugin.languageManager.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_STOP_DISPLAY),
                    listOf(GuiLoreLine.Warning(plugin.languageManager.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_STOP_CONFIRM_QUESTION))),
                ),
                actionEntry(
                    layout.confirmSlot,
                    player,
                    Material.LIME_WOOL,
                    plugin.languageManager.getMessage(player, CommonKeys.GUI_COMMON_CONFIRM),
                    emptyList(),
                    plugin.languageManager.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_STOP_CONFIRM_CONFIRM),
                    ACTION_STOP_CONFIRM,
                    GuiElementRole.CONFIRM,
                ),
                simpleActionEntry(player, layout.cancelSlot, Material.RED_WOOL, CommonKeys.GUI_COMMON_CANCEL, GuiElementRole.CANCEL, ACTION_STOP_CANCEL),
            ),
            category = MenuViewCategory.CONFIRMATION,
        )
    }

    private fun renderPagedTours(
        player: Player,
        route: MenuRoute,
        showWorldIcon: Boolean,
    ): InventoryMenuView {
        val worldData = world(route) ?: error("ツアー対象ワールドがありません")
        val signUuid = route.payload["sign"]?.let(UUID::fromString)
        val tours = if (signUuid == null) {
            plugin.tourManager.validTours(worldData)
        } else {
            plugin.tourManager.findToursBySign(worldData, signUuid)
        }
        val requestedPage = route.payload["page"]?.toIntOrNull() ?: 0
        val page = CCSystem.getAPI().getGuiLayoutService().sevenColumnPage(tours.size, requestedPage)
        val layout = page.layout
        val safePage = page.page
        val elements = mutableListOf<MenuElement>()
        tours.drop(page.startIndex).take(page.itemCount).forEachIndexed { index, tour ->
            elements += createTourEntry(layout.itemSlots[index], player, worldData, tour, false, ACTION_SELECT)
        }
        val footerStart = layout.size - 9
        if (showWorldIcon) {
            elements += createCurrentWorldEntry(player, worldData, 4)
            val activeInWorld = plugin.tourSessionManager.get(player.uniqueId)?.worldUuid == worldData.uuid
            if (activeInWorld) {
                elements += actionEntry(
                    footerStart + 2,
                    player,
                    Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                    plugin.languageManager.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_SKIP_DISPLAY),
                    emptyList(),
                    plugin.languageManager.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_SKIP_ACTION),
                    ACTION_SKIP,
                    GuiElementRole.ACTION,
                )
                elements += actionEntry(
                    footerStart + 6,
                    player,
                    Material.BARRIER,
                    plugin.languageManager.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_STOP_DISPLAY),
                    emptyList(),
                    plugin.languageManager.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_STOP_ACTION),
                    ACTION_STOP,
                    GuiElementRole.CANCEL,
                )
            }
            if (GuiHelper.canGoBack(player)) {
                elements += CCSystem.getAPI().getGuiElementService().backEntry(
                    player,
                    footerStart + 4,
                    plugin.menuConfigManager.getIconMaterial("world_settings", "back", Material.REDSTONE),
                )
            }
        }
        if (safePage > 0) {
            elements += navigationEntry(player, layout.previousPageSlot, false, safePage - 1)
        }
        if (safePage + 1 < page.totalPages) {
            elements += navigationEntry(player, layout.nextPageSlot, true, safePage + 1)
        }
        val titleKey = if (showWorldIcon) {
            MyworldGuiCommonKeys.GUI_TOUR_MENU_VISITOR_TITLE
        } else {
            MyworldGuiCommonKeys.GUI_TOUR_MENU_START_SELECTION_TITLE
        }
        return InventoryMenuView(
            size = layout.size,
            title = GuiHelper.inventoryTitle(Component.text(plugin.languageManager.getMessage(player, titleKey))),
            elements = elements,
        )
    }

    fun openEditMenu(player: Player, worldData: WorldData, page: Int = 0) {
        runtime.navigate(player, editRoute(worldData.uuid, page))
    }

    fun editRoute(worldUuid: UUID, page: Int = 0): MenuRoute =
        MenuRoute(
            OWNER,
            EDIT_ROUTE,
            mapOf("world" to worldUuid.toString(), "page" to page.coerceAtLeast(0).toString()),
        )

    private fun stopTour(context: MenuActionContext): MenuActionResult {
        val worldUuid = context.route.payload["world"]?.let(UUID::fromString)
        if (plugin.tourSessionManager.get(context.player.uniqueId)?.worldUuid != worldUuid) {
            return MenuActionResult.Rejected()
        }
        return MenuActionResult.Success(
            MenuUpdate.Navigate(
                MenuRoute(
                    OWNER,
                    STOP_CONFIRM_ROUTE,
                    context.route.payload,
                ),
            ),
        )
    }

    private fun confirmStopTour(context: MenuActionContext): MenuActionResult {
        plugin.tourManager.stopTour(context.player)
        return MenuActionResult.Success(MenuUpdate.Back)
    }

    private fun renderEditMenu(player: Player, route: MenuRoute): InventoryMenuView {
        val worldData = world(route) ?: error("ツアー対象ワールドがありません")
        val lang = plugin.languageManager
        val tours = worldData.tours.sortedBy { it.createdAt }
        val canCreate = worldData.tours.size < plugin.tourManager.getTourLimit(player, worldData)
        val activeInWorld = plugin.tourSessionManager.get(player.uniqueId)?.worldUuid == worldData.uuid
        // フッター3枠目は、進行中だけスキップ操作を優先します。進行していないときは
        // 旧画面と同じ位置へ新規作成を戻し、作成操作を本文へ重複配置しません。
        val createInContent = canCreate && activeInWorld
        val listItemCount = tours.size + if (createInContent) 1 else 0
        val page = CCSystem.getAPI().getGuiLayoutService().sevenColumnPage(
            listItemCount,
            route.payload["page"]?.toIntOrNull() ?: 0,
        )
        val layout = page.layout
        val safePage = page.page
        val elements = mutableListOf<MenuElement>()
        tours.drop(page.startIndex).take(page.itemCount).forEachIndexed { index, tour ->
            elements += createTourEntry(layout.itemSlots[index], player, worldData, tour, true, ACTION_EDIT)
        }
        if (createInContent && tours.size in page.startIndex until (page.startIndex + page.itemCount)) {
            elements += actionEntry(
                layout.itemSlots[tours.size - page.startIndex],
                player,
                Material.NETHER_STAR,
                lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_CREATE_DISPLAY),
                listOf(GuiLoreLine.Text(lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_CREATE_DESCRIPTION))),
                lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_CREATE_ACTION),
                ACTION_CREATE,
                gesture = MenuGesture.ANY,
            )
        }
        val footerStart = layout.size - 9
        if (activeInWorld) {
            elements += actionEntry(
                footerStart + 1,
                player,
                Material.BARRIER,
                lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_STOP_DISPLAY),
                emptyList(),
                lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_STOP_ACTION),
                ACTION_STOP,
                GuiElementRole.CANCEL,
            )
            elements += actionEntry(
                footerStart + 2,
                player,
                Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_SKIP_DISPLAY),
                emptyList(),
                lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_SKIP_ACTION),
                ACTION_SKIP,
            )
        } else if (canCreate) {
            // 旧実装の新規作成位置（フッター3スロット目）を復元します。
            elements += actionEntry(
                footerStart + 2,
                player,
                Material.NETHER_STAR,
                lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_CREATE_DISPLAY),
                listOf(GuiLoreLine.Text(lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_CREATE_DESCRIPTION))),
                lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_CREATE_ACTION),
                ACTION_CREATE,
                gesture = MenuGesture.ANY,
            )
        }
        if (GuiHelper.canGoBack(player)) {
            elements += CCSystem.getAPI().getGuiElementService().backEntry(
                player,
                footerStart + 4,
                plugin.menuConfigManager.getIconMaterial("world_settings", "back", Material.REDSTONE),
            )
        }
        elements += createCurrentWorldEntry(player, worldData, 4)
        elements += nameOnlyActionEntry(
            footerStart + 6,
            player,
            Material.FILLED_MAP,
            lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_VISITOR_SWITCH_DISPLAY),
            lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_VISITOR_SWITCH_ACTION),
            ACTION_OPEN_VISITOR,
        )
        if (safePage > 0) {
            elements += navigationEntry(player, layout.previousPageSlot, false, safePage - 1)
        }
        if (safePage + 1 < page.totalPages) {
            elements += navigationEntry(player, layout.nextPageSlot, true, safePage + 1)
        }
        return InventoryMenuView(
            size = layout.size,
            title = GuiHelper.inventoryTitle(Component.text(lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_EDIT_TITLE))),
            elements = elements,
        )
    }

    fun openSingleEditMenu(player: Player, worldData: WorldData, tour: TourData, isNew: Boolean = false) {
        runtime.navigate(
            player,
            tourRoute(SINGLE_EDIT_ROUTE, worldData.uuid, tour.uuid, isNew),
        )
    }

    private fun renderSingleEditMenu(player: Player, route: MenuRoute): InventoryMenuView {
        val worldData = world(route) ?: error("ツアー対象ワールドがありません")
        val session = plugin.tourSessionManager.getEdit(player.uniqueId)
            ?: error("ツアー編集セッションがありません")
        val tour = session.draft
        val lang = plugin.languageManager
        val hasAddSlot = tour.waypoints.size < 28
        val layout = CCSystem.getAPI().getGuiLayoutService().sevenColumnList(
            (tour.waypoints.size + if (hasAddSlot) 1 else 0).coerceAtMost(28),
        )
        val elements = mutableListOf<MenuElement>()
        val reorderUuid = session.reorderingWaypointUuid
        // 編集対象を常にヘッダー中央へ表示し、本文とフッターの役割を混在させないようにします。
        elements += createCurrentTourEntry(player, tour, 4)
        tour.waypoints.take(28).forEachIndexed { index, waypoint ->
            val isSelectedForReorder = waypoint.uuid == reorderUuid
            val actionId = when {
                isSelectedForReorder -> ACTION_REORDER_CANCEL
                reorderUuid != null -> ACTION_REORDER_BEFORE
                else -> ACTION_OPEN_WAYPOINT
            }
            val action = when {
                isSelectedForReorder -> lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_WAYPOINT_REORDER_CANCEL_ACTION)
                reorderUuid != null -> lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_WAYPOINT_REORDER_INSERT_ACTION)
                else -> lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_WAYPOINT_OPEN_ACTION)
            }
            elements += actionEntry(
                layout.itemSlots[index], player, waypoint.icon, waypoint.name,
                buildList {
                    add(GuiLoreLine.Metadata("XYZ", "${waypoint.blockX}, ${waypoint.blockY}, ${waypoint.blockZ}"))
                    waypoint.description.forEach { add(GuiLoreLine.UserText(it)) }
                },
                action,
                actionId,
                payload = mapOf("waypoint" to waypoint.uuid.toString()),
            )
        }
        if (hasAddSlot) {
            elements += actionEntry(
                layout.itemSlots[tour.waypoints.size],
                player,
                if (reorderUuid == null) Material.YELLOW_STAINED_GLASS_PANE else Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_ADD_WAYPOINT_BUTTON), emptyList(),
                if (reorderUuid == null) lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_ADD_SIGN_ACTION)
                else lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_WAYPOINT_REORDER_END_ACTION),
                if (reorderUuid == null) ACTION_ADD_WAYPOINT else ACTION_REORDER_END,
            )
        }
        elements += CCSystem.getAPI().getGuiElementService().backEntry(
            player,
            layout.actionSlot,
            plugin.menuConfigManager.getIconMaterial("world_settings", "back", Material.REDSTONE),
        )
        elements += CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = layout.actionSlot - 2,
                material = Material.NAME_TAG,
                name = me.awabi2048.myworldmanager.util.fixedLabelName(lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_EDIT_TEXT_DISPLAY), GuiNameStyle.DEFAULT),
                role = GuiElementRole.ACTION,
                actions = listOf(
                    menuGestureAction(
                        ACTION_EDIT_TEXT,
                        MenuGesture.LEFT_RIGHT,
                        lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_EDIT_TEXT_ACTION_TEXT),
                        safety = MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE,
                    ),
                ),
            ),
        )
        elements += actionEntry(
            layout.actionSlot - 3,
            player,
            Material.ANVIL,
            lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_ICON_DISPLAY),
            emptyList(),
            lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_ICON_ACTION),
            ACTION_EDIT_ICON,
            gesture = MenuGesture.LEFT_RIGHT,
        )
        elements += actionEntry(
            layout.actionSlot + 2,
            player, Material.LAVA_BUCKET, lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_DELETE_DISPLAY),
            emptyList(), lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_DELETE_ACTION),
            ACTION_DELETE,
        )
        return InventoryMenuView(
            size = layout.size,
            title = GuiHelper.inventoryTitle(
                Component.text(lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_SINGLE_EDIT_TITLE, mapOf("tour" to tour.name))),
            ),
            elements = elements,
            playerInventoryInteraction =
                if (session.awaitingIconPick) {
                    PlayerInventoryInteraction.SELECTION
                } else {
                    PlayerInventoryInteraction.INTERACTIVE
                },
        )
    }

    fun openDeleteConfirm(player: Player, worldData: WorldData, tour: TourData, isNew: Boolean = false) {
        runtime.navigate(
            player,
            tourRoute(DELETE_CONFIRM_ROUTE, worldData.uuid, tour.uuid, isNew),
        )
    }

    private fun selectEditIcon(context: MenuActionContext): MenuActionResult {
        val session = plugin.tourSessionManager.getEdit(context.player.uniqueId)
            ?: return MenuActionResult.Ignored
        if (context.item.type.isAir) {
            return MenuActionResult.Ignored
        }
        if (context.item.type == Material.BLACK_STAINED_GLASS_PANE ||
            context.item.type == Material.GRAY_STAINED_GLASS_PANE
        ) {
            context.player.playSound(context.player.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_BIT, 1.0f, 0.5f)
            context.player.sendMessage(plugin.languageManager.getMessage(context.player, MyworldMessagesKeys.MESSAGES_ICON_FORBIDDEN))
            return MenuActionResult.Rejected()
        }
        val worldData = world(context.route) ?: return MenuActionResult.Rejected()
        val waypointUuid = session.awaitingWaypointIconPick
        if (waypointUuid != null) {
            if (!plugin.tourManager.updateWaypointIcon(session, waypointUuid, context.item.type)) {
                return MenuActionResult.Rejected()
            }
            session.awaitingWaypointIconPick = null
        } else if (session.awaitingIconPick) {
            session.draft.icon = context.item.type
            session.awaitingIconPick = false
        } else {
            return MenuActionResult.Ignored
        }
        plugin.tourManager.saveEditSession(context.player, worldData, closeSession = false)
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun renderWaypointEditMenu(player: Player, route: MenuRoute): InventoryMenuView {
        val worldData = world(route) ?: error("ツアー対象ワールドがありません")
        val session = plugin.tourSessionManager.getEdit(player.uniqueId)
            ?: error("ツアー編集セッションがありません")
        val waypointUuid = route.payload["waypoint"]?.let(UUID::fromString)
            ?: error("経由地点が指定されていません")
        val waypoint = plugin.tourManager.findDraftWaypoint(session, waypointUuid)
            ?: error("経由地点がありません")
        val lang = plugin.languageManager
        val layout = CCSystem.getAPI().getGuiLayoutService().free45()
        val middleRowStart = 18
        val elements = mutableListOf<MenuElement>()
        elements += actionEntry(
            middleRowStart + 1,
            player,
            Material.NAME_TAG,
            lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_WAYPOINT_NAME),
            listOf(GuiLoreLine.Metadata("現在値", waypoint.name)),
            lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_WAYPOINT_NAME_ACTION),
            ACTION_WAYPOINT_NAME,
        )
        elements += actionEntry(
            middleRowStart + 2,
            player,
            Material.WRITABLE_BOOK,
            lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_WAYPOINT_DESCRIPTION),
            waypoint.description.map { GuiLoreLine.UserText(it) },
            lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_WAYPOINT_DESCRIPTION_ACTION),
            ACTION_WAYPOINT_DESCRIPTION,
        )
        elements += actionEntry(
            middleRowStart + 3,
            player,
            waypoint.icon,
            lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_WAYPOINT_ICON),
            emptyList(),
            lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_WAYPOINT_ICON_ACTION),
            ACTION_WAYPOINT_ICON,
        )
        elements += actionEntry(
            middleRowStart + 4,
            player,
            Material.COMPASS,
            lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_WAYPOINT_POSITION),
            listOf(GuiLoreLine.Metadata("XYZ", "${waypoint.blockX}, ${waypoint.blockY}, ${waypoint.blockZ}")),
            lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_WAYPOINT_POSITION_ACTION),
            ACTION_WAYPOINT_POSITION,
        )
        elements += actionEntry(
            middleRowStart + 6,
            player,
            Material.LAVA_BUCKET,
            lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_WAYPOINT_DELETE),
            emptyList(),
            lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_WAYPOINT_DELETE_ACTION),
            ACTION_WAYPOINT_DELETE,
            GuiElementRole.ACTION,
        )
        elements += actionEntry(
            middleRowStart + 7,
            player,
            Material.HOPPER,
            lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_WAYPOINT_REORDER),
            emptyList(),
            lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_WAYPOINT_REORDER_ACTION),
            ACTION_WAYPOINT_REORDER,
        )
        elements += CCSystem.getAPI().getGuiElementService().backEntry(
            player,
            layout.backSlot,
            plugin.menuConfigManager.getIconMaterial("world_settings", "back", Material.REDSTONE),
        )
        return InventoryMenuView(
            size = layout.size,
            title = GuiHelper.inventoryTitle(
                Component.text(
                    lang.getMessage(
                        player,
                        MyworldGuiCommonKeys.GUI_TOUR_MENU_WAYPOINT_EDIT_TITLE,
                        mapOf("waypoint" to waypoint.name),
                    ),
                ),
            ),
            elements = elements,
            playerInventoryInteraction =
                if (session.awaitingWaypointIconPick == waypoint.uuid) {
                    PlayerInventoryInteraction.SELECTION
                } else {
                    PlayerInventoryInteraction.INTERACTIVE
                },
        )
    }

    private fun skipTourWaypoint(context: MenuActionContext): MenuActionResult {
        val worldData = world(context.route) ?: return MenuActionResult.Rejected()
        if (!plugin.tourManager.skipCurrentWaypoint(context.player, worldData)) {
            return MenuActionResult.Rejected()
        }
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun openVisitorFromEdit(context: MenuActionContext): MenuActionResult {
        val worldData = world(context.route) ?: return MenuActionResult.Rejected()
        return MenuActionResult.Success(
            // 訪問者表示と編集用表示は同じツアー画面の表示状態です。切り替えで
            // 履歴を増やすと戻る操作が同じ画面を何度も経由するため、現在の履歴を置換します。
            MenuUpdate.Replace(
                MenuRoute(
                    OWNER,
                    VISITOR_ROUTE,
                    mapOf("world" to worldData.uuid.toString(), "page" to "0"),
                ),
            ),
        )
    }

    private fun openWaypoint(context: MenuActionContext): MenuActionResult {
        val worldData = world(context.route) ?: return MenuActionResult.Rejected()
        val session = plugin.tourSessionManager.getEdit(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        val waypointUuid = context.payload["waypoint"]?.let(UUID::fromString)
            ?: return MenuActionResult.Rejected()
        if (plugin.tourManager.findDraftWaypoint(session, waypointUuid) == null) {
            return MenuActionResult.Rejected()
        }
        return MenuActionResult.Success(
            MenuUpdate.Navigate(
                waypointRoute(
                    WAYPOINT_EDIT_ROUTE,
                    worldData.uuid,
                    session.draft.uuid,
                    waypointUuid,
                    context.route.payload["is_new"].toBoolean(),
                ),
            ),
        )
    }

    private fun editWaypointName(context: MenuActionContext): MenuActionResult {
        val worldData = world(context.route) ?: return MenuActionResult.Rejected()
        val session = plugin.tourSessionManager.getEdit(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        val waypointUuid = context.route.payload["waypoint"]?.let(UUID::fromString)
            ?: return MenuActionResult.Rejected()
        val waypoint = plugin.tourManager.findDraftWaypoint(session, waypointUuid)
            ?: return MenuActionResult.Rejected()
        TourDialogManager.startWaypointNameEdit(
            context.player,
            plugin,
            worldData.uuid,
            session.draft.uuid,
            waypoint.uuid,
            waypoint.name,
        )
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun editWaypointDescription(context: MenuActionContext): MenuActionResult {
        val worldData = world(context.route) ?: return MenuActionResult.Rejected()
        val session = plugin.tourSessionManager.getEdit(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        val waypointUuid = context.route.payload["waypoint"]?.let(UUID::fromString)
            ?: return MenuActionResult.Rejected()
        val waypoint = plugin.tourManager.findDraftWaypoint(session, waypointUuid)
            ?: return MenuActionResult.Rejected()
        TourDialogManager.startWaypointDescriptionEdit(
            context.player,
            plugin,
            worldData.uuid,
            session.draft.uuid,
            waypoint.uuid,
            waypoint.description,
        )
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun editWaypointIcon(context: MenuActionContext): MenuActionResult {
        val session = plugin.tourSessionManager.getEdit(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        val waypointUuid = context.route.payload["waypoint"]?.let(UUID::fromString)
            ?: return MenuActionResult.Rejected()
        if (plugin.tourManager.findDraftWaypoint(session, waypointUuid) == null) {
            return MenuActionResult.Rejected()
        }
        session.awaitingWaypointIconPick = waypointUuid
        context.player.sendMessage(plugin.languageManager.getMessage(context.player, MyworldMessagesKeys.MESSAGES_ICON_PROMPT))
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun editWaypointPosition(context: MenuActionContext): MenuActionResult {
        val session = plugin.tourSessionManager.getEdit(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        val waypointUuid = context.route.payload["waypoint"]?.let(UUID::fromString)
            ?: return MenuActionResult.Rejected()
        if (plugin.tourManager.findDraftWaypoint(session, waypointUuid) == null) {
            return MenuActionResult.Rejected()
        }
        plugin.tourListener.beginWaypointPick(context.player, waypointUuid)
        runtime.suspendForExternal(context.player)
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun openWaypointDelete(context: MenuActionContext): MenuActionResult {
        val worldData = world(context.route) ?: return MenuActionResult.Rejected()
        val session = plugin.tourSessionManager.getEdit(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        val waypointUuid = context.route.payload["waypoint"]?.let(UUID::fromString)
            ?: return MenuActionResult.Rejected()
        return MenuActionResult.Success(
            MenuUpdate.Replace(
                waypointRoute(
                    WAYPOINT_DELETE_CONFIRM_ROUTE,
                    worldData.uuid,
                    session.draft.uuid,
                    waypointUuid,
                    context.route.payload["is_new"].toBoolean(),
                ),
            ),
        )
    }

    private fun startWaypointReorder(context: MenuActionContext): MenuActionResult {
        val session = plugin.tourSessionManager.getEdit(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        val waypointUuid = context.route.payload["waypoint"]?.let(UUID::fromString)
            ?: return MenuActionResult.Rejected()
        if (plugin.tourManager.findDraftWaypoint(session, waypointUuid) == null) {
            return MenuActionResult.Rejected()
        }
        session.reorderingWaypointUuid = waypointUuid
        return MenuActionResult.Success(MenuUpdate.Back)
    }

    private fun cancelWaypointReorder(context: MenuActionContext): MenuActionResult {
        val worldData = world(context.route) ?: return MenuActionResult.Rejected()
        val session = plugin.tourSessionManager.getEdit(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        session.reorderingWaypointUuid = null
        return MenuActionResult.Success(
            MenuUpdate.Navigate(
                waypointRoute(
                    WAYPOINT_EDIT_ROUTE,
                    worldData.uuid,
                    session.draft.uuid,
                    context.payload["waypoint"]?.let(UUID::fromString) ?: return MenuActionResult.Rejected(),
                    context.route.payload["is_new"].toBoolean(),
                ),
            ),
        )
    }

    private fun insertWaypointBefore(context: MenuActionContext): MenuActionResult {
        val worldData = world(context.route) ?: return MenuActionResult.Rejected()
        val session = plugin.tourSessionManager.getEdit(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        val selectedUuid = session.reorderingWaypointUuid ?: return MenuActionResult.Rejected()
        val targetUuid = context.payload["waypoint"]?.let(UUID::fromString)
            ?: return MenuActionResult.Rejected()
        if (!plugin.tourManager.moveWaypointBefore(session, selectedUuid, targetUuid)) {
            return MenuActionResult.Rejected()
        }
        session.reorderingWaypointUuid = null
        plugin.tourManager.saveEditSession(context.player, worldData, closeSession = false)
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun insertWaypointAtEnd(context: MenuActionContext): MenuActionResult {
        val worldData = world(context.route) ?: return MenuActionResult.Rejected()
        val session = plugin.tourSessionManager.getEdit(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        val selectedUuid = session.reorderingWaypointUuid ?: return MenuActionResult.Rejected()
        plugin.tourManager.moveWaypointToEnd(session, selectedUuid)
        session.reorderingWaypointUuid = null
        plugin.tourManager.saveEditSession(context.player, worldData, closeSession = false)
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun renderWaypointDeleteConfirm(player: Player, route: MenuRoute): InventoryMenuView {
        val lang = plugin.languageManager
        val layout = GuiHelper.confirmationLayout()
        return InventoryMenuView(
            size = layout.size,
            title = GuiHelper.inventoryTitle(
                Component.text(lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_WAYPOINT_DELETE_CONFIRM_TITLE)),
            ),
            elements = listOf(
                displayEntry(
                    layout.previewSlot,
                    Material.LAVA_BUCKET,
                    lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_WAYPOINT_DELETE_CONFIRM_TITLE),
                    listOf(GuiLoreLine.Warning(lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_WAYPOINT_DELETE_CONFIRM_BODY))),
                ),
                simpleActionEntry(
                    player,
                    layout.confirmSlot,
                    Material.LIME_WOOL,
                    MyworldGuiCommonKeys.GUI_TOUR_MENU_WAYPOINT_DELETE_CONFIRM_CONFIRM,
                    GuiElementRole.CONFIRM,
                    ACTION_WAYPOINT_DELETE_CONFIRM,
                ),
                simpleActionEntry(
                    player,
                    layout.cancelSlot,
                    Material.RED_WOOL,
                    MyworldGuiCommonKeys.GUI_TOUR_MENU_WAYPOINT_DELETE_CONFIRM_CANCEL,
                    GuiElementRole.CANCEL,
                    ACTION_WAYPOINT_DELETE_CANCEL,
                ),
            ),
            category = MenuViewCategory.CONFIRMATION,
        )
    }

    private fun confirmWaypointDelete(context: MenuActionContext): MenuActionResult {
        val worldData = world(context.route) ?: return MenuActionResult.Rejected()
        val session = plugin.tourSessionManager.getEdit(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        val waypointUuid = context.route.payload["waypoint"]?.let(UUID::fromString)
            ?: return MenuActionResult.Rejected()
        if (!plugin.tourManager.deleteWaypoint(session, waypointUuid)) {
            return MenuActionResult.Rejected()
        }
        plugin.tourManager.saveEditSession(context.player, worldData, closeSession = false)
        return MenuActionResult.Success(
            MenuUpdate.Replace(
                tourRoute(
                    SINGLE_EDIT_ROUTE,
                    worldData.uuid,
                    session.draft.uuid,
                    context.route.payload["is_new"].toBoolean(),
                ),
            ),
        )
    }

    private fun renderDeleteConfirm(player: Player, route: MenuRoute): InventoryMenuView {
        val lang = plugin.languageManager
        val layout = GuiHelper.confirmationLayout()
        return InventoryMenuView(
            size = layout.size,
            title = GuiHelper.inventoryTitle(
                Component.text(lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_DELETE_CONFIRM_TITLE)),
            ),
            elements = listOf(
                displayEntry(
                    layout.previewSlot,
                    Material.LAVA_BUCKET,
                    lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_DELETE_CONFIRM_TITLE),
                    listOf(
                        GuiLoreLine.Text(lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_DELETE_CONFIRM_BODY_LINE1)),
                        GuiLoreLine.Text(lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_DELETE_CONFIRM_BODY_LINE2)),
                        GuiLoreLine.Warning(lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_DELETE_CONFIRM_WARNING)),
                    ),
                ),
                simpleActionEntry(player, layout.confirmSlot, Material.LIME_WOOL, MyworldGuiCommonKeys.GUI_TOUR_MENU_DELETE_CONFIRM_CONFIRM, GuiElementRole.CONFIRM, ACTION_DELETE_CONFIRM),
                simpleActionEntry(player, layout.cancelSlot, Material.RED_WOOL, MyworldGuiCommonKeys.GUI_TOUR_MENU_DELETE_CONFIRM_CANCEL, GuiElementRole.CANCEL, ACTION_DELETE_CANCEL),
            ),
            category = MenuViewCategory.CONFIRMATION,
        )
    }

    private fun startTour(context: MenuActionContext): MenuActionResult {
        val worldData = world(context.route) ?: return MenuActionResult.Rejected()
        val tour = tour(worldData, context.route) ?: return MenuActionResult.Rejected()
        return when (plugin.tourManager.startTour(context.player, worldData, tour)) {
            me.awabi2048.myworldmanager.service.TourManager.StartTourResult.STARTED ->
                MenuActionResult.Success(MenuUpdate.Close)
            me.awabi2048.myworldmanager.service.TourManager.StartTourResult.INVALID_TOUR -> {
                context.player.sendMessage(plugin.languageManager.getMessage(context.player, MyworldMessagesKeys.MESSAGES_TOUR_NONE_AVAILABLE))
                MenuActionResult.Rejected()
            }
            me.awabi2048.myworldmanager.service.TourManager.StartTourResult.WRONG_WORLD -> {
                context.player.sendMessage(plugin.languageManager.getMessage(context.player, MyworldMessagesKeys.MESSAGES_NO_IN_MYWORLD))
                MenuActionResult.Rejected()
            }
        }
    }

    private fun changeTourPage(context: MenuActionContext): MenuActionResult {
        val page = context.payload["page"]?.toIntOrNull() ?: return MenuActionResult.Rejected()
        return MenuActionResult.Success(
            MenuUpdate.Replace(
                context.route.copy(
                    payload = context.route.payload + ("page" to page.coerceAtLeast(0).toString()),
                ),
            ),
        )
    }

    private fun selectTour(context: MenuActionContext): MenuActionResult {
        val worldUuid = context.route.payload["world"] ?: return MenuActionResult.Rejected()
        val tourUuid = context.payload["tour"] ?: return MenuActionResult.Rejected()
        return MenuActionResult.Success(
            MenuUpdate.Navigate(
                MenuRoute(
                    OWNER,
                    START_CONFIRM_ROUTE,
                    mapOf("world" to worldUuid, "tour" to tourUuid),
                ),
            ),
        )
    }

    private fun createTour(context: MenuActionContext): MenuActionResult {
        val worldData = world(context.route) ?: return MenuActionResult.Rejected()
        TourDialogManager.startTourCreation(context.player, plugin, worldData.uuid)
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun editTour(context: MenuActionContext): MenuActionResult {
        val worldData = world(context.route) ?: return MenuActionResult.Rejected()
        val tourUuid = context.payload["tour"]?.let(UUID::fromString)
            ?: return MenuActionResult.Rejected()
        val tour = plugin.tourManager.getTour(worldData, tourUuid)
            ?: return MenuActionResult.Rejected()
        val session = plugin.tourManager.openEditSession(context.player, worldData, tour)
        return MenuActionResult.Success(
            MenuUpdate.Navigate(tourRoute(SINGLE_EDIT_ROUTE, worldData.uuid, session.draft.uuid, false)),
        )
    }

    private fun singleBack(context: MenuActionContext): MenuActionResult {
        val worldData = world(context.route) ?: return MenuActionResult.Rejected()
        val session = plugin.tourSessionManager.getEdit(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        if (context.route.payload["is_new"].toBoolean() && session.isNew) {
            return MenuActionResult.Success(
                MenuUpdate.Replace(
                    tourRoute(DISCARD_CONFIRM_ROUTE, worldData.uuid, session.draft.uuid, true),
                ),
            )
        }
        plugin.tourManager.saveEditSession(context.player, worldData)
        return MenuActionResult.Success(MenuUpdate.Back)
    }

    private fun editText(context: MenuActionContext): MenuActionResult {
        val worldData = world(context.route) ?: return MenuActionResult.Rejected()
        val session = plugin.tourSessionManager.getEdit(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        TourDialogManager.startTourTextEdit(
            context.player,
            plugin,
            worldData.uuid,
            session.draft.uuid,
            session.draft.name,
            session.draft.description,
        )
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun editIcon(context: MenuActionContext): MenuActionResult {
        val session = plugin.tourSessionManager.getEdit(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        session.awaitingIconPick = true
        context.player.sendMessage(plugin.languageManager.getMessage(context.player, MyworldMessagesKeys.MESSAGES_ICON_PROMPT))
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun openDelete(context: MenuActionContext): MenuActionResult {
        val worldData = world(context.route) ?: return MenuActionResult.Rejected()
        val session = plugin.tourSessionManager.getEdit(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        return MenuActionResult.Success(
            MenuUpdate.Replace(
                tourRoute(
                    DELETE_CONFIRM_ROUTE,
                    worldData.uuid,
                    session.draft.uuid,
                    context.route.payload["is_new"].toBoolean(),
                ),
            ),
        )
    }

    private fun addWaypoint(context: MenuActionContext): MenuActionResult {
        plugin.tourListener.beginWaypointPick(context.player)
        runtime.suspendForExternal(context.player)
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun renderDiscardConfirm(player: Player, route: MenuRoute): InventoryMenuView {
        world(route) ?: error("ツアー対象ワールドがありません")
        val lang = plugin.languageManager
        val layout = GuiHelper.confirmationLayout()
        return InventoryMenuView(
            size = layout.size,
            title = GuiHelper.inventoryTitle(
                Component.text(lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_DISCARD_NEW_TITLE)),
            ),
            elements = listOf(
                displayEntry(
                    layout.previewSlot,
                    Material.PAPER,
                    lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_DISCARD_NEW_TITLE),
                    listOf(
                        GuiLoreLine.Text(lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_DISCARD_NEW_BODY_LINE1)),
                        GuiLoreLine.Text(lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_DISCARD_NEW_BODY_LINE2)),
                    ),
                ),
                simpleActionEntry(player, layout.confirmSlot, Material.LIME_WOOL, CommonKeys.GUI_COMMON_CONFIRM, GuiElementRole.CONFIRM, ACTION_DISCARD_CONFIRM),
                simpleActionEntry(player, layout.cancelSlot, Material.RED_WOOL, CommonKeys.GUI_COMMON_CANCEL, GuiElementRole.CANCEL, ACTION_DISCARD_CANCEL),
            ),
            category = MenuViewCategory.CONFIRMATION,
        )
    }

    private fun confirmDiscard(context: MenuActionContext): MenuActionResult {
        world(context.route) ?: return MenuActionResult.Rejected()
        plugin.tourSessionManager.clearEdit(context.player.uniqueId)
        return MenuActionResult.Success(MenuUpdate.Back)
    }

    private fun cancelDiscard(context: MenuActionContext): MenuActionResult {
        val worldData = world(context.route) ?: return MenuActionResult.Rejected()
        val session = plugin.tourSessionManager.getEdit(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        return MenuActionResult.Success(
            MenuUpdate.Replace(
                tourRoute(SINGLE_EDIT_ROUTE, worldData.uuid, session.draft.uuid, true),
            ),
        )
    }

    private fun bindSign(context: MenuActionContext): MenuActionResult {
        val worldData = world(context.route) ?: return MenuActionResult.Rejected()
        val tourUuid = context.payload["tour"]?.let(UUID::fromString)
            ?: return MenuActionResult.Rejected()
        val tour = plugin.tourManager.getTour(worldData, tourUuid)
            ?: return MenuActionResult.Rejected()
        if (!plugin.tourManager.canManage(worldData, context.player.uniqueId) ||
            tour.startSignUuid != null ||
            !plugin.tourManager.canPlaceSign(worldData)
        ) {
            TourDialogManager.consumePlacement(context.player.uniqueId)
            return MenuActionResult.Rejected()
        }
        val placement = TourDialogManager.consumePlacement(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        if (placement.worldUuid != worldData.uuid) {
            return MenuActionResult.Rejected()
        }
        val world = Bukkit.getWorld(plugin.worldService.getWorldFolderName(worldData))
            ?: return MenuActionResult.Rejected()
        val signData = plugin.tourManager.registerExistingTourSign(
            worldData,
            context.player,
            world.getBlockAt(placement.x, placement.y, placement.z),
        ) ?: return MenuActionResult.Rejected()
        tour.startSignUuid = signData.uuid
        plugin.worldConfigRepository.save(worldData)
        context.player.sendMessage(
            plugin.languageManager.getMessage(context.player, MyworldMessagesKeys.MESSAGES_TOUR_SIGN_BOUND),
        )
        return MenuActionResult.Success(MenuUpdate.Close)
    }

    private fun cancelBindSign(context: MenuActionContext): MenuActionResult {
        TourDialogManager.consumePlacement(context.player.uniqueId)
        return MenuActionResult.Success(MenuUpdate.Close)
    }

    private fun confirmDelete(context: MenuActionContext): MenuActionResult {
        val worldData = world(context.route) ?: return MenuActionResult.Rejected()
        val session = plugin.tourSessionManager.getEdit(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        val isNew = context.route.payload["is_new"].toBoolean()
        if (!isNew) {
            val tourUuid = session.originalTourUuid
                ?: context.route.payload["tour"]?.let(UUID::fromString)
                ?: return MenuActionResult.Rejected()
            plugin.tourManager.deleteTour(worldData, tourUuid)
        }
        plugin.tourSessionManager.clearEdit(context.player.uniqueId)
        return MenuActionResult.Success(MenuUpdate.Back)
    }

    private fun cancelDelete(context: MenuActionContext): MenuActionResult {
        val worldData = world(context.route) ?: return MenuActionResult.Rejected()
        val session = plugin.tourSessionManager.getEdit(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        return MenuActionResult.Success(
            MenuUpdate.Replace(
                tourRoute(
                    SINGLE_EDIT_ROUTE,
                    worldData.uuid,
                    session.draft.uuid,
                    context.route.payload["is_new"].toBoolean(),
                ),
            ),
        )
    }

    private fun world(route: MenuRoute): WorldData? =
        route.payload["world"]?.let(UUID::fromString)?.let(plugin.worldConfigRepository::findByUuid)

    private fun tour(worldData: WorldData, route: MenuRoute): TourData? =
        route.payload["tour"]?.let(UUID::fromString)?.let { plugin.tourManager.getTour(worldData, it) }

    private fun tourRoute(
        id: String,
        worldUuid: UUID,
        tourUuid: UUID,
        isNew: Boolean? = null,
    ): MenuRoute = MenuRoute(
        OWNER,
        id,
        buildMap {
            put("world", worldUuid.toString())
            put("tour", tourUuid.toString())
            if (isNew != null) put("is_new", isNew.toString())
        },
    )

    private fun waypointRoute(
        id: String,
        worldUuid: UUID,
        tourUuid: UUID,
        waypointUuid: UUID,
        isNew: Boolean = false,
    ): MenuRoute = MenuRoute(
        OWNER,
        id,
        buildMap {
            put("world", worldUuid.toString())
            put("tour", tourUuid.toString())
            put("waypoint", waypointUuid.toString())
            put("is_new", isNew.toString())
        },
    )

    private fun displayEntry(
        slot: Int,
        material: Material,
        name: String,
        lore: List<GuiLoreLine>,
        role: GuiElementRole = GuiElementRole.CONTENT,
        frame: GuiLoreFrame = GuiLoreFrame.TOP,
    ): MenuElement = CCSystem.getAPI().getGuiElementService().menuDisplay(
        GuiMenuDisplaySpec(
            slot,
            GuiItemSpec(
                material,
                me.awabi2048.myworldmanager.util.fixedLabelName(name, GuiNameStyle.DEFAULT),
                if (lore.isEmpty()) GuiLoreSpec.None else me.awabi2048.myworldmanager.util.semanticLore(lore, frame),
                role,
                1,
            ),
        ),
    )

    private fun simpleActionEntry(
        player: Player,
        slot: Int,
        material: Material,
        nameKey: LocalizationKey<String>,
        role: GuiElementRole,
        actionId: String,
    ): MenuElement = actionEntry(
        slot,
        player,
        material,
        plugin.languageManager.getMessage(player, nameKey),
        emptyList(),
        plugin.languageManager.getMessage(player, nameKey),
        actionId,
        role,
    )

    private fun navigationEntry(player: Player, slot: Int, next: Boolean, targetPage: Int): MenuElement {
        val key = if (next) CommonKeys.GUI_COMMON_NEXT_PAGE else CommonKeys.GUI_COMMON_PREV_PAGE
        val iconId = if (next) "next_page" else "prev_page"
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = plugin.menuConfigManager.getIconMaterial("tour", iconId, Material.ARROW),
                name = GuiNameSpec.FixedLabel(plugin.languageManager.getComponent(player, key)),
                role = GuiElementRole.NAVIGATION,
                actions = listOf(
                    menuGestureAction(
                        ACTION_PAGE,
                        MenuGesture.LEFT_RIGHT,
                        plugin.languageManager.getMessage(player, key),
                        mapOf("page" to targetPage.toString()),
                        safety = MenuActionSafety.NAVIGATION_ONLY,
                    ),
                ),
                interactionGuidance = GuiInteractionGuidance.SINGLE_ACTION_CLICK,
            ),
        )
    }

    private fun createCurrentWorldEntry(player: Player, worldData: WorldData, slot: Int): MenuElement {
        val lang = plugin.languageManager
        val owner = Bukkit.getOfflinePlayer(worldData.owner)
        val ownerName = owner.name ?: lang.getMessage(player, CommonKeys.GENERAL_UNKNOWN)
        val lore = GuiLoreSpec.Blocks(buildList {
            if (worldData.description.isNotBlank()) {
                add(GuiLoreBlock(listOf(GuiLoreLine.UserText(worldData.description))))
            }
            add(GuiLoreBlock(listOf(
                GuiLoreLine.Data(lang.getMessage(player, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_WORLD_NAME), worldData.name, "§a"),
                GuiLoreLine.Data(lang.getMessage(player, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_OWNER), ownerName, "§b"),
            )))
        })
        return CCSystem.getAPI().getGuiElementService().menuDisplay(
            GuiMenuDisplaySpec(
                slot,
                GuiItemSpec(
                    worldData.icon,
                    GuiNameSpec.FixedLabel(lang.getComponent(player, MyworldGuiFavoriteKeys.GUI_FAVORITE_CURRENT_WORLD_NAME)),
                    lore,
                    GuiElementRole.CONTENT,
                    1,
                ),
            ),
        )
    }

    private fun createCurrentTourEntry(player: Player, tour: TourData, slot: Int): MenuElement {
        val lore = if (tour.description.isBlank()) {
            GuiLoreSpec.None
        } else {
            GuiLoreSpec.Blocks(listOf(GuiLoreBlock(listOf(GuiLoreLine.UserText(tour.description)))))
        }
        return CCSystem.getAPI().getGuiElementService().menuDisplay(
            GuiMenuDisplaySpec(
                slot,
                GuiItemSpec(
                    tour.icon,
                    GuiNameSpec.FixedLabel(Component.text(tour.name)),
                    lore,
                    GuiElementRole.CONTENT,
                    1,
                ),
            ),
        )
    }

    private fun createTourEntry(slot: Int, player: Player, worldData: WorldData, tour: TourData, editing: Boolean, actionId: String): MenuElement {
        val lang = plugin.languageManager
        val current = plugin.tourSessionManager.get(player.uniqueId)?.let { it.tourUuid == tour.uuid && it.worldUuid == worldData.uuid } == true
        val countValue = if (tour.completedCount == 0) {
            lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_TOUR_ITEM_VISITORS_NONE)
        } else {
            lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_TOUR_ITEM_VISITORS_COUNT, mapOf("count" to tour.completedCount.toString()))
        }
        val action = when {
            editing -> lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_TOUR_ITEM_ACTION_EDIT)
            current -> lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_TOUR_ITEM_ACTION_CURRENT)
            else -> lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_TOUR_ITEM_ACTION_START)
        }
        return actionEntry(slot, player, tour.icon, tour.name, buildList {
            if (tour.description.isNotBlank()) add(GuiLoreLine.UserText(tour.description))
            add(GuiLoreLine.Data(lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_TOUR_ITEM_VISITORS_LABEL), countValue, "§a"))
        }, action, actionId, payload = mapOf("tour" to tour.uuid.toString()))
    }

    private fun actionEntry(
        slot: Int,
        player: Player,
        material: Material,
        name: String,
        information: List<GuiLoreLine>,
        action: String,
        actionId: String,
        role: GuiElementRole = GuiElementRole.ACTION,
        payload: Map<String, String> = emptyMap(),
        gesture: MenuGesture = MenuGesture.LEFT_RIGHT,
    ): MenuElement = CCSystem.getAPI().getGuiElementService().menuEntry(
        player,
        GuiMenuEntrySpec(
            slot = slot,
            material = material,
            name = me.awabi2048.myworldmanager.util.fixedLabelName(name, GuiNameStyle.DEFAULT),
            role = role,
            description = information.mapNotNull {
                when (it) {
                    is GuiLoreLine.Text -> it.text
                    is GuiLoreLine.UserText -> it.text
                    else -> null
                }
            },
            data = information.mapNotNull {
                when (it) {
                    is GuiLoreLine.Data -> GuiMenuEntryData(it.label, it.value, toneFor(it.valueColor))
                    is GuiLoreLine.Metadata -> GuiMenuEntryData(it.label, it.value)
                    else -> null
                }
            },
                actions = listOf(menuGestureAction(
                actionId, gesture, action, payload,
                safety = tourActionSafety(actionId),
                reversibleContract = when (actionId) {
                    ACTION_DISCARD_CONFIRM -> MwmMenuActionSemantics.contract("tour-discard")
                    else -> null
                },
            )),
            interactionGuidance = GuiInteractionGuidance.SINGLE_ACTION_CLICK,
        ),
    )

    /** 画面切り替えのように、操作説明をLoreへ追加せずNameだけを表示する操作です。 */
    private fun nameOnlyActionEntry(
        slot: Int,
        player: Player,
        material: Material,
        name: String,
        action: String,
        actionId: String,
        role: GuiElementRole = GuiElementRole.ACTION,
        payload: Map<String, String> = emptyMap(),
        gesture: MenuGesture = MenuGesture.LEFT_RIGHT,
    ): MenuElement = CCSystem.getAPI().getGuiElementService().menuStructuredEntry(
        player,
        GuiStructuredMenuEntrySpec(
            slot = slot,
            item = GuiItemSpec(
                material = material,
                name = me.awabi2048.myworldmanager.util.fixedLabelName(name, GuiNameStyle.DEFAULT),
                lore = GuiLoreSpec.NameOnly,
                role = role,
                amount = 1,
            ),
            actions = listOf(
                menuGestureAction(
                    actionId,
                    gesture,
                    action,
                    payload,
                    safety = tourActionSafety(actionId),
                ),
            ),
        ),
    )

    private fun tourActionSafety(actionId: String): MenuActionSafety = when (actionId) {
        ACTION_START,
        ACTION_STOP_CONFIRM -> MenuActionSafety.EXTERNAL_SIDE_EFFECT
        ACTION_START_CANCEL,
        ACTION_STOP_CANCEL,
        ACTION_PAGE,
        ACTION_EDIT,
        ACTION_OPEN_WAYPOINT,
        ACTION_OPEN_VISITOR,
        ACTION_REORDER_CANCEL,
        ACTION_DISCARD_CANCEL,
        ACTION_BIND_CANCEL,
        ACTION_DELETE_CANCEL,
        ACTION_WAYPOINT_DELETE_CANCEL -> MenuActionSafety.NAVIGATION_ONLY
        ACTION_STOP,
        ACTION_SELECT,
        ACTION_DELETE,
        ACTION_WAYPOINT_DELETE -> MenuActionSafety.CONFIRM_ENTRY
        ACTION_CREATE,
        ACTION_ADD_WAYPOINT,
        ACTION_REORDER_BEFORE,
        ACTION_REORDER_END,
        ACTION_EDIT_ICON,
        ACTION_WAYPOINT_NAME,
        ACTION_WAYPOINT_DESCRIPTION,
        ACTION_WAYPOINT_ICON,
        ACTION_WAYPOINT_POSITION,
        ACTION_WAYPOINT_REORDER -> MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE
        ACTION_BIND_SIGN,
        ACTION_DELETE_CONFIRM,
        ACTION_WAYPOINT_DELETE_CONFIRM -> MenuActionSafety.IRREVERSIBLE
        ACTION_DISCARD_CONFIRM,
        ACTION_SKIP -> MenuActionSafety.EXTERNAL_SIDE_EFFECT
        else -> error("Unknown tour action safety: $actionId")
    }

    private fun toneFor(colorCode: String): GuiValueTone =
        GuiValueTone.entries.firstOrNull { it.colorCode == colorCode } ?: GuiValueTone.DEFAULT

    private fun framedLore(lines: List<GuiLoreLine>): GuiLoreSpec {
        if (lines.isEmpty()) return GuiLoreSpec.None
        return me.awabi2048.myworldmanager.util.semanticLore(lines, GuiLoreFrame.BOTH)
    }

    fun openBindSignToTourMenu(player: Player, worldData: WorldData) {
        runtime.open(
            player,
            MenuRoute(OWNER, BIND_SIGN_ROUTE, mapOf("world" to worldData.uuid.toString())),
        )
    }

    private fun renderBindSignMenu(player: Player, route: MenuRoute): InventoryMenuView {
        val worldData = world(route) ?: error("ツアー対象ワールドがありません")
        val lang = plugin.languageManager
        val unboundTours = worldData.tours.filter { it.startSignUuid == null }.sortedBy { it.createdAt }
        val page = CCSystem.getAPI().getGuiLayoutService().sevenColumnPage(
            unboundTours.size,
            route.payload["page"]?.toIntOrNull() ?: 0,
        )
        val layout = page.layout
        val elements = mutableListOf<MenuElement>()
        unboundTours.drop(page.startIndex).take(page.itemCount).forEachIndexed { index, tour ->
            elements += actionEntry(
                layout.itemSlots[index],
                player,
                tour.icon,
                tour.name,
                if (tour.description.isBlank()) emptyList() else listOf(GuiLoreLine.UserText(tour.description)),
                lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_MENU_TOUR_ITEM_ACTION_BIND),
                ACTION_BIND_SIGN,
                payload = mapOf("tour" to tour.uuid.toString()),
            )
        }
        if (page.page > 0) {
            elements += navigationEntry(player, layout.previousPageSlot, false, page.page - 1)
        }
        if (page.page + 1 < page.totalPages) {
            elements += navigationEntry(player, layout.nextPageSlot, true, page.page + 1)
        }
        elements += simpleActionEntry(player, layout.backSlot, Material.RED_WOOL, CommonKeys.GUI_COMMON_CANCEL, GuiElementRole.CANCEL, ACTION_BIND_CANCEL)
        return InventoryMenuView(
            size = layout.size,
            title = GuiHelper.inventoryTitle(Component.text(lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_BIND_SIGN_TITLE))),
            elements = elements,
        )
    }

    private companion object {
        private const val OWNER = "myworldmanager"
        private const val START_CONFIRM_ROUTE = "tour_start_confirmation"
        private const val STOP_CONFIRM_ROUTE = "tour_stop_confirmation"
        private const val DELETE_CONFIRM_ROUTE = "tour_delete_confirmation"
        private const val VISITOR_ROUTE = "tour_visitor"
        private const val WAYPOINT_EDIT_ROUTE = "tour_waypoint_edit"
        private const val WAYPOINT_DELETE_CONFIRM_ROUTE = "tour_waypoint_delete_confirmation"
        private const val START_SELECTION_ROUTE = "tour_start_selection"
        private const val EDIT_ROUTE = "tour_edit"
        private const val SINGLE_EDIT_ROUTE = "tour_single_edit"
        private const val DISCARD_CONFIRM_ROUTE = "tour_discard_confirmation"
        private const val BIND_SIGN_ROUTE = "tour_bind_sign"
        private const val ACTION_START = "start"
        private const val ACTION_START_CANCEL = "start_cancel"
        private const val ACTION_PAGE = "page"
        private const val ACTION_SELECT = "select"
        private const val ACTION_STOP = "stop"
        private const val ACTION_STOP_CONFIRM = "stop_confirm"
        private const val ACTION_STOP_CANCEL = "stop_cancel"
        private const val ACTION_BACK = "back"
        private const val ACTION_CREATE = "create"
        private const val ACTION_EDIT = "edit"
        private const val ACTION_OPEN_VISITOR = "open_visitor"
        private const val ACTION_SINGLE_BACK = "single_back"
        private const val ACTION_EDIT_TEXT = "edit_text"
        private const val ACTION_EDIT_ICON = "edit_icon"
        private const val ACTION_DELETE = "delete"
        private const val ACTION_ADD_WAYPOINT = "add_waypoint"
        private const val ACTION_OPEN_WAYPOINT = "open_waypoint"
        private const val ACTION_REORDER_CANCEL = "reorder_cancel"
        private const val ACTION_REORDER_BEFORE = "reorder_before"
        private const val ACTION_REORDER_END = "reorder_end"
        private const val ACTION_SKIP = "skip"
        private const val ACTION_WAYPOINT_NAME = "waypoint_name"
        private const val ACTION_WAYPOINT_DESCRIPTION = "waypoint_description"
        private const val ACTION_WAYPOINT_ICON = "waypoint_icon"
        private const val ACTION_WAYPOINT_POSITION = "waypoint_position"
        private const val ACTION_WAYPOINT_DELETE = "waypoint_delete"
        private const val ACTION_WAYPOINT_REORDER = "waypoint_reorder"
        private const val ACTION_WAYPOINT_DELETE_CONFIRM = "waypoint_delete_confirm"
        private const val ACTION_WAYPOINT_DELETE_CANCEL = "waypoint_delete_cancel"
        private const val ACTION_DISCARD_CONFIRM = "discard_confirm"
        private const val ACTION_DISCARD_CANCEL = "discard_cancel"
        private const val ACTION_BIND_SIGN = "bind_sign"
        private const val ACTION_BIND_CANCEL = "bind_cancel"
        private const val ACTION_DELETE_CONFIRM = "delete_confirm"
        private const val ACTION_DELETE_CANCEL = "delete_cancel"
    }
}
