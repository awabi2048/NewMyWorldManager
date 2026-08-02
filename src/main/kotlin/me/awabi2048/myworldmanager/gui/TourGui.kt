package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.util.descriptionLine
import me.awabi2048.myworldmanager.util.warningLine
import me.awabi2048.myworldmanager.util.dangerLine

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuDisplaySpec
import com.awabi2048.ccsystem.api.gui.GuiMenuActionIntent
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import com.awabi2048.ccsystem.api.gui.GuiValueTone
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuActionSafety
import com.awabi2048.ccsystem.api.gui.MenuGesture
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuRuntimeActions
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import com.awabi2048.ccsystem.api.gui.PlayerInventoryInteraction
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.TourData
import me.awabi2048.myworldmanager.model.TourWaypointData
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.block.BlockFace
import org.bukkit.inventory.EquipmentSlot
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
                    ACTION_SAVE to MenuActionHandler(::saveTour),
                    ACTION_DELETE to MenuActionHandler(::openDelete),
                    ACTION_ADD_WAYPOINT to MenuActionHandler(::addWaypoint),
                    ACTION_REMOVE_WAYPOINT to MenuActionHandler(::removeWaypoint),
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
            ?: plugin.languageManager.getMessage(player, "general.unknown")
        val replacingActiveTour = plugin.tourSessionManager.get(player.uniqueId) != null

        val previewLines = buildList {
            if (tour.description.isNotBlank()) add(GuiLoreLine.UserText(tour.description))
            add(GuiLoreLine.Metadata("by", ownerName))
            if (replacingActiveTour) {
                add(GuiLoreLine.Warning(
                    plugin.languageManager.getMessage(player, "gui.tour.menu.start_confirm.replace_active"),
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
                plugin.languageManager.getMessage(player, "gui.tour.menu.tour_item.action_start"),
                ACTION_START,
                GuiElementRole.CONFIRM),
                simpleActionEntry(player, layout.cancelSlot, Material.RED_WOOL, "gui.common.cancel", GuiElementRole.CANCEL, ACTION_START_CANCEL),
            ),
        )
    }

    private fun renderStopConfirm(player: Player, route: MenuRoute): InventoryMenuView {
        val layout = GuiHelper.confirmationLayout()
        return InventoryMenuView(
            size = layout.size,
            title = GuiHelper.inventoryTitle(
                Component.text(plugin.languageManager.getMessage(player, "gui.tour.menu.stop_confirm.title")),
            ),
            elements = listOf(
                displayEntry(
                    layout.previewSlot,
                    Material.BARRIER,
                    plugin.languageManager.getMessage(player, "gui.tour.menu.stop.display"),
                    listOf(GuiLoreLine.Warning(plugin.languageManager.getMessage(player, "gui.tour.menu.stop_confirm.question"))),
                ),
                actionEntry(
                    layout.confirmSlot,
                    player,
                    Material.LIME_WOOL,
                    plugin.languageManager.getMessage(player, "gui.common.confirm"),
                    emptyList(),
                    plugin.languageManager.getMessage(player, "gui.tour.menu.stop_confirm.confirm"),
                    ACTION_STOP_CONFIRM,
                    GuiElementRole.CONFIRM,
                ),
                simpleActionEntry(player, layout.cancelSlot, Material.RED_WOOL, "gui.common.cancel", GuiElementRole.CANCEL, ACTION_STOP_CANCEL),
            ),
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
            if (plugin.tourSessionManager.get(player.uniqueId)?.worldUuid == worldData.uuid) {
                elements += actionEntry(
                    footerStart + 6,
                    player,
                    Material.BARRIER,
                    plugin.languageManager.getMessage(player, "gui.tour.menu.stop.display"),
                    emptyList(),
                    plugin.languageManager.getMessage(player, "gui.tour.menu.stop.action"),
                    ACTION_STOP,
                    GuiElementRole.CANCEL,
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
            "gui.tour.menu.visitor_title"
        } else {
            "gui.tour.menu.start_selection_title"
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
        if (plugin.tourSessionManager.get(context.player.uniqueId) == null) {
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
        val page = CCSystem.getAPI().getGuiLayoutService().sevenColumnPage(
            tours.size,
            route.payload["page"]?.toIntOrNull() ?: 0,
        )
        val layout = page.layout
        val safePage = page.page
        val elements = mutableListOf<MenuElement>()
        tours.drop(page.startIndex).take(page.itemCount).forEachIndexed { index, tour ->
            elements += createTourEntry(layout.itemSlots[index], player, worldData, tour, true, ACTION_EDIT)
        }
        val footerStart = layout.size - 9
        elements += CCSystem.getAPI().getGuiElementService().backEntry(
            player,
            footerStart + 4,
            plugin.menuConfigManager.getIconMaterial("world_settings", "back", Material.REDSTONE),
        )
        if (worldData.tours.size < plugin.tourManager.getTourLimit(player, worldData)) {
            elements += actionEntry(
                footerStart + 2,
                player,
                Material.NETHER_STAR,
                lang.getMessage(player, "gui.tour.menu.create.display"),
                listOf(GuiLoreLine.Text(lang.getMessage(player, "gui.tour.menu.create.description"))),
                lang.getMessage(player, "gui.tour.menu.create.action"),
                ACTION_CREATE,
                gesture = MenuGesture.ANY,
            )
        }
        elements += createCurrentWorldEntry(player, worldData, 4)
        val infoLines = lang.getMessageList(player, "gui.tour.menu.info.lore")
        elements += displayEntry(
            footerStart + 6,
            Material.REDSTONE_TORCH,
            lang.getMessage(player, "gui.tour.menu.info.display"),
            infoLines.map(::descriptionLine),
            frame = GuiLoreFrame.BOTH,
        )
        if (safePage > 0) {
            elements += navigationEntry(player, layout.previousPageSlot, false, safePage - 1)
        }
        if (safePage + 1 < page.totalPages) {
            elements += navigationEntry(player, layout.nextPageSlot, true, safePage + 1)
        }
        return InventoryMenuView(
            size = layout.size,
            title = GuiHelper.inventoryTitle(Component.text(lang.getMessage(player, "gui.tour.menu.edit_title"))),
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
        val waypointRows = ((tour.waypoints.size + 1 + 6) / 7).coerceAtLeast(1).coerceAtMost(4)
        val rows = (waypointRows + 2).coerceAtMost(6)
        val elements = mutableListOf<MenuElement>()
        val slots = mutableListOf<Int>()
        repeat(waypointRows) { row -> slots.addAll((1..7).map { (row + 1) * 9 + it }) }
        tour.waypoints.take(28).forEachIndexed { index, waypoint ->
            elements += actionEntry(
                slots[index], player, Material.OAK_BOAT, waypoint.name,
                listOf(GuiLoreLine.Metadata("XYZ", "${waypoint.blockX}, ${waypoint.blockY}, ${waypoint.blockZ}")),
                lang.getMessage(player, "gui.tour.menu.remove_waypoint_action"), ACTION_REMOVE_WAYPOINT,
                payload = mapOf("waypoint" to waypoint.uuid.toString()),
            )
        }
        if (tour.waypoints.size < 28) {
            elements += actionEntry(
                slots[tour.waypoints.size],
                player, Material.YELLOW_STAINED_GLASS_PANE,
                lang.getMessage(player, "gui.tour.menu.add_waypoint_button"), emptyList(),
                lang.getMessage(player, "gui.tour.menu.add_sign_action"),
                ACTION_ADD_WAYPOINT,
            )
        }
        val bottom = rows * 9 - 9
        elements += CCSystem.getAPI().getGuiElementService().backEntry(
            player,
            bottom,
            plugin.menuConfigManager.getIconMaterial("world_settings", "back", Material.REDSTONE),
        )
        elements += CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = bottom + 2,
                material = Material.NAME_TAG,
                name = me.awabi2048.myworldmanager.util.fixedLabelName(lang.getMessage(player, "gui.tour.menu.edit_text.display"), GuiNameStyle.DEFAULT),
                role = GuiElementRole.ACTION,
                actions = listOf(
                    menuGestureAction(ACTION_EDIT_TEXT, MenuGesture.PLAIN_LEFT, lang.getMessage(player, "gui.tour.menu.edit_text.action.text"), safety = MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE),
                    menuGestureAction(ACTION_EDIT_TEXT, MenuGesture.PLAIN_RIGHT, lang.getMessage(player, "gui.tour.menu.edit_text.action.icon"), safety = MenuActionSafety.REVERSIBLE, reversibleContract = MwmMenuActionSemantics.contract("tour-icon")),
                ),
            ),
        )
        elements += actionEntry(
            bottom + 4,
            player, Material.LIME_WOOL, lang.getMessage(player, "gui.tour.menu.save.display"),
            emptyList(), lang.getMessage(player, "gui.tour.menu.save.action"),
            ACTION_SAVE,
        )
        elements += actionEntry(
            bottom + 6,
            player, Material.LAVA_BUCKET, lang.getMessage(player, "gui.tour.menu.delete.display"),
            emptyList(), lang.getMessage(player, "gui.tour.menu.delete.action"),
            ACTION_DELETE,
        )
        return InventoryMenuView(
            size = rows * 9,
            title = GuiHelper.inventoryTitle(
                Component.text(lang.getMessage(player, "gui.tour.menu.single_edit_title", mapOf("tour" to tour.name))),
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
        if (!session.awaitingIconPick || context.item.type.isAir) {
            return MenuActionResult.Ignored
        }
        session.draft.icon = context.item.type
        session.awaitingIconPick = false
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun renderDeleteConfirm(player: Player, route: MenuRoute): InventoryMenuView {
        val lang = plugin.languageManager
        val layout = GuiHelper.confirmationLayout()
        return InventoryMenuView(
            size = layout.size,
            title = GuiHelper.inventoryTitle(
                Component.text(lang.getMessage(player, "gui.tour.menu.delete_confirm.title")),
            ),
            elements = listOf(
                displayEntry(
                    layout.previewSlot,
                    Material.LAVA_BUCKET,
                    lang.getMessage(player, "gui.tour.menu.delete_confirm.title"),
                    listOf(
                        GuiLoreLine.Text(lang.getMessage(player, "gui.tour.menu.delete_confirm.body_line1")),
                        GuiLoreLine.Text(lang.getMessage(player, "gui.tour.menu.delete_confirm.body_line2")),
                        GuiLoreLine.Warning(lang.getMessage(player, "gui.tour.menu.delete_confirm.warning")),
                    ),
                ),
                simpleActionEntry(player, layout.confirmSlot, Material.LIME_WOOL, "gui.tour.menu.delete_confirm.confirm", GuiElementRole.CONFIRM, ACTION_DELETE_CONFIRM),
                simpleActionEntry(player, layout.cancelSlot, Material.RED_WOOL, "gui.tour.menu.delete_confirm.cancel", GuiElementRole.CANCEL, ACTION_DELETE_CANCEL),
            ),
        )
    }

    private fun startTour(context: MenuActionContext): MenuActionResult {
        val worldData = world(context.route) ?: return MenuActionResult.Rejected()
        val tour = tour(worldData, context.route) ?: return MenuActionResult.Rejected()
        return when (plugin.tourManager.startTour(context.player, worldData, tour)) {
            me.awabi2048.myworldmanager.service.TourManager.StartTourResult.STARTED ->
                MenuActionResult.Success(MenuUpdate.Close)
            me.awabi2048.myworldmanager.service.TourManager.StartTourResult.INVALID_TOUR -> {
                context.player.sendMessage(plugin.languageManager.getMessage(context.player, "messages.tour.none_available"))
                MenuActionResult.Rejected()
            }
            me.awabi2048.myworldmanager.service.TourManager.StartTourResult.WRONG_WORLD -> {
                context.player.sendMessage(plugin.languageManager.getMessage(context.player, "messages.no_in_myworld"))
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
        if (context.route.payload["is_new"].toBoolean()) {
            return MenuActionResult.Success(
                MenuUpdate.Replace(
                    tourRoute(DISCARD_CONFIRM_ROUTE, worldData.uuid, session.draft.uuid, true),
                ),
            )
        }
        if (!canSaveTour(context.player, session.draft)) return MenuActionResult.Rejected()
        plugin.tourManager.saveEditSession(context.player, worldData)
        return MenuActionResult.Success(MenuUpdate.Back)
    }

    private fun editText(context: MenuActionContext): MenuActionResult {
        val worldData = world(context.route) ?: return MenuActionResult.Rejected()
        val session = plugin.tourSessionManager.getEdit(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        if (context.click.isRightClick) {
            session.awaitingIconPick = true
            context.player.sendMessage(plugin.languageManager.getMessage(context.player, "messages.icon_prompt"))
            return MenuActionResult.Success(MenuUpdate.Refresh)
        }
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

    private fun saveTour(context: MenuActionContext): MenuActionResult {
        val worldData = world(context.route) ?: return MenuActionResult.Rejected()
        val session = plugin.tourSessionManager.getEdit(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        if (!canSaveTour(context.player, session.draft)) return MenuActionResult.Rejected()
        plugin.tourManager.saveEditSession(context.player, worldData)
        return MenuActionResult.Success(MenuUpdate.Back)
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

    private fun removeWaypoint(context: MenuActionContext): MenuActionResult {
        val session = plugin.tourSessionManager.getEdit(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        val waypointUuid = context.payload["waypoint"]?.let(UUID::fromString)
            ?: return MenuActionResult.Rejected()
        session.draft.waypoints.removeIf { it.uuid == waypointUuid }
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun renderDiscardConfirm(player: Player, route: MenuRoute): InventoryMenuView {
        world(route) ?: error("ツアー対象ワールドがありません")
        val lang = plugin.languageManager
        val layout = GuiHelper.confirmationLayout()
        return InventoryMenuView(
            size = layout.size,
            title = GuiHelper.inventoryTitle(
                Component.text(lang.getMessage(player, "gui.tour.menu.discard_new.title")),
            ),
            elements = listOf(
                displayEntry(
                    layout.previewSlot,
                    Material.PAPER,
                    lang.getMessage(player, "gui.tour.menu.discard_new.title"),
                    listOf(
                        GuiLoreLine.Text(lang.getMessage(player, "gui.tour.menu.discard_new.body_line1")),
                        GuiLoreLine.Text(lang.getMessage(player, "gui.tour.menu.discard_new.body_line2")),
                    ),
                ),
                simpleActionEntry(player, layout.confirmSlot, Material.LIME_WOOL, "gui.common.confirm", GuiElementRole.CONFIRM, ACTION_DISCARD_CONFIRM),
                simpleActionEntry(player, layout.cancelSlot, Material.RED_WOOL, "gui.common.cancel", GuiElementRole.CANCEL, ACTION_DISCARD_CANCEL),
            ),
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
            plugin.languageManager.getMessage(context.player, "messages.tour_sign.bound"),
        )
        return MenuActionResult.Success(MenuUpdate.Close)
    }

    private fun cancelBindSign(context: MenuActionContext): MenuActionResult {
        TourDialogManager.consumePlacement(context.player.uniqueId)
        return MenuActionResult.Success(MenuUpdate.Close)
    }

    private fun canSaveTour(player: Player, tour: TourData): Boolean {
        if (tour.waypoints.size >= 2) return true
        player.sendMessage(plugin.languageManager.getMessage(player, "error.tour.not_enough_signs"))
        return false
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
        nameKey: String,
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
        val key = if (next) "gui.common.next_page" else "gui.common.prev_page"
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
            ),
        )
    }

    private fun createCurrentWorldEntry(player: Player, worldData: WorldData, slot: Int): MenuElement {
        val lang = plugin.languageManager
        val owner = Bukkit.getOfflinePlayer(worldData.owner)
        val ownerName = owner.name ?: lang.getMessage(player, "general.unknown")
        val lore = GuiLoreSpec.Blocks(buildList {
            if (worldData.description.isNotBlank()) {
                add(GuiLoreBlock(listOf(GuiLoreLine.UserText(worldData.description))))
            }
            add(GuiLoreBlock(listOf(
                GuiLoreLine.Data(lang.getMessage(player, "gui.common.world_item.world_name"), worldData.name, "§a"),
                GuiLoreLine.Data(lang.getMessage(player, "gui.common.world_item.owner"), ownerName, "§b"),
            )))
        })
        return CCSystem.getAPI().getGuiElementService().menuDisplay(
            GuiMenuDisplaySpec(
                slot,
                GuiItemSpec(
                    worldData.icon,
                    GuiNameSpec.FixedLabel(lang.getComponent(player, "gui.favorite.current_world.name")),
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
            lang.getMessage(player, "gui.tour.menu.tour_item.visitors_none")
        } else {
            lang.getMessage(player, "gui.tour.menu.tour_item.visitors_count", mapOf("count" to tour.completedCount.toString()))
        }
        val action = when {
            editing -> lang.getMessage(player, "gui.tour.menu.tour_item.action_edit")
            current -> lang.getMessage(player, "gui.tour.menu.tour_item.action_current")
            else -> lang.getMessage(player, "gui.tour.menu.tour_item.action_start")
        }
        return actionEntry(slot, player, tour.icon, tour.name, buildList {
            if (tour.description.isNotBlank()) add(GuiLoreLine.UserText(tour.description))
            add(GuiLoreLine.Data(lang.getMessage(player, "gui.tour.menu.tour_item.visitors_label"), countValue, "§a"))
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
                    ACTION_REMOVE_WAYPOINT -> MwmMenuActionSemantics.contract("tour-remove-waypoint")
                    else -> null
                },
            )),
        ),
    )

    private fun tourActionSafety(actionId: String): MenuActionSafety = when (actionId) {
        ACTION_START,
        ACTION_STOP_CONFIRM -> MenuActionSafety.EXTERNAL_SIDE_EFFECT
        ACTION_START_CANCEL,
        ACTION_STOP_CANCEL,
        ACTION_PAGE,
        ACTION_EDIT,
        ACTION_DISCARD_CANCEL,
        ACTION_BIND_CANCEL,
        ACTION_DELETE_CANCEL -> MenuActionSafety.NAVIGATION_ONLY
        ACTION_STOP,
        ACTION_SELECT,
        ACTION_DELETE -> MenuActionSafety.CONFIRM_ENTRY
        ACTION_CREATE,
        ACTION_ADD_WAYPOINT -> MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE
        ACTION_SAVE,
        ACTION_BIND_SIGN,
        ACTION_DELETE_CONFIRM -> MenuActionSafety.IRREVERSIBLE
        ACTION_REMOVE_WAYPOINT,
        ACTION_DISCARD_CONFIRM -> MenuActionSafety.REVERSIBLE
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
                lang.getMessage(player, "gui.tour.menu.tour_item.action_bind"),
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
        elements += simpleActionEntry(player, layout.backSlot, Material.RED_WOOL, "gui.common.cancel", GuiElementRole.CANCEL, ACTION_BIND_CANCEL)
        return InventoryMenuView(
            size = layout.size,
            title = GuiHelper.inventoryTitle(Component.text(lang.getMessage(player, "gui.tour.bind_sign_title"))),
            elements = elements,
        )
    }

    private companion object {
        private const val OWNER = "myworldmanager"
        private const val START_CONFIRM_ROUTE = "tour_start_confirmation"
        private const val STOP_CONFIRM_ROUTE = "tour_stop_confirmation"
        private const val DELETE_CONFIRM_ROUTE = "tour_delete_confirmation"
        private const val VISITOR_ROUTE = "tour_visitor"
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
        private const val ACTION_SINGLE_BACK = "single_back"
        private const val ACTION_EDIT_TEXT = "edit_text"
        private const val ACTION_SAVE = "save"
        private const val ACTION_DELETE = "delete"
        private const val ACTION_ADD_WAYPOINT = "add_waypoint"
        private const val ACTION_REMOVE_WAYPOINT = "remove_waypoint"
        private const val ACTION_DISCARD_CONFIRM = "discard_confirm"
        private const val ACTION_DISCARD_CANCEL = "discard_cancel"
        private const val ACTION_BIND_SIGN = "bind_sign"
        private const val ACTION_BIND_CANCEL = "bind_cancel"
        private const val ACTION_DELETE_CONFIRM = "delete_confirm"
        private const val ACTION_DELETE_CANCEL = "delete_cancel"
    }
}
