package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuRuntimeActions
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.TourData
import me.awabi2048.myworldmanager.model.TourWaypointData
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.block.BlockFace
import org.bukkit.inventory.EquipmentSlot
import java.util.UUID

class TourGui(private val plugin: MyWorldManager) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()
    // ツアー一覧は5行固定にし、ヘッダー中央を現在ワールド表示、下段を操作領域として使う。
    private val pageSlots = listOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34)

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
                id = BIND_SIGN_ROUTE,
                renderer = { context -> renderBindSignMenu(context.player, context.route) },
                actions = mapOf(
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

        val previewLines = buildList {
            if (tour.description.isNotBlank()) add(GuiLoreLine.UserText(tour.description))
            add(GuiLoreLine.Metadata("by", ownerName))
        }
        val layout = GuiHelper.confirmationLayout()
        return InventoryMenuView(
            size = layout.size,
            title = GuiHelper.inventoryTitle(Component.text("§b【${tour.name}】")),
            elements = listOf(
                MenuElement(layout.previewSlot, createItem(
                Material.FILLED_MAP,
                "§b【${tour.name}】",
                framedLore(previewLines),
                ItemTag.TYPE_GUI_INFO
                ), GuiElementRole.CONTENT),
                MenuElement(layout.confirmSlot, createActionItem(
                player,
                Material.LIME_WOOL,
                "§eこのツアーをはじめる！",
                emptyList(),
                plugin.languageManager.getMessage(player, "gui.tour.menu.tour_item.action_start"),
                ItemTag.TYPE_GUI_CONFIRM
                ), GuiElementRole.CONFIRM, ACTION_START),
                MenuElement(
                    layout.cancelSlot,
                    createLoreItem(
                        Material.RED_WOOL,
                        plugin.languageManager.getMessage(player, "gui.common.cancel"),
                        emptyList(),
                        ItemTag.TYPE_GUI_CANCEL,
                    ),
                    GuiElementRole.CANCEL,
                    ACTION_START_CANCEL,
                ),
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
        val maxPage = ((tours.size - 1).coerceAtLeast(0) / pageSlots.size)
        val safePage = requestedPage.coerceIn(0, maxPage)
        val elements = mutableListOf<MenuElement>()
        tours.drop(safePage * pageSlots.size).take(pageSlots.size).forEachIndexed { index, tour ->
            elements += MenuElement(
                pageSlots[index],
                createTourItem(player, worldData, tour, false),
                GuiElementRole.ACTION,
                ACTION_SELECT,
                mapOf("tour" to tour.uuid.toString()),
            )
        }
        val footerStart = 36
        if (showWorldIcon) {
            elements += MenuElement(4, createCurrentWorldItem(player, worldData), GuiElementRole.CONTENT)
            if (plugin.tourSessionManager.get(player.uniqueId)?.worldUuid == worldData.uuid) {
                elements += MenuElement(
                    42,
                    createActionItem(
                        player,
                        Material.BARRIER,
                        plugin.languageManager.getMessage(player, "gui.tour.menu.stop.display"),
                        emptyList(),
                        plugin.languageManager.getMessage(player, "gui.tour.menu.stop.action"),
                        ItemTag.TYPE_GUI_CANCEL,
                    ),
                    GuiElementRole.CANCEL,
                    ACTION_STOP,
                )
            }
        }
        if (safePage > 0) {
            elements += MenuElement(
                footerStart,
                GuiHelper.createPrevPageItem(plugin, player, "tour", safePage - 1),
                GuiElementRole.NAVIGATION,
                ACTION_PAGE,
                mapOf("page" to (safePage - 1).toString()),
            )
        }
        if ((safePage + 1) * pageSlots.size < tours.size) {
            elements += MenuElement(
                footerStart + 8,
                GuiHelper.createNextPageItem(plugin, player, "tour", safePage + 1),
                GuiElementRole.NAVIGATION,
                ACTION_PAGE,
                mapOf("page" to (safePage + 1).toString()),
            )
        }
        val titleKey = if (showWorldIcon) {
            "gui.tour.menu.visitor_title"
        } else {
            "gui.tour.menu.start_selection_title"
        }
        return InventoryMenuView(
            size = 45,
            title = GuiHelper.inventoryTitle(Component.text(plugin.languageManager.getMessage(player, titleKey))),
            elements = elements,
        )
    }

    fun openEditMenu(player: Player, worldData: WorldData, page: Int = 0) {
        runtime.navigate(
            player,
            MenuRoute(
                OWNER,
                EDIT_ROUTE,
                mapOf("world" to worldData.uuid.toString(), "page" to page.coerceAtLeast(0).toString()),
            ),
        )
    }

    private fun stopTour(context: MenuActionContext): MenuActionResult {
        plugin.tourManager.stopTour(context.player)
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun renderEditMenu(player: Player, route: MenuRoute): InventoryMenuView {
        val worldData = world(route) ?: error("ツアー対象ワールドがありません")
        val lang = plugin.languageManager
        val tours = worldData.tours.sortedBy { it.createdAt }
        val maxPage = ((tours.size - 1).coerceAtLeast(0) / pageSlots.size)
        val safePage = (route.payload["page"]?.toIntOrNull() ?: 0).coerceIn(0, maxPage)
        val elements = mutableListOf<MenuElement>()
        tours.drop(safePage * pageSlots.size).take(pageSlots.size).forEachIndexed { index, tour ->
            elements += MenuElement(
                pageSlots[index],
                createEditTourItem(player, worldData, tour),
                GuiElementRole.ACTION,
                ACTION_EDIT,
                mapOf("tour" to tour.uuid.toString()),
            )
        }
        val footerStart = 36
        elements += MenuElement(
            footerStart + 4,
            createLoreItem(Material.REDSTONE, lang.getMessage(player, "gui.tour.menu.back"), emptyList(), ItemTag.TYPE_GUI_TOUR_BACK),
            GuiElementRole.NAVIGATION,
            ACTION_BACK,
        )
        if (worldData.tours.size < plugin.tourManager.getTourLimit(player, worldData)) {
            elements += MenuElement(
                footerStart + 2,
                createActionItem(player, Material.NETHER_STAR, lang.getMessage(player, "gui.tour.menu.create.display"), listOf(GuiLoreLine.Text(lang.getMessage(player, "gui.tour.menu.create.description"))), lang.getMessage(player, "gui.tour.menu.create.action"), ItemTag.TYPE_GUI_TOUR_CREATE),
                GuiElementRole.ACTION,
                ACTION_CREATE,
            )
        }
        elements += MenuElement(4, createCurrentWorldItem(player, worldData), GuiElementRole.CONTENT)
        val infoLines = lang.getMessageList(player, "gui.tour.menu.info.lore")
        elements += MenuElement(
            footerStart + 6,
            createLoreItem(Material.REDSTONE_TORCH, lang.getMessage(player, "gui.tour.menu.info.display"), infoLines.map(GuiLoreLine::Text), ItemTag.TYPE_GUI_TOUR_INFO, GuiLoreFrame.BOTH),
            GuiElementRole.CONTENT,
        )
        if (safePage > 0) {
            elements += MenuElement(
                footerStart,
                GuiHelper.createPrevPageItem(plugin, player, "tour", safePage - 1),
                GuiElementRole.NAVIGATION,
                ACTION_PAGE,
                mapOf("page" to (safePage - 1).toString()),
            )
        }
        if ((safePage + 1) * pageSlots.size < tours.size) {
            elements += MenuElement(
                footerStart + 8,
                GuiHelper.createNextPageItem(plugin, player, "tour", safePage + 1),
                GuiElementRole.NAVIGATION,
                ACTION_PAGE,
                mapOf("page" to (safePage + 1).toString()),
            )
        }
        return InventoryMenuView(
            size = 45,
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
            elements += MenuElement(
                slots[index],
                createWaypointItem(player, waypoint, lang.getMessage(player, "gui.tour.menu.remove_waypoint_action")),
                GuiElementRole.ACTION,
                ACTION_REMOVE_WAYPOINT,
                mapOf("waypoint" to waypoint.uuid.toString()),
            )
        }
        if (tour.waypoints.size < 28) {
            elements += MenuElement(
                slots[tour.waypoints.size],
                createActionItem(player, Material.YELLOW_STAINED_GLASS_PANE, lang.getMessage(player, "gui.tour.menu.add_waypoint_button"), emptyList(), lang.getMessage(player, "gui.tour.menu.add_sign_action"), ItemTag.TYPE_GUI_TOUR_ADD_WAYPOINT),
                GuiElementRole.ACTION,
                ACTION_ADD_WAYPOINT,
            )
        }
        val bottom = rows * 9 - 9
        elements += MenuElement(
            bottom,
            createLoreItem(Material.REDSTONE, lang.getMessage(player, "gui.tour.menu.back"), emptyList(), ItemTag.TYPE_GUI_TOUR_BACK),
            GuiElementRole.NAVIGATION,
            ACTION_SINGLE_BACK,
        )
        val editTextLore = GuiLoreSpec.Blocks(listOf(
            GuiLoreBlock(listOf(
                GuiLoreLine.Action(lang.getMessage(player, "lore.click.left"), lang.getMessage(player, "gui.tour.menu.edit_text.action.text")),
                GuiLoreLine.Action(lang.getMessage(player, "lore.click.right"), lang.getMessage(player, "gui.tour.menu.edit_text.action.icon"))
            ))
        ))
        elements += MenuElement(
            bottom + 2,
            createItem(Material.NAME_TAG, lang.getMessage(player, "gui.tour.menu.edit_text.display"), editTextLore, ItemTag.TYPE_GUI_TOUR_EDIT_TEXT),
            GuiElementRole.ACTION,
            ACTION_EDIT_TEXT,
        )
        elements += MenuElement(
            bottom + 4,
            createActionItem(player, Material.LIME_WOOL, lang.getMessage(player, "gui.tour.menu.save.display"), emptyList(), lang.getMessage(player, "gui.tour.menu.save.action"), ItemTag.TYPE_GUI_TOUR_SAVE),
            GuiElementRole.ACTION,
            ACTION_SAVE,
        )
        elements += MenuElement(
            bottom + 6,
            createActionItem(player, Material.LAVA_BUCKET, lang.getMessage(player, "gui.tour.menu.delete.display"), emptyList(), lang.getMessage(player, "gui.tour.menu.delete.action"), ItemTag.TYPE_GUI_TOUR_DELETE),
            GuiElementRole.ACTION,
            ACTION_DELETE,
        )
        return InventoryMenuView(
            size = rows * 9,
            title = GuiHelper.inventoryTitle(
                Component.text(lang.getMessage(player, "gui.tour.menu.single_edit_title", mapOf("tour" to tour.name))),
            ),
            elements = elements,
            allowPlayerInventoryInteraction = session.awaitingIconPick,
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
                MenuElement(layout.previewSlot, createLoreItem(
                Material.LAVA_BUCKET,
                lang.getMessage(player, "gui.tour.menu.delete_confirm.title"),
                listOf(
                    GuiLoreLine.Text(lang.getMessage(player, "gui.tour.menu.delete_confirm.body_line1")),
                    GuiLoreLine.Text(lang.getMessage(player, "gui.tour.menu.delete_confirm.body_line2")),
                    GuiLoreLine.Warning(lang.getMessage(player, "gui.tour.menu.delete_confirm.warning"))
                ),
                ItemTag.TYPE_GUI_INFO
                ), GuiElementRole.CONTENT),
                MenuElement(layout.confirmSlot, createLoreItem(
                Material.LIME_WOOL,
                lang.getMessage(player, "gui.tour.menu.delete_confirm.confirm"),
                emptyList(),
                ItemTag.TYPE_GUI_CONFIRM
                ), GuiElementRole.CONFIRM, ACTION_DELETE_CONFIRM),
                MenuElement(layout.cancelSlot, createLoreItem(
                Material.RED_WOOL,
                lang.getMessage(player, "gui.tour.menu.delete_confirm.cancel"),
                emptyList(),
                ItemTag.TYPE_GUI_CANCEL
                ), GuiElementRole.CANCEL, ACTION_DELETE_CANCEL),
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
        context.player.playSound(
            context.player.location,
            Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
            0.9f,
            1.4f,
        )
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
                MenuElement(
                    layout.previewSlot,
                    createLoreItem(
                        Material.PAPER,
                        lang.getMessage(player, "gui.tour.menu.discard_new.title"),
                        listOf(
                            GuiLoreLine.Text(lang.getMessage(player, "gui.tour.menu.discard_new.body_line1")),
                            GuiLoreLine.Text(lang.getMessage(player, "gui.tour.menu.discard_new.body_line2")),
                        ),
                        ItemTag.TYPE_GUI_INFO,
                    ),
                    GuiElementRole.CONTENT,
                ),
                MenuElement(
                    layout.confirmSlot,
                    createLoreItem(Material.LIME_WOOL, lang.getMessage(player, "gui.common.confirm"), emptyList(), ItemTag.TYPE_GUI_CONFIRM),
                    GuiElementRole.ACTION,
                    ACTION_DISCARD_CONFIRM,
                ),
                MenuElement(
                    layout.cancelSlot,
                    createLoreItem(Material.RED_WOOL, lang.getMessage(player, "gui.common.cancel"), emptyList(), ItemTag.TYPE_GUI_CANCEL),
                    GuiElementRole.NAVIGATION,
                    ACTION_DISCARD_CANCEL,
                ),
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

    private fun createCurrentWorldItem(player: Player, worldData: WorldData): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(worldData.icon)
        val meta = item.itemMeta ?: return item
        val owner = Bukkit.getOfflinePlayer(worldData.owner)
        val ownerName = owner.name ?: lang.getMessage(player, "general.unknown")
        meta.displayName(lang.getComponent(player, "gui.favorite.current_world.name"))
        val lore = CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(listOf(
            GuiLoreBlock(buildList {
                add(GuiLoreLine.Data(lang.getMessage(player, "gui.common.world_item.world_name"), worldData.name, "§a"))
                if (worldData.description.isNotBlank()) add(GuiLoreLine.UserText(worldData.description))
            }),
            GuiLoreBlock(listOf(GuiLoreLine.Data(lang.getMessage(player, "gui.common.world_item.owner"), ownerName, "§b")))
        )))
        meta.lore(lore)
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_TOUR_CURRENT_WORLD)
        return item
    }

    private fun createTourItem(player: Player, worldData: WorldData, tour: TourData, editing: Boolean): ItemStack {
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
        val item = createActionItem(player, tour.icon, tour.name, buildList {
            if (tour.description.isNotBlank()) add(GuiLoreLine.UserText(tour.description))
            add(GuiLoreLine.Data(lang.getMessage(player, "gui.tour.menu.tour_item.visitors_label"), countValue, "§a"))
        }, action, ItemTag.TYPE_GUI_TOUR_ITEM)
        ItemTag.setString(item, "tour_uuid", tour.uuid.toString())
        return item
    }

    private fun createEditTourItem(player: Player, worldData: WorldData, tour: TourData): ItemStack = createTourItem(player, worldData, tour, true)

    private fun createWaypointItem(player: Player, waypoint: TourWaypointData, actionLine: String): ItemStack {
        val lang = plugin.languageManager
        val item = createActionItem(player, Material.OAK_BOAT, waypoint.name, listOf(
            GuiLoreLine.Metadata("XYZ", "${waypoint.blockX}, ${waypoint.blockY}, ${waypoint.blockZ}")
        ), actionLine, ItemTag.TYPE_GUI_TOUR_WAYPOINT_ITEM)
        ItemTag.setString(item, "tour_waypoint_uuid", waypoint.uuid.toString())
        return item
    }

    private fun createLoreItem(
        material: Material,
        name: String,
        lore: List<GuiLoreLine>,
        type: String,
        frame: GuiLoreFrame = GuiLoreFrame.TOP
    ): ItemStack {
        val spec = if (lore.isEmpty()) GuiLoreSpec.None else GuiLoreSpec.Rich(lore, frame)
        return createItem(material, name, spec, type)
    }

    private fun createActionItem(
        player: Player,
        material: Material,
        name: String,
        information: List<GuiLoreLine>,
        action: String,
        type: String
    ): ItemStack {
        val lore = GuiLoreSpec.Blocks(buildList {
            if (information.isNotEmpty()) add(GuiLoreBlock(information))
            add(GuiLoreBlock(listOf(me.awabi2048.myworldmanager.util.GuiLoreActions.singleClick(plugin.languageManager, player, action))))
        })
        return createItem(material, name, lore, type)
    }

    private fun createItem(material: Material, name: String, lore: GuiLoreSpec, type: String): ItemStack {
        return GuiItemFactory.item(material, name, lore, type)
    }

    private fun framedLore(lines: List<GuiLoreLine>): GuiLoreSpec {
        if (lines.isEmpty()) return GuiLoreSpec.None
        return GuiLoreSpec.Rich(lines, GuiLoreFrame.BOTH)
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
        val unboundTours = worldData.tours.filter { it.startSignUuid == null }
        val rows = (((minOf(pageSlots.size, maxOf(7, unboundTours.size)) + 6) / 7) + 2).coerceIn(3, 6)
        val elements = mutableListOf<MenuElement>()
        unboundTours.sortedBy { it.createdAt }.take((rows - 2) * 7).forEachIndexed { index, tour ->
            val row = index / 7 + 1
            val col = index % 7 + 1
            val item = createActionItem(player, tour.icon, tour.name,
                if (tour.description.isBlank()) emptyList() else listOf(GuiLoreLine.UserText(tour.description)), lang.getMessage(player, "gui.tour.menu.tour_item.action_bind"),
                ItemTag.TYPE_GUI_TOUR_ITEM)
            ItemTag.setString(item, "tour_uuid", tour.uuid.toString())
            elements += MenuElement(
                row * 9 + col,
                item,
                GuiElementRole.ACTION,
                ACTION_BIND_SIGN,
                mapOf("tour" to tour.uuid.toString()),
            )
        }
        val footerStart = rows * 9 - 9
        elements += MenuElement(
            footerStart + 4,
            createLoreItem(
                Material.RED_WOOL,
                lang.getMessage(player, "gui.common.cancel"),
                emptyList(),
                ItemTag.TYPE_GUI_CANCEL,
            ),
            GuiElementRole.CANCEL,
            ACTION_BIND_CANCEL,
        )
        return InventoryMenuView(
            size = rows * 9,
            title = GuiHelper.inventoryTitle(Component.text(lang.getMessage(player, "gui.tour.bind_sign_title"))),
            elements = elements,
        )
    }

    private companion object {
        private const val OWNER = "myworldmanager"
        private const val START_CONFIRM_ROUTE = "tour_start_confirmation"
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
