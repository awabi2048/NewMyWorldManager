package me.awabi2048.myworldmanager.ui.bedrock

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiMenuIconData
import com.awabi2048.ccsystem.api.gui.GuiMenuIconOption
import com.awabi2048.ccsystem.api.gui.GuiMenuIconSpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuAcceptedClicks
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.extension.PlayerWorldCapabilityContract
import me.awabi2048.myworldmanager.api.extension.PlayerWorldCapabilitySubject
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.TourNavigationMode
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.GuiLoreActions
import me.awabi2048.myworldmanager.util.PermissionManager
import me.awabi2048.myworldmanager.util.WorldCreationChecks
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import me.awabi2048.myworldmanager.util.StructuredLore
import me.awabi2048.myworldmanager.util.WorldRuntimePolicies
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

class BedrockMenuService(
    private val plugin: MyWorldManager,
    private val routingService: BedrockUiRoutingService,
    private val formBridge: FloodgateFormBridge
) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()

    init {
        listOf(
            PLAYER_WORLD_ROUTE to { context: com.awabi2048.ccsystem.api.gui.MenuRenderContext ->
                renderPlayerWorldInventory(
                    context.player,
                    context.route.payload["page"]?.toIntOrNull() ?: 0,
                    context.route.payload["back"]?.toBooleanStrictOrNull() ?: false,
                )
            },
            WORLD_ACTION_ROUTE to { context: com.awabi2048.ccsystem.api.gui.MenuRenderContext ->
                val worldUuid = UUID.fromString(context.route.payload.getValue("world"))
                val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
                    ?: error("Bedrockワールド操作対象が見つかりません: $worldUuid")
                renderWorldActionsInventory(
                    context.player,
                    worldData,
                    context.route.payload["page"]?.toIntOrNull() ?: 0,
                    context.route.payload["back"]?.toBooleanStrictOrNull() ?: false,
                )
            },
            SETTINGS_ROUTE to { context: com.awabi2048.ccsystem.api.gui.MenuRenderContext ->
                renderSettingsInventory(
                    context.player,
                    context.route.payload["back"]?.toBooleanStrictOrNull() ?: false,
                    context.route.payload["page"]?.toIntOrNull() ?: 0,
                )
            },
        ).forEach { (id, renderer) ->
            runtime.register(
                InventoryMenuDefinition(
                    owner = RUNTIME_OWNER,
                    id = id,
                    renderer = renderer,
                    actions = RUNTIME_ACTION_IDS.associateWith {
                        MenuActionHandler(::handleRuntimeAction)
                    },
                ),
            )
        }
    }

    private class RuntimeItemBuffer {
        private val items = linkedMapOf<Int, ItemStack>()
        private val actions = mutableMapOf<Int, RuntimeAction>()

        fun setItem(slot: Int, item: ItemStack?) {
            if (item == null || item.type == Material.AIR) {
                items.remove(slot)
                actions.remove(slot)
            } else {
                items[slot] = item
                actions.remove(slot)
            }
        }

        fun setActionItem(
            slot: Int,
            item: ItemStack,
            actionId: String,
            payload: Map<String, String> = emptyMap(),
            role: GuiElementRole = GuiElementRole.ACTION,
            acceptedClicks: Set<org.bukkit.event.inventory.ClickType> = MenuAcceptedClicks.LEFT_RIGHT,
        ) {
            items[slot] = item
            actions[slot] = RuntimeAction(actionId, payload, role, acceptedClicks)
        }

        fun elements(): List<MenuElement> = items.map { (slot, item) ->
            val action = actions[slot]
            MenuElement(
                slot = slot,
                item = item,
                role = action?.role ?: if (item.type.name.endsWith("_STAINED_GLASS_PANE")) {
                    GuiElementRole.DECORATION
                } else {
                    GuiElementRole.CONTENT
                },
                actionId = action?.id,
                actionPayload = action?.payload.orEmpty(),
                interaction = action?.let {
                    com.awabi2048.ccsystem.api.gui.MenuInteraction.Action(
                        it.id,
                        it.acceptedClicks,
                        it.payload,
                    )
                },
            )
        }

        private data class RuntimeAction(
            val id: String,
            val payload: Map<String, String>,
            val role: GuiElementRole,
            val acceptedClicks: Set<org.bukkit.event.inventory.ClickType>,
        )
    }

    private data class FormAction(
        val label: String,
        val iconMaterial: Material? = null,
        val onClick: () -> MenuActionResult,
    )

    private val playerWorldPageSize = 28

    private val materialPathOverrides =
        mapOf(
            Material.WRITABLE_BOOK to "textures/items/book_writable",
            Material.COMPASS to "textures/items/compass_item",
            Material.REDSTONE to "textures/items/redstone_dust",
            Material.EXPERIENCE_BOTTLE to "textures/items/experience_bottle",
            Material.ENDER_PEARL to "textures/items/ender_pearl",
            Material.ENDER_EYE to "textures/items/ender_eye"
        )

    fun openPlayerWorld(player: Player, page: Int = 0, showBackButton: Boolean = false) {
        val safePage = page.coerceAtLeast(0)
        runtime.navigate(player, playerWorldRoute(safePage, showBackButton))
    }

    fun openCurrentWorldMenu(player: Player, worldData: WorldData, showBackButton: Boolean = false) {
        plugin.worldSettingsGui.open(player, worldData, showBackButton)
    }

    fun openSettings(player: Player, showBackButton: Boolean = false, returnPage: Int = 0) {
        runtime.navigate(player, settingsRoute(showBackButton, returnPage))
    }

    fun openDiscovery(player: Player, page: Int = 0, showBackButton: Boolean = false) {
        plugin.discoveryGui.open(player, page, showBackButton)
    }

    fun openFavoriteList(
        player: Player,
        page: Int = 0,
        worldData: WorldData? = null,
        returnToFavoriteMenu: Boolean = false,
        showBackButton: Boolean = false
    ) {
        plugin.favoriteGui.open(player, page, worldData, returnToFavoriteMenu, showBackButton)
    }

    fun openFavoriteMenu(player: Player, worldData: WorldData?) {
        plugin.favoriteMenuGui.open(player, worldData)
    }

    fun openVisitMenu(
        player: Player,
        owner: org.bukkit.OfflinePlayer,
        page: Int = 0,
        worldData: WorldData? = null
    ) {
        plugin.visitGui.open(player, owner, page, worldData)
    }

    fun openMeet(player: Player, showBackButton: Boolean? = null) {
        plugin.meetGui.open(player, showBackButton)
    }

    private fun openWorldActionsForm(
        player: Player,
        worldData: WorldData,
        returnPage: Int,
        showBackButton: Boolean
    ): Boolean {
        val actions = mutableListOf<FormAction>()

        actions += FormAction(tr(player, "gui.bedrock.world_action.button.warp"), Material.ENDER_PEARL) {
            val latest = plugin.worldConfigRepository.findByUuid(worldData.uuid)
                ?: return@FormAction MenuActionResult.Rejected()
            warpToWorld(player, latest)
            MenuActionResult.Success(MenuUpdate.Close)
        }

        if (canManagePublish(player, worldData)) {
            actions +=
                FormAction(
                    tr(
                        player,
                        "gui.bedrock.world_action.button.cycle_publish",
                        mapOf("level" to publishDisplayText(player, worldData))
                    ),
                    Material.ENDER_EYE
                ) {
                val latest = plugin.worldConfigRepository.findByUuid(worldData.uuid)
                    ?: return@FormAction MenuActionResult.Rejected()
                cyclePublishLevel(player, latest)
                MenuActionResult.Success(
                    MenuUpdate.Replace(
                        worldActionRoute(worldData.uuid, returnPage, showBackButton),
                    ),
                )
            }
        }

        if (canManageArchive(player, worldData)) {
            val label =
                if (worldData.isArchived) {
                    tr(player, "gui.bedrock.world_action.button.unarchive")
                } else {
                    tr(player, "gui.bedrock.world_action.button.archive")
                }
            actions += FormAction(label, Material.CHEST) {
                val latest = plugin.worldConfigRepository.findByUuid(worldData.uuid)
                    ?: return@FormAction MenuActionResult.Rejected()
                toggleArchiveState(player, latest) {
                    val refreshed = plugin.worldConfigRepository.findByUuid(worldData.uuid)
                    if (refreshed != null) {
                        openWorldActionMenu(player, refreshed, returnPage, showBackButton)
                    } else {
                        openPlayerWorld(player, returnPage, showBackButton)
                    }
                }
                MenuActionResult.Success(MenuUpdate.Close)
            }
        }

        if (canAccessWorldSettings(player, worldData)) {
            actions += FormAction(tr(player, "gui.bedrock.world_action.button.advanced_settings"), Material.COMPARATOR) {
                val latest = plugin.worldConfigRepository.findByUuid(worldData.uuid)
                    ?: return@FormAction MenuActionResult.Rejected()
                plugin.worldSettingsGui.open(player, latest, showBackButton)
                MenuActionResult.Success(MenuUpdate.None)
            }
        }

        actions += FormAction(tr(player, "gui.bedrock.world_action.button.back_to_worlds"), Material.ARROW) {
            MenuActionResult.Success(
                MenuUpdate.Replace(playerWorldRoute(returnPage, showBackButton)),
            )
        }

        actions += FormAction(tr(player, "gui.bedrock.world_action.button.settings"), Material.WRITABLE_BOOK) {
            MenuActionResult.Success(
                MenuUpdate.Navigate(settingsRoute(showBackButton, returnPage)),
            )
        }

        if (GuiHelper.canGoBack(player)) {
            actions += FormAction(tr(player, "gui.bedrock.world_action.button.return"), Material.BARRIER) {
                performConfiguredReturn(player)
                MenuActionResult.Success(MenuUpdate.Close)
            }
        }

        actions += FormAction(tr(player, "gui.bedrock.world_action.button.close"), Material.REDSTONE) {
            MenuActionResult.Success(MenuUpdate.Close)
        }

        val content =
            listOf(
                    tr(
                        player,
                        "gui.bedrock.world_action.content.owner",
                        mapOf("owner" to worldData.owner)
                    ),
                    tr(
                        player,
                        "gui.bedrock.world_action.content.status",
                        mapOf("status" to worldStateText(player, worldData.isArchived))
                    ),
                    tr(
                        player,
                        "gui.bedrock.world_action.content.publish",
                        mapOf("publish" to publishDisplayText(player, worldData))
                    )
                )
                .joinToString("\n")
        return sendActionForm(
            player,
            tr(player, "gui.bedrock.world_action.title", mapOf("world" to worldData.name)),
            content,
            actions
        )
    }

    private fun sendActionForm(
        player: Player,
        title: String,
        content: String,
        actions: List<FormAction>
    ): Boolean {
        if (actions.isEmpty()) {
            return false
        }

        val buttons =
            actions.map { action ->
                FloodgateFormBridge.SimpleFormButton(
                    label = action.label,
                    imagePath = materialToBedrockPath(action.iconMaterial)
                )
            }
        return formBridge.sendSimpleFormWithImagesResult(
            player = player,
            title = title,
            content = content,
            buttons = buttons,
            onSelect = { index ->
                val action = actions.getOrNull(index)
                    ?: return@sendSimpleFormWithImagesResult MenuActionResult.Ignored
                action.onClick()
            }
        )
    }

    private fun renderPlayerWorldInventory(
        player: Player,
        requestedPage: Int,
        showBackButton: Boolean,
    ): InventoryMenuView {
        val worlds = getAccessibleWorlds(player)
        val pageSize = playerWorldPageSize
        val totalPages = if (worlds.isEmpty()) 1 else (worlds.size + pageSize - 1) / pageSize
        val page = requestedPage.coerceIn(0, totalPages - 1)
        val start = page * pageSize
        val pageWorlds = worlds.drop(start).take(pageSize)
        val capabilitySubject = PlayerWorldCapabilitySubject(
            player,
            player.uniqueId,
            player.name,
            worlds,
            showBackButton,
            playerWorldRoute(page, showBackButton),
        )
        val capabilityService = CCSystem.getAPI().getMenuCapabilityService()

        val neededDataRows = if (pageWorlds.isEmpty()) 1 else (pageWorlds.size + 6) / 7
        val rowCount = (neededDataRows + 2).coerceIn(3, 6)
        val footerStart = (rowCount - 1) * 9
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val currentCreateCount = worlds.count { it.owner == player.uniqueId }
        val maxSlot = WorldRuntimePolicies.maxCreateCountDefault(plugin.config) + stats.unlockedWorldSlot
        val bypassLimits = PermissionManager.canBypassWorldLimits(player)

        val title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(
            tr(player, "gui.player_world.title"),
        )
        val inventory = RuntimeItemBuffer()

        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)

        for (slot in 0..8) {
            inventory.setItem(slot, blackPane)
        }
        for (row in 0 until neededDataRows) {
            val rowStart = (row + 1) * 9
            inventory.setItem(rowStart, grayPane)
            inventory.setItem(rowStart + 8, grayPane)
            for (col in 1..7) {
                inventory.setItem(rowStart + col, grayPane)
            }
        }
        for (slot in footerStart until footerStart + 9) {
            inventory.setItem(slot, blackPane)
        }

        pageWorlds.forEachIndexed { index, worldData ->
            val row = index / 7
            val col = index % 7
            val slot = (row + 1) * 9 + 1 + col
            val attributes = playerWorldCapabilityAttributes(capabilitySubject, worldData)
            val capability = capabilityService
                .definitions(PlayerWorldCapabilityContract.WORLD_ITEM_PLACEMENT)
                .firstNotNullOfOrNull { definition ->
                    capabilityService.resolve(
                        definition.capabilityId,
                        player,
                        attributes = attributes,
                    )
                }
            inventory.setActionItem(
                slot,
                capability?.let {
                    CCSystem.getAPI().getGuiElementService().menuCapability(it.presentation)
                } ?: createWorldListItem(player, worldData),
                if (capability == null) "warp_world" else "capability_world",
                buildMap {
                    put("world", worldData.uuid.toString())
                    capability?.let { put("capability", it.capabilityId) }
                },
                acceptedClicks = capability?.acceptedClicks ?: MenuAcceptedClicks.LEFT_RIGHT,
            )
        }

        if (page > 0) {
            inventory.setActionItem(
                footerStart + 1,
                createActionItem(Material.ARROW, tr(player, "gui.bedrock.player_world.button.prev"), "open_prev_page"),
                "open_prev_page",
                role = GuiElementRole.NAVIGATION,
            )
        }
        if (start + pageWorlds.size < worlds.size) {
            inventory.setActionItem(
                footerStart + 8,
                createActionItem(Material.ARROW, tr(player, "gui.bedrock.player_world.button.next"), "open_next_page"),
                "open_next_page",
                role = GuiElementRole.NAVIGATION,
            )
        }

        val creationBlockReason = creationBlockReason(player, currentCreateCount, maxSlot, bypassLimits)
        val creationCapability = capabilityService
            .definitions(PlayerWorldCapabilityContract.CREATION_PLACEMENT)
            .firstNotNullOfOrNull { definition ->
                capabilityService.resolve(
                    definition.capabilityId,
                    player,
                    attributes = playerWorldCapabilityAttributes(capabilitySubject),
                )
            }
        if (creationCapability != null) {
            val item = CCSystem.getAPI().getGuiElementService()
                .menuCapability(creationCapability.presentation)
            if (creationCapability.actionable) {
                inventory.setActionItem(
                    footerStart + 2,
                    item,
                    "capability_create",
                    mapOf("capability" to creationCapability.capabilityId),
                    acceptedClicks = creationCapability.acceptedClicks,
                )
            } else {
                inventory.setItem(footerStart + 2, item)
            }
        } else if (creationBlockReason == null) {
            inventory.setActionItem(
                footerStart + 2,
                createCreationButtonItem(player),
                "start_creation",
            )
        } else {
            inventory.setItem(footerStart + 2, createCreationUnavailableButtonItem(player, creationBlockReason))
        }
        val summaryCapability = capabilityService
            .definitions(PlayerWorldCapabilityContract.SUMMARY_PLACEMENT)
            .firstNotNullOfOrNull { definition ->
                capabilityService.resolve(
                    definition.capabilityId,
                    player,
                    attributes = playerWorldCapabilityAttributes(capabilitySubject),
                )
            }
        val statsItem = summaryCapability?.let {
            CCSystem.getAPI().getGuiElementService().menuCapability(it.presentation)
        } ?: createStatsButtonItem(player, currentCreateCount, maxSlot, stats.worldPoint)
        if (plugin.pendingDecisionManager.getPendingCount(player.uniqueId) > 0) {
            inventory.setActionItem(
                footerStart + 4,
                statsItem,
                "open_pending_interactions",
            )
        } else {
            inventory.setItem(footerStart + 4, statsItem)
        }
        inventory.setActionItem(
            footerStart + 6,
            createActionItem(
                Material.WRITABLE_BOOK,
                tr(player, "gui.user_settings.button.display"),
                "open_settings",
                lore = GuiLoreSpec.Blocks(listOf(com.awabi2048.ccsystem.api.gui.GuiLoreBlock(listOf(
                    GuiLoreActions.singleClick(plugin.languageManager, player, tr(player, "gui.user_settings.button.action"))
                ))))
            ),
            "open_settings",
        )

        if (GuiHelper.canGoBack(player)) {
            inventory.setActionItem(
                footerStart,
                createActionItem(Material.BARRIER, tr(player, "gui.bedrock.player_world.button.return"), "return_command"),
                "return_command",
                role = GuiElementRole.CANCEL,
            )
        }

        return InventoryMenuView(
            size = rowCount * 9,
            title = title,
            elements = inventory.elements(),
            standardFrame = false,
        )
    }

    private fun renderWorldActionsInventory(
        player: Player,
        worldData: WorldData,
        returnPage: Int,
        showBackButton: Boolean
    ): InventoryMenuView {
        val title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(
            tr(player, "gui.bedrock.world_action.title", mapOf("world" to worldData.name)),
        )
        val inventory = RuntimeItemBuffer()

        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        for (slot in 0 until 27) {
            inventory.setItem(slot, blackPane)
        }

        inventory.setActionItem(
            10,
            createActionItem(Material.ENDER_PEARL, tr(player, "gui.bedrock.world_action.button.warp"), "warp_world", worldData.uuid),
            "warp_world",
        )

        if (canManagePublish(player, worldData)) {
            inventory.setActionItem(
                11,
                createActionItem(
                    Material.ENDER_EYE,
                    tr(
                        player,
                        "gui.bedrock.world_action.button.cycle_publish",
                        mapOf("level" to publishDisplayText(player, worldData))
                    ),
                    "cycle_publish",
                    worldData.uuid
                ),
                "cycle_publish",
            )
        }

        if (canManageArchive(player, worldData)) {
            val label =
                if (worldData.isArchived) {
                    tr(player, "gui.bedrock.world_action.button.unarchive")
                } else {
                    tr(player, "gui.bedrock.world_action.button.archive")
                }
            inventory.setActionItem(
                12,
                createActionItem(Material.CHEST, label, "toggle_archive", worldData.uuid),
                "toggle_archive",
            )
        }

        if (canAccessWorldSettings(player, worldData)) {
            inventory.setActionItem(
                14,
                createActionItem(
                    Material.COMPARATOR,
                    tr(player, "gui.bedrock.world_action.button.advanced_settings"),
                    "open_advanced_settings",
                    worldData.uuid
                ),
                "open_advanced_settings",
            )
        }

        inventory.setActionItem(
            15,
            createActionItem(Material.WRITABLE_BOOK, tr(player, "gui.bedrock.world_action.button.settings"), "open_settings"),
            "open_settings",
        )

        inventory.setActionItem(
            16,
            createActionItem(Material.ARROW, tr(player, "gui.bedrock.world_action.button.back_to_worlds"), "back_to_worlds"),
            "back_to_worlds",
            role = GuiElementRole.NAVIGATION,
        )

        if (GuiHelper.canGoBack(player)) {
            inventory.setActionItem(
                22,
                createActionItem(Material.BARRIER, tr(player, "gui.bedrock.world_action.button.return"), "return_command"),
                "return_command",
                role = GuiElementRole.CANCEL,
            )
        } else {
            inventory.setActionItem(
                22,
                createActionItem(Material.REDSTONE, tr(player, "gui.bedrock.world_action.button.close"), "close_menu"),
                "close_menu",
                role = GuiElementRole.CANCEL,
            )
        }

        return InventoryMenuView(
            size = 27,
            title = title,
            elements = inventory.elements(),
            standardFrame = false,
        )
    }

    private fun renderSettingsInventory(
        player: Player,
        showBackButton: Boolean,
        returnPage: Int,
    ): InventoryMenuView {
        val title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(tr(player, "gui.user_settings.title"))
        val inventory = RuntimeItemBuffer()

        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)

        for (slot in 0..8) {
            inventory.setItem(slot, blackPane)
        }
        for (slot in 9..17) {
            inventory.setItem(slot, grayPane)
        }
        for (slot in 18..26) {
            inventory.setItem(slot, blackPane)
        }

        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val languageName = languageDisplay(player, plugin.languageManager.resolveLocale(player))
        val notifyStatus = statusText(player, stats.visitorNotificationEnabled)

        inventory.setActionItem(
            10,
            createSettingActionItem(
                player,
                Material.BELL,
                "gui.user_settings.notification.display",
                "notification",
                notifyStatus,
                if (stats.visitorNotificationEnabled) "§a" else "§c",
                "toggle_notification",
                "gui.user_settings.cycle_action.toggle",
                glint = stats.visitorNotificationEnabled
            ),
            "toggle_notification",
        )
        inventory.setActionItem(
            11,
            createSettingActionItem(
                player,
                Material.WRITABLE_BOOK,
                "gui.user_settings.language.display",
                "language",
                languageName,
                "§f",
                "cycle_language",
                "gui.user_settings.cycle_action.next"
            ),
            "cycle_language",
        )
        inventory.setActionItem(
            12,
            createCriticalVisibilityItem(player, stats.criticalSettingsEnabled),
            "toggle_critical",
        )
        inventory.setActionItem(
            13,
            createSettingActionItem(
                player,
                Material.COMPASS,
                "gui.user_settings.tour_navigation.display",
                "tour_navigation",
                null,
                "§f",
                "cycle_tour_navigation",
                "gui.user_settings.cycle_action.toggle",
                TourNavigationMode.entries.map { mode ->
                    GuiMenuIconOption(
                        tr(player, "gui.user_settings.tour_navigation.mode.${mode.name.lowercase(Locale.ROOT)}"),
                        mode == stats.tourNavigationMode,
                        "§b",
                        "§7"
                    )
                }
            ),
            "cycle_tour_navigation",
        )
        if (GuiHelper.canGoBack(player)) {
            inventory.setActionItem(
                22,
                createActionItem(Material.REDSTONE, tr(player, "gui.common.return"), "return_command"),
                "return_command",
                role = GuiElementRole.CANCEL,
            )
        }

        return InventoryMenuView(
            size = 27,
            title = title,
            elements = inventory.elements(),
            standardFrame = false,
        )
    }

    private fun openWorldActionMenu(
        player: Player,
        worldData: WorldData,
        returnPage: Int,
        showBackButton: Boolean
    ) {
        if (routingService.shouldUseForm(player)) {
            if (openWorldActionsForm(player, worldData, returnPage, showBackButton)) {
                routingService.clearFormFailure(player)
                return
            }
            routingService.markFormFailure(player, "world_action_form_open_failed")
        }

        runtime.navigate(player, worldActionRoute(worldData.uuid, returnPage, showBackButton))
    }

    private fun handleRuntimeAction(context: MenuActionContext): MenuActionResult {
        val player = context.player
        val page = context.route.payload["page"]?.toIntOrNull() ?: 0
        val showBackButton = context.route.payload["back"]?.toBooleanStrictOrNull() ?: false
        return when (context.route.id) {
            PLAYER_WORLD_ROUTE -> when (context.actionId) {
                "capability_world" -> {
                    val capabilityId = context.payload["capability"]
                        ?: return MenuActionResult.Ignored
                    val worldUuid = context.payload["world"]
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return MenuActionResult.Rejected()
                    val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
                        ?: return MenuActionResult.Rejected()
                    val subject = playerWorldCapabilitySubject(player, page, showBackButton)
                    CCSystem.getAPI().getMenuCapabilityService().execute(
                        capabilityId,
                        player,
                        context.click,
                        attributes = playerWorldCapabilityAttributes(subject, worldData),
                    )
                }
                "capability_create" -> {
                    val capabilityId = context.payload["capability"]
                        ?: return MenuActionResult.Ignored
                    val subject = playerWorldCapabilitySubject(player, page, showBackButton)
                    CCSystem.getAPI().getMenuCapabilityService().execute(
                        capabilityId,
                        player,
                        context.click,
                        attributes = playerWorldCapabilityAttributes(subject),
                    )
                }
                "warp_world" -> {
                    val worldUuid = context.payload["world"]
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return MenuActionResult.Rejected()
                    val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
                        ?: return MenuActionResult.Rejected()
                    warpToWorld(player, worldData)
                    MenuActionResult.Success(MenuUpdate.Close)
                }
                "open_prev_page" ->
                    MenuActionResult.Success(MenuUpdate.Replace(playerWorldRoute(page - 1, showBackButton)))
                "open_next_page" ->
                    MenuActionResult.Success(MenuUpdate.Replace(playerWorldRoute(page + 1, showBackButton)))
                "start_creation" -> {
                    if (!WorldCreationChecks.checkSelfCreatePermission(player)) {
                        return MenuActionResult.Rejected()
                    }
                    val session = plugin.creationSessionManager.startSession(player.uniqueId)
                    session.isDialogMode = false
                    Bukkit.getScheduler().runTask(
                        plugin,
                        Runnable { plugin.creationGui.openTypeSelection(player) },
                    )
                    MenuActionResult.Success(MenuUpdate.Close)
                }
                "open_settings" ->
                    MenuActionResult.Success(MenuUpdate.Navigate(settingsRoute(showBackButton, page)))
                "open_pending_interactions" -> {
                    plugin.pendingInteractionGui.open(
                        player = player,
                        page = 0,
                        returnPage = page,
                        showBackButton = showBackButton,
                        fromBedrockMenu = true,
                    )
                    MenuActionResult.Success(MenuUpdate.None)
                }
                "return_command" -> {
                    performConfiguredReturn(player)
                    MenuActionResult.Success(MenuUpdate.Close)
                }
                else -> MenuActionResult.Ignored
            }
            WORLD_ACTION_ROUTE -> {
                val worldUuid = context.route.payload["world"]?.let(UUID::fromString)
                    ?: return MenuActionResult.Rejected()
                val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
                    ?: return MenuActionResult.Success(
                        MenuUpdate.Replace(playerWorldRoute(page, showBackButton)),
                    )
                when (context.actionId) {
                    "warp_world" -> {
                        warpToWorld(player, worldData)
                        MenuActionResult.Success(MenuUpdate.Close)
                    }
                    "cycle_publish" -> {
                        cyclePublishLevel(player, worldData)
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    }
                    "toggle_archive" -> {
                        toggleArchiveState(player, worldData) {
                            val refreshed = plugin.worldConfigRepository.findByUuid(worldUuid)
                            if (refreshed != null) {
                                runtime.replace(player, worldActionRoute(worldUuid, page, showBackButton))
                            } else {
                                runtime.replace(player, playerWorldRoute(page, showBackButton))
                            }
                        }
                        MenuActionResult.Success(MenuUpdate.Close)
                    }
                    "open_advanced_settings" -> {
                        plugin.worldSettingsGui.open(player, worldData, showBackButton)
                        MenuActionResult.Success(MenuUpdate.None)
                    }
                    "open_settings" ->
                        MenuActionResult.Success(MenuUpdate.Navigate(settingsRoute(showBackButton, page)))
                    "back_to_worlds" ->
                        MenuActionResult.Success(MenuUpdate.Back)
                    "return_command" -> {
                        performConfiguredReturn(player)
                        MenuActionResult.Success(MenuUpdate.Close)
                    }
                    "close_menu" -> MenuActionResult.Success(MenuUpdate.Close)
                    else -> MenuActionResult.Ignored
                }
            }
            SETTINGS_ROUTE -> {
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                when (context.actionId) {
                    "toggle_notification" -> {
                        stats.visitorNotificationEnabled = !stats.visitorNotificationEnabled
                        plugin.playerStatsRepository.save(stats)
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    }
                    "cycle_language" -> MenuActionResult.Success(MenuUpdate.Refresh)
                    "toggle_critical" -> {
                        stats.criticalSettingsEnabled = !stats.criticalSettingsEnabled
                        plugin.playerStatsRepository.save(stats)
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    }
                    "cycle_tour_navigation" -> {
                        stats.tourNavigationMode = nextTourNavigationMode(stats.tourNavigationMode)
                        plugin.playerStatsRepository.save(stats)
                        plugin.tourManager.refreshNavigation(player)
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    }
                    "back_to_worlds" ->
                        MenuActionResult.Success(MenuUpdate.Back)
                    "return_command" -> {
                        performConfiguredReturn(player)
                        MenuActionResult.Success(MenuUpdate.Close)
                    }
                    "close_menu" -> MenuActionResult.Success(MenuUpdate.Close)
                    else -> MenuActionResult.Ignored
                }
            }
            else -> MenuActionResult.Ignored
        }
    }

    private fun getAccessibleWorlds(player: Player): List<WorldData> {
        plugin.worldConfigRepository.loadAll()

        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val allWorlds = plugin.worldConfigRepository.findAll()
        val accessibleWorlds =
            allWorlds
                .filter {
                    it.owner == player.uniqueId ||
                        it.moderators.contains(player.uniqueId) ||
                        it.members.contains(player.uniqueId) ||
                        it.isArchived
                }
                .filter { it.owner == player.uniqueId || !it.isArchived }

        val orderedWorlds =
            stats.worldDisplayOrder.mapNotNull { uuid ->
                accessibleWorlds.find { it.uuid == uuid }
            }

        val unorderedWorlds =
            accessibleWorlds
                .filter { !stats.worldDisplayOrder.contains(it.uuid) }
                .sortedWith(compareBy<WorldData> { it.isArchived }.thenByDescending { it.createdAt })

        return orderedWorlds + unorderedWorlds
    }

    private fun getCurrentManagedWorld(player: Player): WorldData? {
        return plugin.worldConfigRepository.findByWorldName(player.world.name)
    }

    private fun canAccessWorldSettings(player: Player, worldData: WorldData): Boolean {
        return player.hasPermission("myworldmanager.admin") ||
            worldData.owner == player.uniqueId ||
            worldData.moderators.contains(player.uniqueId) ||
            worldData.members.contains(player.uniqueId)
    }

    private fun canManagePublish(player: Player, worldData: WorldData): Boolean {
        return player.hasPermission("myworldmanager.admin") ||
            worldData.owner == player.uniqueId ||
            worldData.moderators.contains(player.uniqueId)
    }

    private fun canManageArchive(player: Player, worldData: WorldData): Boolean {
        return player.hasPermission("myworldmanager.admin") || worldData.owner == player.uniqueId
    }

    private fun warpToWorld(player: Player, worldData: WorldData) {
        if (worldData.isArchived) {
            player.sendMessage(tr(player, "messages.archive_access_denied"))
            return
        }

        val folderName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
        if (Bukkit.getWorld(folderName) == null) {
            runtime.close(player)
            player.sendMessage(tr(player, "messages.world_loading"))
            plugin.worldService.teleportToWorld(player, worldData.uuid) {
                completeWarpToWorld(player, worldData)
            }
            return
        }

        runtime.close(player)
        plugin.worldService.teleportToWorld(player, worldData.uuid) {
            completeWarpToWorld(player, worldData)
        }
    }

    private fun completeWarpToWorld(player: Player, worldData: WorldData) {
        player.sendMessage(tr(player, "messages.warp_success", mapOf("world" to worldData.name)))
        runtime.close(player)
    }

    private fun cyclePublishLevel(player: Player, worldData: WorldData) {
        if (MyWorldManagerApi.getWorldPublishPolicy().cyclePublishLevel(player, worldData)) {
            return
        }
        val levels = PublishLevel.values()
        val currentIndex = levels.indexOf(worldData.publishLevel).let { if (it < 0) 0 else it }
        worldData.publishLevel = levels[(currentIndex + 1) % levels.size]
        if (worldData.publishLevel == PublishLevel.PUBLIC) {
            worldData.publicAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        }
        plugin.worldConfigRepository.save(worldData)
        player.sendMessage(
            tr(
                player,
                "messages.publish_updated",
                mapOf("level" to publishLevelText(player, worldData.publishLevel))
            )
        )
    }

    private fun toggleArchiveState(player: Player, worldData: WorldData, onComplete: () -> Unit) {
        if (worldData.isArchived) {
            player.sendMessage(tr(player, "messages.unarchive_start"))
            plugin.worldService.unarchiveWorld(worldData.uuid).thenAccept { success ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (success) {
                        player.sendMessage(tr(player, "messages.unarchive_success"))
                    } else {
                        player.sendMessage(tr(player, "messages.unarchive_failed"))
                    }
                    onComplete()
                })
            }
        } else {
            player.sendMessage(tr(player, "messages.archive_start"))
            plugin.worldService.archiveWorld(worldData.uuid).thenAccept { success ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (success) {
                        player.sendMessage(tr(player, "messages.archive_success", mapOf("world" to worldData.name)))
                    } else {
                        player.sendMessage(tr(player, "messages.archive_failed"))
                    }
                    onComplete()
                })
            }
        }
    }

    private fun languageDisplay(player: Player, languageKey: String): String {
        val message = runCatching {
            plugin.languageManager.getMessage(player, "general.language.$languageKey")
        }.getOrNull()
        return if (message.isNullOrBlank()) languageKey else message
    }

    private fun statusText(player: Player, enabled: Boolean): String {
        return if (enabled) {
            tr(player, "messages.status_on")
        } else {
            tr(player, "messages.status_off")
        }
    }

    private fun dateFormatterFor(player: Player): DateTimeFormatter {
        val language = plugin.languageManager.resolveLocale(player).lowercase(Locale.ROOT)
        return if (language == "ja_jp") {
            DateTimeFormatter.ofPattern("yyyy年MM月dd日")
        } else {
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
        }
    }

    private fun materialToBedrockPath(material: Material?): String? {
        if (material == null || material.isAir) {
            return null
        }
        return materialPathOverrides[material] ?: "textures/items/${material.key.key}"
    }

    private fun performConfiguredReturn(player: Player) {
        CCSystem.getAPI().getMenuRuntimeService().back(player)
    }

    private fun playerWorldCapabilitySubject(
        player: Player,
        page: Int,
        showBackButton: Boolean,
    ): PlayerWorldCapabilitySubject {
        val worlds = getAccessibleWorlds(player)
        return PlayerWorldCapabilitySubject(
            player,
            player.uniqueId,
            player.name,
            worlds,
            showBackButton,
            playerWorldRoute(page, showBackButton),
        )
    }

    private fun playerWorldCapabilityAttributes(
        subject: PlayerWorldCapabilitySubject,
        worldData: WorldData? = null,
    ): Map<String, Any> = buildMap {
        put(PlayerWorldCapabilityContract.SUBJECT_ATTRIBUTE, subject)
        worldData?.let {
            put(PlayerWorldCapabilityContract.WORLD_ATTRIBUTE, it)
        }
    }

    private fun createWorldListItem(player: Player, worldData: WorldData): ItemStack {
        val item = ItemStack(worldData.icon)
        val meta = item.itemMeta ?: return item

        meta.displayName(
            plugin.languageManager.getComponent(
                player,
                "gui.common.world_item_name",
                mapOf("world" to worldData.name)
            )
        )

        val ownerName = PlayerNameUtil.getNameOrDefault(worldData.owner, tr(player, "general.unknown"))
        val publishLevelColor = tr(player, "publish_level.color.${worldData.publishLevel.name.lowercase()}")
        val publishLevelName = tr(player, "publish_level.${worldData.publishLevel.name.lowercase()}")
        val tagNames =
            if (worldData.tags.isNotEmpty()) {
                worldData.tags.joinToString(", ") { plugin.worldTagManager.getDisplayName(player, it) }
            } else {
                null
            }

        val now = LocalDate.now()
        val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val displayFormatter = dateFormatterFor(player)
        val expireDate =
            try {
                LocalDate.parse(worldData.expireDate, inputFormatter)
            } catch (_: Exception) {
                LocalDate.now().plusYears(1)
            }
        val daysRemaining = ChronoUnit.DAYS.between(now, expireDate)

        val expiresAtValue =
            if (expireDate.year < 2900) {
                if (daysRemaining < 0) {
                    meta.setEnchantmentGlintOverride(true)
                }
                tr(
                    player,
                    "gui.player_world.world_item.expires_value",
                    mapOf("days" to daysRemaining, "date" to displayFormatter.format(expireDate))
                )
            } else {
                null
            }

        if (worldData.isArchived) meta.setEnchantmentGlintOverride(true)

        val warpAction = tr(player, "gui.player_world.world_item.warp")
        // Bedrock uses the same semantic sections as the Java menu, with its own action text.
        meta.lore(CCSystem.getAPI().getLoreService().render(StructuredLore.blocks(
            *buildList {
                if (worldData.description.isNotBlank()) add(listOf(GuiLoreLine.UserText(worldData.description)))
                add(buildList {
                    add(GuiLoreLine.Data(tr(player, "gui.common.world_item.owner"), ownerName, "§f"))
                    add(GuiLoreLine.Data(tr(player, "gui.common.world_item.publish"), publishLevelName, publishLevelColor))
                    add(GuiLoreLine.Data(tr(player, "gui.common.world_item.favorite"), worldData.favorite, "§c"))
                    add(GuiLoreLine.Data(
                        tr(player, "gui.common.world_item.recent_visitors"),
                        tr(player, "gui.common.world_item.recent_visitors_value", mapOf("count" to worldData.recentVisitors.sum())),
                        "§a"
                    ))
                    if (tagNames != null) add(GuiLoreLine.Data(tr(player, "gui.common.world_item.tags"), tagNames, "§e"))
                })
                val lifecycle = buildList {
                    if (expiresAtValue != null) add(GuiLoreLine.Data(tr(player, "gui.player_world.world_item.expires_at"), expiresAtValue, "§f"))
                    if (worldData.isArchived) add(GuiLoreLine.Warning(tr(player, "gui.player_world.world_item.expired")))
                }
                if (lifecycle.isNotEmpty()) add(lifecycle)
                add(listOf(GuiLoreActions.singleClick(plugin.languageManager, player, warpAction)))
            }.toTypedArray()
        )))

        item.itemMeta = meta
        return item
    }

    private fun createCreationButtonItem(player: Player): ItemStack {
        val item = ItemStack(Material.NETHER_STAR)
        val meta = item.itemMeta ?: return item
        meta.displayName(plugin.languageManager.getComponent(player, "gui.player_world.creation_button.display"))
        meta.lore(CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(listOf(
            com.awabi2048.ccsystem.api.gui.GuiLoreBlock(
                plugin.languageManager.getMessageList(player, "gui.player_world.creation_button.description").map(GuiLoreLine::Text)
            ),
            com.awabi2048.ccsystem.api.gui.GuiLoreBlock(listOf(GuiLoreActions.singleClick(
                plugin.languageManager,
                player,
                tr(player, "gui.player_world.creation_button.action")
            )))
        ))))
        item.itemMeta = meta
        return item
    }

    private fun createCreationUnavailableButtonItem(player: Player, reason: CreationBlockReason): ItemStack {
        val item = ItemStack(Material.BARRIER)
        val meta = item.itemMeta ?: return item
        meta.displayName(plugin.languageManager.getComponent(player, reason.displayKey))
        meta.lore(GuiItemFactory.menuLore(plugin.languageManager.getMessageList(player, reason.loreKey).map(GuiLoreLine::Text)))
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES)
        item.itemMeta = meta
        return item
    }

    private fun creationBlockReason(
        player: Player,
        currentCreateCount: Int,
        maxSlot: Int,
        bypassLimits: Boolean
    ): CreationBlockReason? {
        // 作成権限は、運営トグルや枠不足より先に本人へ表示する基本要件として扱う。
        if (!PermissionManager.checkPermission(player, PermissionManager.WORLD_CREATE)) {
            return CreationBlockReason.NO_PERMISSION
        }
        if (!WorldCreationChecks.check(player, notify = false)) return CreationBlockReason.POLICY_DENIED
        if (bypassLimits) return null
        if (MyWorldManagerApi.isWorldSlotSystemEnabled() && currentCreateCount >= maxSlot) {
            return CreationBlockReason.NO_SLOT
        }
        return null
    }

    private fun createStatsButtonItem(
        player: Player,
        currentCreateCount: Int,
        maxSlot: Int,
        worldPoint: Int
    ): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? org.bukkit.inventory.meta.SkullMeta ?: return item
        meta.owningPlayer = player
        val playerName = PlayerNameUtil.getNameOrDefault(player.uniqueId, tr(player, "general.unknown"))
        val bypassLimits = PermissionManager.canBypassWorldLimits(player)
        val pendingCount = plugin.pendingDecisionManager.getPendingCount(player.uniqueId)
        meta.displayName(
            plugin.languageManager.getComponent(
                player,
                "gui.player_world.stats_button.display",
                mapOf("player" to playerName)
            )
        )
        meta.lore(CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(buildList {
            if (MyWorldManagerApi.isWorldPointEconomyEnabled()) {
                add(com.awabi2048.ccsystem.api.gui.GuiLoreBlock(listOf(
                    GuiLoreLine.Data(tr(player, "gui.player_world.stats_button.points_label"), worldPoint, "§6")
                )))
            }
            if (MyWorldManagerApi.isWorldSlotSystemEnabled()) {
                add(com.awabi2048.ccsystem.api.gui.GuiLoreBlock(listOf(
                    if (bypassLimits) {
                        GuiLoreLine.Data(tr(player, "gui.player_world.stats_button.world_count_label"), currentCreateCount, "§a")
                    } else {
                        GuiLoreLine.Data(tr(player, "gui.player_world.stats_button.slots_label"), "$currentCreateCount/$maxSlot", "§a")
                    },
                    GuiLoreLine.Text(tr(player, if (bypassLimits) "gui.player_world.stats_button.slots_bypass_description" else "gui.player_world.stats_button.slots_description"))
                )))
            } else {
                add(com.awabi2048.ccsystem.api.gui.GuiLoreBlock(listOf(
                    GuiLoreLine.Data(tr(player, "gui.player_world.stats_button.world_count_label"), currentCreateCount, "§a")
                )))
            }
            if (pendingCount > 0) {
                val latest = plugin.pendingDecisionManager.getLatestPendingCreatedAt(player.uniqueId)
                    ?.let {
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            .withZone(ZoneId.systemDefault())
                            .format(Instant.ofEpochMilli(it))
                    }
                    ?: tr(player, "gui.player_world.pending_button.none")
                add(com.awabi2048.ccsystem.api.gui.GuiLoreBlock(listOf(
                    GuiLoreLine.Data(
                        tr(player, "gui.player_world.pending_button.count_label"),
                        pendingCount,
                        "§e"
                    ),
                    GuiLoreLine.Data(
                        tr(player, "gui.player_world.pending_button.latest_label"),
                        latest,
                        "§b"
                    )
                )))
                add(com.awabi2048.ccsystem.api.gui.GuiLoreBlock(listOf(
                    GuiLoreActions.singleClick(
                        plugin.languageManager,
                        player,
                        tr(player, "gui.player_world.pending_button.action")
                    )
                )))
            }
        })))
        meta.setEnchantmentGlintOverride(pendingCount > 0)
        item.itemMeta = meta
        return item
    }

    private fun createCriticalVisibilityItem(player: Player, enabled: Boolean): ItemStack {
        val status = if (enabled) tr(player, "messages.status_visible") else tr(player, "messages.status_hidden")
        return createSettingActionItem(
            player,
            Material.RECOVERY_COMPASS,
            "gui.user_settings.critical_settings_visibility.display",
            "critical_settings_visibility",
            status,
            if (enabled) "§a" else "§7",
            "toggle_critical",
            "gui.user_settings.cycle_action.toggle"
        )
    }

    private fun createSettingActionItem(
        player: Player,
        material: Material,
        displayKey: String,
        setting: String,
        currentValue: String?,
        currentValueColor: String,
        actionId: String,
        actionKey: String,
        options: List<GuiMenuIconOption> = emptyList(),
        glint: Boolean? = null
    ): ItemStack {
        val prefix = "gui.user_settings.$setting.blocks"
        val item = GuiItemFactory.menuIcon(
            GuiMenuIconSpec(
                material,
                GuiNameSpec.Text(tr(player, displayKey), GuiNameStyle.DEFAULT),
                GuiElementRole.CONTENT,
                1,
                plugin.languageManager.getMessageList(player, "$prefix.description"),
                currentValue?.let {
                    listOf(GuiMenuIconData(tr(player, "$prefix.current_label"), it, currentValueColor))
                } ?: emptyList(),
                options,
                emptyList(),
                emptyList(),
                listOf(GuiLoreActions.menuSingleClick(plugin.languageManager, player, tr(player, actionKey))),
                glint
            ),
            null
        )
        return item
    }

    private fun nextTourNavigationMode(mode: TourNavigationMode): TourNavigationMode {
        return when (mode) {
            TourNavigationMode.BOSSBAR_ONLY -> TourNavigationMode.ALL
            TourNavigationMode.ALL -> TourNavigationMode.NONE
            TourNavigationMode.NONE -> TourNavigationMode.BOSSBAR_ONLY
        }
    }

    private fun createActionItem(
        material: Material,
        displayName: String,
        action: String,
        worldUuid: UUID? = null,
        lore: GuiLoreSpec = GuiLoreSpec.None
    ): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        meta.displayName(CCSystem.getAPI().getGuiElementService().name(displayName))

        if (lore != GuiLoreSpec.None) {
            meta.lore(CCSystem.getAPI().getLoreService().render(lore))
        }

        item.itemMeta = meta
        return item
    }

    private fun tr(player: Player, key: String, placeholders: Map<String, Any> = emptyMap()): String {
        return if (placeholders.isEmpty()) {
            plugin.languageManager.getMessage(player, key)
        } else {
            plugin.languageManager.getMessage(player, key, placeholders)
        }
    }

    private fun publishLevelText(player: Player, level: PublishLevel): String {
        return tr(player, "publish_level.${level.name.lowercase()}")
    }

    private fun publishDisplayText(player: Player, worldData: WorldData): String {
        return MyWorldManagerApi.getWorldPublishPolicy()
            .getPublishDisplayName(player, worldData, publishLevelText(player, worldData.publishLevel))
    }

    private fun worldStateText(player: Player, archived: Boolean): String {
        return if (archived) {
            tr(player, "gui.bedrock.world_action.status.archived")
        } else {
            tr(player, "gui.bedrock.world_action.status.active")
        }
    }

    private fun createDecorationItem(material: Material): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.empty())
        meta.isHideTooltip = true
        item.itemMeta = meta
        return item
    }

    private fun playerWorldRoute(page: Int, showBackButton: Boolean): MenuRoute =
        MenuRoute(
            RUNTIME_OWNER,
            PLAYER_WORLD_ROUTE,
            mapOf(
                "page" to page.coerceAtLeast(0).toString(),
                "back" to showBackButton.toString(),
            ),
        )

    private fun worldActionRoute(
        worldUuid: UUID,
        page: Int,
        showBackButton: Boolean,
    ): MenuRoute =
        MenuRoute(
            RUNTIME_OWNER,
            WORLD_ACTION_ROUTE,
            mapOf(
                "world" to worldUuid.toString(),
                "page" to page.coerceAtLeast(0).toString(),
                "back" to showBackButton.toString(),
            ),
        )

    private fun settingsRoute(showBackButton: Boolean, returnPage: Int): MenuRoute =
        MenuRoute(
            RUNTIME_OWNER,
            SETTINGS_ROUTE,
            mapOf(
                "page" to returnPage.coerceAtLeast(0).toString(),
                "back" to showBackButton.toString(),
            ),
        )

    private companion object {
        const val RUNTIME_OWNER = "myworldmanager"
        const val PLAYER_WORLD_ROUTE = "bedrock_player_world"
        const val WORLD_ACTION_ROUTE = "bedrock_world_action"
        const val SETTINGS_ROUTE = "bedrock_user_settings"
        val RUNTIME_ACTION_IDS = setOf(
            "capability_world",
            "capability_create",
            "warp_world",
            "open_prev_page",
            "open_next_page",
            "start_creation",
            "open_settings",
            "open_pending_interactions",
            "return_command",
            "cycle_publish",
            "toggle_archive",
            "open_advanced_settings",
            "back_to_worlds",
            "close_menu",
            "toggle_notification",
            "cycle_language",
            "toggle_critical",
            "cycle_tour_navigation",
        )
    }

    private enum class CreationBlockReason(val displayKey: String, val loreKey: String) {
        POLICY_DENIED(
            "gui.player_world.creation_unavailable.policy_denied.display",
            "gui.player_world.creation_unavailable.policy_denied.lore"
        ),
        NO_SLOT(
            "gui.player_world.creation_unavailable.no_slot.display",
            "gui.player_world.creation_unavailable.no_slot.lore"
        ),
        NO_PERMISSION(
            "gui.player_world.creation_unavailable.no_permission.display",
            "gui.player_world.creation_unavailable.no_permission.lore"
        )
    }
}
