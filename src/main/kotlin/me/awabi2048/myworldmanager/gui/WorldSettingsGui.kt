package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiAdminKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiBedrockKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiCommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiPortalKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiSettingsKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldPublishLevelKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldRoleKeys

import me.awabi2048.myworldmanager.util.descriptionLine
import me.awabi2048.myworldmanager.util.warningLine
import me.awabi2048.myworldmanager.util.dangerLine


import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiInteractionGuidance
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuCapabilityInvocationSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuActionIntent
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryOption
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiMenuDisplaySpec
import com.awabi2048.ccsystem.api.gui.GuiStructuredMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiValueTone
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuActionSafety
import com.awabi2048.ccsystem.api.gui.MenuGesture
import com.awabi2048.ccsystem.api.gui.MenuInteraction
import com.awabi2048.ccsystem.api.gui.MenuCloseReason
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.withCapabilityComposition
import com.awabi2048.ccsystem.api.gui.copyWithPresentationSemantics
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuSoundPolicy
import com.awabi2048.ccsystem.api.gui.MenuSoundPresets
import com.awabi2048.ccsystem.api.gui.MenuRuntimeActions
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import com.awabi2048.ccsystem.api.gui.MenuViewCategory
import com.awabi2048.ccsystem.api.gui.PlayerInventoryInteraction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.extension.WorldSettingsAction
import me.awabi2048.myworldmanager.api.extension.WorldSettingsRestriction
import me.awabi2048.myworldmanager.api.extension.WorldSettingsStateContext
import me.awabi2048.myworldmanager.api.extension.MemberManagementCapabilityContract
import me.awabi2048.myworldmanager.api.extension.WorldSettingsCapabilityPlacements
import me.awabi2048.myworldmanager.model.PendingInteractionType
import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.service.BorderResetSpawnService
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PermissionManager
import me.awabi2048.myworldmanager.util.WorldRuntimePolicies
import net.kyori.adventure.text.Component
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

private enum class WorldSettingsDisplayMode {
        DEFAULT,
        COMPACT_LIMITED_OWNER,
        READ_ONLY_DEFAULT,
        READ_ONLY_COMPACT,
}

private val WORLD_SETTINGS_CONFIRMATION_SCREENS = setOf(
        WorldSettingsRuntimeScreen.MEMBER_PENDING_INVITE_CANCEL_CONFIRM,
        WorldSettingsRuntimeScreen.MEMBER_REMOVE_CONFIRM,
        WorldSettingsRuntimeScreen.MEMBER_TRANSFER_CONFIRM,
        WorldSettingsRuntimeScreen.VISITOR_KICK_CONFIRM,
        WorldSettingsRuntimeScreen.EXPANSION_CONFIRM,
        WorldSettingsRuntimeScreen.EXPANSION_STEP_BACK_CONFIRM,
        WorldSettingsRuntimeScreen.RESET_EXPANSION_CONFIRM,
        WorldSettingsRuntimeScreen.RESET_EXPANSION_SPAWN_UNSAFE_CONFIRM,
        WorldSettingsRuntimeScreen.DELETE_WORLD_CONFIRM,
        WorldSettingsRuntimeScreen.DELETE_WORLD_FINAL_CONFIRM,
        WorldSettingsRuntimeScreen.ARCHIVE_CONFIRM,
        WorldSettingsRuntimeScreen.UNARCHIVE_CONFIRM,
        WorldSettingsRuntimeScreen.ARCHIVE_FROM_CRITICAL_CONFIRM,
)

class WorldSettingsGui(private val plugin: MyWorldManager) {
        private val borderResetSpawnService = BorderResetSpawnService()
        private val runtime = CCSystem.getAPI().getMenuRuntimeService()
        init {
                runtime.register(
                        InventoryMenuDefinition(
                                owner = RUNTIME_OWNER,
                                id = RUNTIME_ROUTE,
                                renderer = { context ->
                                        renderRuntimeRoute(context.player, context.route)
                                },
                                actions = mapOf(
                                        ACTION_RUNTIME_DISPATCH to
                                                MenuActionHandler { context ->
                                                        plugin.worldSettingsListener.handleRuntimeInventoryClick(
                                                                context.player,
                                                                context.click,
                                                                context.item,
                                                                context.slot,
                                                                WorldSettingsRuntimeContext(
                                                                        screen = runtimeScreen(context.route)
                                                                                ?: return@MenuActionHandler MenuActionResult.Ignored,
                                                                        worldUuid = runtimeWorldUuid(context.route),
                                                                        page = runtimePage(context.route),
                                                                        targetUuid = runtimeTargetUuid(context.route),
                                                                        decisionId = runtimeDecisionId(context.route),
                                                                        operation = runtimeOperation(context.payload),
                                                                        actionPayload = context.payload,
                                                                ),
                                                        )
                                                },
                                ),
                                onClose = { context ->
                                        plugin.worldSettingsListener.onRuntimeInventoryClose(
                                                context.player,
                                                context.reason,
                                        )
                                },
                                openSoundResolver = { route ->
                                        if (runtimeScreen(route) in WORLD_SETTINGS_CONFIRMATION_SCREENS) {
                                                MenuSoundPresets.CONFIRMATION_OPEN
                                        } else {
                                                MenuSoundPolicy.Default
                                        }
                                },
                        ),
                )
                runtime.register(
                        InventoryMenuDefinition(
                                owner = RUNTIME_OWNER,
                                id = RUNTIME_SELECTION_ROUTE,
                                renderer = { context ->
                                        renderRuntimeRoute(context.player, context.route)
                                                .copy(
                                                        playerInventoryInteraction =
                                                                PlayerInventoryInteraction.SELECTION
                                                )
                                },
                                actions = mapOf(
                                        ACTION_RUNTIME_DISPATCH to
                                                MenuActionHandler { context ->
                                                        plugin.worldSettingsListener.handleRuntimeInventoryClick(
                                                                context.player,
                                                                context.click,
                                                                context.item,
                                                                context.slot,
                                                                WorldSettingsRuntimeContext(
                                                                        screen = WorldSettingsRuntimeScreen.ICON_SELECTION,
                                                                        worldUuid = runtimeWorldUuid(context.route),
                                                                        page = runtimePage(context.route),
                                                                        targetUuid = runtimeTargetUuid(context.route),
                                                                        decisionId = runtimeDecisionId(context.route),
                                                                        operation = runtimeOperation(context.payload),
                                                                        actionPayload = context.payload,
                                                                ),
                                                        )
                                                },
                                        MenuRuntimeActions.PLAYER_INVENTORY_CLICK to
                                                MenuActionHandler { context ->
                                                        plugin.worldSettingsIconSelectionService.select(
                                                                context.player,
                                                                context.item,
                                                        )
                                                },
                                ),
                                onClose = { context ->
                                        plugin.worldSettingsListener.onRuntimeInventoryClose(
                                                context.player,
                                                context.reason,
                                        )
                                },
                        ),
                )
                runtime.register(
                        InventoryMenuDefinition(
                                owner = RUNTIME_OWNER,
                                id = RUNTIME_MEMBER_MANAGEMENT_ROUTE,
                                renderer = { context ->
                                        val worldUuid =
                                                context.route.payload[ROUTE_WORLD_UUID]
                                                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                                                        ?: error("メンバー管理の対象ワールドがありません")
                                        val worldData =
                                                plugin.worldConfigRepository.findByUuid(worldUuid)
                                                        ?: error("メンバー管理の対象ワールドが見つかりません")
                                        val page =
                                                context.route.payload[ROUTE_PAGE]
                                                        ?.toIntOrNull()
                                                        ?.coerceAtLeast(0)
                                                        ?: 0
                                        renderMemberManagement(context.player, worldData, page)
                                },
                                actions = mapOf(
                                        ACTION_RUNTIME_DISPATCH to
                                                MenuActionHandler { context ->
                                                        val worldUuid =
                                                                context.route.payload[ROUTE_WORLD_UUID]
                                                                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                                                                        ?: return@MenuActionHandler MenuActionResult.Ignored
                                                        plugin.worldSettingsListener.handleRuntimeInventoryClick(
                                                                context.player,
                                                                context.click,
                                                                context.item,
                                                                context.slot,
                                                                WorldSettingsRuntimeContext(
                                                                        screen = WorldSettingsRuntimeScreen.MEMBER_MANAGEMENT,
                                                                        worldUuid = worldUuid,
                                                                        page = context.route.payload[ROUTE_PAGE]
                                                                                ?.toIntOrNull()
                                                                                ?.coerceAtLeast(0)
                                                                                ?: 0,
                                                                        operation = runtimeOperation(context.payload),
                                                                        actionPayload = context.payload,
                                                                ),
                                                        )
                                                },
                                ),
                                onClose = { context ->
                                        plugin.worldSettingsListener.onRuntimeInventoryClose(
                                                context.player,
                                                context.reason,
                                        )
                                },
                        ),
                )
        }

        internal fun route(worldUuid: UUID): MenuRoute =
                runtimeRoute(WorldSettingsRuntimeScreen.WORLD_SETTINGS, worldUuid)

        internal fun iconSelectionRoute(worldUuid: UUID): MenuRoute =
                route(worldUuid).copy(id = RUNTIME_SELECTION_ROUTE)

        internal fun memberManagementRoute(worldUuid: UUID, page: Int = 0): MenuRoute =
                MenuRoute(
                        RUNTIME_OWNER,
                        RUNTIME_MEMBER_MANAGEMENT_ROUTE,
                        mapOf(
                                ROUTE_WORLD_UUID to worldUuid.toString(),
                                ROUTE_PAGE to page.coerceAtLeast(0).toString(),
                        ),
                )

        internal fun runtimeRoute(
                screen: WorldSettingsRuntimeScreen,
                worldUuid: UUID,
                page: Int? = null,
                targetUuid: UUID? = null,
                decisionId: UUID? = null,
                arguments: Map<String, String> = emptyMap(),
        ): MenuRoute {
                val payload = mutableMapOf(
                        ROUTE_SCREEN to screen.name,
                        ROUTE_WORLD_UUID to worldUuid.toString(),
                )
                page?.let { payload[ROUTE_PAGE] = it.coerceAtLeast(0).toString() }
                targetUuid?.let { payload[ROUTE_TARGET_UUID] = it.toString() }
                decisionId?.let { payload[ROUTE_DECISION_ID] = it.toString() }
                payload.putAll(arguments)
                return MenuRoute(RUNTIME_OWNER, RUNTIME_ROUTE, payload)
        }

        internal fun expansionConfirmationRoute(
                worldUuid: UUID,
                direction: org.bukkit.block.BlockFace?,
                cost: Int,
        ): MenuRoute = runtimeRoute(
                WorldSettingsRuntimeScreen.EXPANSION_CONFIRM,
                worldUuid,
                arguments = mapOf(
                        ROUTE_EXPANSION_COST to cost.toString(),
                        ROUTE_EXPANSION_DIRECTION to (direction?.name ?: ROUTE_NULL_VALUE),
                ),
        )

        private inner class RuntimeItemBuffer(
                val size: Int,
                private val viewer: Player,
        ) {
                private val items = arrayOfNulls<GuiItemSpec>(size)
                private val elements = mutableMapOf<Int, MenuElement>()

                fun setItem(slot: Int, item: GuiItemSpec?) {
                        require(slot in items.indices) { "slot is outside Runtime buffer: $slot/$size" }
                        items[slot] = item
                        elements.remove(slot)
                }

                fun setElement(element: MenuElement) {
                        require(element.slot in items.indices) {
                                "slot is outside Runtime buffer: ${element.slot}/$size"
                        }
                        items[element.slot] = null
                        elements[element.slot] = element
                }

                fun setMenuEntry(
                        player: Player,
                        spec: com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec,
                ) {
                        val element = CCSystem.getAPI().getGuiElementService().menuEntry(player, spec)
                        setElement(element)
                }

                fun setDisplay(spec: GuiMenuDisplaySpec) {
                        setElement(CCSystem.getAPI().getGuiElementService().menuDisplay(spec))
                }

                fun setRuntimeOperation(
                        slot: Int,
                        item: GuiItemSpec,
                        operation: WorldSettingsRuntimeOperation,
                        gesture: MenuGesture =
                                MenuGesture.ANY,
                        payload: Map<String, String> = emptyMap(),
                        sounds: com.awabi2048.ccsystem.api.gui.MenuActionSoundPolicy? = null,
                         actionLabel: String = runtimeActionLabel(operation),
                ) {
                        setElement(
                                CCSystem.getAPI().getGuiElementService().menuStructuredEntry(
                                         viewer,
                                        GuiStructuredMenuEntrySpec(
                                                slot = slot,
                                                item = item.copy(role = when (operation) {
                                        WorldSettingsRuntimeOperation.BACK -> GuiElementRole.BACK
                                        WorldSettingsRuntimeOperation.PAGE -> GuiElementRole.NAVIGATION
                                        WorldSettingsRuntimeOperation.CONFIRM -> GuiElementRole.CONFIRM
                                        WorldSettingsRuntimeOperation.CANCEL -> GuiElementRole.CANCEL
                                        else -> GuiElementRole.ACTION
                                                }),
                                                actions = listOf(
                                                        menuGestureAction(
                                                                actionId = "dispatch",
                                                                gesture = gesture,
                                                                label = actionLabel,
                                                                payload = mapOf(
                                                                        ROUTE_OPERATION to operation.name,
                                                                ) + payload,
                                                                safety = runtimeOperationSafety(operation),
                                                                reversibleContract = when (operation) {
                                                                        WorldSettingsRuntimeOperation.CYCLE_PUBLISH -> MwmMenuActionSemantics.contract("world-publish")
                                                                        WorldSettingsRuntimeOperation.TOGGLE_NOTIFICATION -> MwmMenuActionSemantics.contract("world-notification")
                                                                        else -> null
                                                                },
                                                        ),
                                                ),
                                                sounds = sounds,
                                        ),
                                ),
                        )
                }

                fun bindRuntimeOperation(
                        slot: Int,
                        operation: WorldSettingsRuntimeOperation,
                        gesture: MenuGesture =
                                MenuGesture.ANY,
                        payload: Map<String, String> = emptyMap(),
                        sounds: com.awabi2048.ccsystem.api.gui.MenuActionSoundPolicy? = null,
                         actionLabel: String = runtimeActionLabel(operation),
                ) {
                        val item = requireNotNull(getItem(slot)) {
                                "Runtime operation item is missing: $slot/$operation"
                        }
                        setRuntimeOperation(
                                slot,
                                item,
                                operation,
                                gesture,
                                payload,
                                sounds,
                                actionLabel,
                        )
                }

                fun bindConfirmation(
                        confirmOperation: WorldSettingsRuntimeOperation =
                                WorldSettingsRuntimeOperation.CONFIRM,
                        cancelOperation: WorldSettingsRuntimeOperation =
                                WorldSettingsRuntimeOperation.CANCEL,
                        confirmSlot: Int = 20,
                        cancelSlot: Int = 24,
                ) {
                        bindRuntimeOperation(confirmSlot, confirmOperation)
                        bindRuntimeOperation(cancelSlot, cancelOperation)
                }

                fun getItem(slot: Int): GuiItemSpec? =
                        items.getOrNull(slot) ?: elements[slot]?.let { null }

                fun isOccupied(slot: Int): Boolean =
                        items.getOrNull(slot) != null || elements.containsKey(slot)

                fun clear() {
                        items.fill(null)
                        elements.clear()
                }

                fun applyStandardFrame() {
                        val black = decorationSpec(Material.BLACK_STAINED_GLASS_PANE)
                        val gray = decorationSpec(Material.GRAY_STAINED_GLASS_PANE)
                        for (slot in items.indices) {
                                items[slot] = when {
                                        slot < 9 || slot >= size - 9 -> black
                                        else -> gray
                                }
                        }
                }

                fun elements(): List<MenuElement> =
                        items.indices.mapNotNull { slot ->
                                elements[slot]
                                        ?: items[slot]?.let { item ->
                                                CCSystem.getAPI().getGuiElementService().menuDisplay(
                                                        GuiMenuDisplaySpec(slot, item),
                                                )
                                        }
                        }

                private fun runtimeOperationSafety(operation: WorldSettingsRuntimeOperation): MenuActionSafety = when (operation) {
                        WorldSettingsRuntimeOperation.BACK,
                        WorldSettingsRuntimeOperation.TOUR,
                        WorldSettingsRuntimeOperation.MANAGE_MEMBERS,
                        WorldSettingsRuntimeOperation.OPEN_ENVIRONMENT,
                        WorldSettingsRuntimeOperation.OPEN_CRITICAL,
                        WorldSettingsRuntimeOperation.MANAGE_VISITORS,
                        WorldSettingsRuntimeOperation.MANAGE_PORTALS,
                        WorldSettingsRuntimeOperation.CANCEL,
                        WorldSettingsRuntimeOperation.PAGE -> MenuActionSafety.NAVIGATION_ONLY
                        WorldSettingsRuntimeOperation.EDIT_INFO,
                        WorldSettingsRuntimeOperation.SELECT_ICON,
                        WorldSettingsRuntimeOperation.EDIT_TAGS,
                        WorldSettingsRuntimeOperation.EDIT_ANNOUNCEMENT,
                        WorldSettingsRuntimeOperation.EXPAND_DIRECTION,
                        WorldSettingsRuntimeOperation.MEMBER_OWNER_RESET,
                        WorldSettingsRuntimeOperation.INVITE_MEMBER -> MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE
                        WorldSettingsRuntimeOperation.CYCLE_PUBLISH,
                        WorldSettingsRuntimeOperation.TOGGLE_NOTIFICATION -> MenuActionSafety.REVERSIBLE
                        WorldSettingsRuntimeOperation.WARP -> MenuActionSafety.EXTERNAL_SIDE_EFFECT
                        WorldSettingsRuntimeOperation.EXPAND_AUTOMATIC,
                        WorldSettingsRuntimeOperation.EXPANSION_STEP_BACK,
                        WorldSettingsRuntimeOperation.RESET_EXPANSION,
                        WorldSettingsRuntimeOperation.ARCHIVE,
                        WorldSettingsRuntimeOperation.DELETE_WORLD,
                        WorldSettingsRuntimeOperation.VISITOR,
                        WorldSettingsRuntimeOperation.MEMBER,
                        WorldSettingsRuntimeOperation.PENDING_INVITE,
                        WorldSettingsRuntimeOperation.PENDING_REQUEST -> MenuActionSafety.CONFIRM_ENTRY
                        WorldSettingsRuntimeOperation.CONFIRM,
                        WorldSettingsRuntimeOperation.SET_SPAWN,
                        WorldSettingsRuntimeOperation.PORTAL -> MenuActionSafety.IRREVERSIBLE
                        WorldSettingsRuntimeOperation.EXPAND -> error("EXPAND requires click-specific safety")
                }

                private fun runtimeActionLabel(operation: WorldSettingsRuntimeOperation): String =
                        when (operation) {
                                WorldSettingsRuntimeOperation.BACK -> plugin.languageManager.getMessage(viewer, CommonKeys.GUI_COMMON_RETURN)
                                WorldSettingsRuntimeOperation.CONFIRM -> plugin.languageManager.getMessage(viewer, CommonKeys.GUI_COMMON_CONFIRM)
                                WorldSettingsRuntimeOperation.CANCEL -> plugin.languageManager.getMessage(viewer, CommonKeys.GUI_COMMON_CANCEL)
                                WorldSettingsRuntimeOperation.PAGE -> plugin.languageManager.getMessage(viewer, CommonKeys.GUI_COMMON_ACTION_PAGE)
                                WorldSettingsRuntimeOperation.TOUR -> plugin.languageManager.getMessage(viewer, MyworldGuiCommonKeys.GUI_TOUR_WORLDMENU_ACTION_OPEN)
                                WorldSettingsRuntimeOperation.EDIT_INFO -> plugin.languageManager.getMessage(viewer, MyworldGuiSettingsKeys.GUI_SETTINGS_INFO_ACTION_OPEN_EDITOR)
                                WorldSettingsRuntimeOperation.SELECT_ICON -> plugin.languageManager.getMessage(viewer, MyworldGuiSettingsKeys.GUI_SETTINGS_ICON_ACTION_START_SELECTION)
                                WorldSettingsRuntimeOperation.SET_SPAWN -> plugin.languageManager.getMessage(viewer, MyworldGuiSettingsKeys.GUI_SETTINGS_SPAWN_ACTION_SET_BOTH)
                                WorldSettingsRuntimeOperation.EXPAND -> plugin.languageManager.getMessage(viewer, MyworldGuiSettingsKeys.GUI_SETTINGS_EXPAND_ACTION_OPEN_MENU)
                                WorldSettingsRuntimeOperation.CYCLE_PUBLISH -> plugin.languageManager.getMessage(viewer, MyworldGuiCommonKeys.GUI_COMMON_ACTION_CYCLE)
                                WorldSettingsRuntimeOperation.MANAGE_MEMBERS -> plugin.languageManager.getMessage(viewer, MyworldGuiSettingsKeys.GUI_SETTINGS_MEMBER_ACTION_OPEN_LIST)
                                WorldSettingsRuntimeOperation.EDIT_TAGS -> plugin.languageManager.getMessage(viewer, MyworldGuiSettingsKeys.GUI_SETTINGS_TAGS_ACTION_EDIT)
                                WorldSettingsRuntimeOperation.EDIT_ANNOUNCEMENT -> plugin.languageManager.getMessage(viewer, MyworldGuiSettingsKeys.GUI_SETTINGS_ANNOUNCEMENT_ACTION_SET_MESSAGE)
                                WorldSettingsRuntimeOperation.TOGGLE_NOTIFICATION -> plugin.languageManager.getMessage(viewer, MyworldGuiSettingsKeys.GUI_SETTINGS_NOTIFICATION_ACTION_TOGGLE)
                                WorldSettingsRuntimeOperation.OPEN_ENVIRONMENT -> plugin.languageManager.getMessage(viewer, MyworldGuiSettingsKeys.GUI_SETTINGS_ENVIRONMENT_ACTION_OPEN)
                                WorldSettingsRuntimeOperation.OPEN_CRITICAL -> plugin.languageManager.getMessage(viewer, MyworldGuiSettingsKeys.GUI_SETTINGS_CRITICAL_ACTION_OPEN)
                                WorldSettingsRuntimeOperation.MANAGE_VISITORS -> plugin.languageManager.getMessage(viewer, MyworldGuiSettingsKeys.GUI_SETTINGS_VISITORS_ACTION_OPEN)
                                WorldSettingsRuntimeOperation.MANAGE_PORTALS -> plugin.languageManager.getMessage(viewer, MyworldGuiSettingsKeys.GUI_SETTINGS_PORTALS_ACTION_OPEN)
                                else -> plugin.languageManager.getMessage(viewer, CommonKeys.GUI_COMMON_ACTION_OPEN)
                        }

                        private fun decorationSpec(material: Material): GuiItemSpec =
                                GuiItemSpec(
                                        material = material,
                                        name = GuiNameSpec.Empty,
                                        lore = GuiLoreSpec.None,
                                        role = GuiElementRole.DECORATION,
                                        amount = 1,
                                )

        }

        private data class MemberManagementEntry(
                val playerUuid: UUID,
                val role: String? = null,
                val pendingDecisionId: UUID? = null,
                val pendingCreatedAt: Long? = null,
                val pendingType: PendingInteractionType? = null
        )

        fun open(
                player: Player,
                worldData: WorldData,
                showBackButton: Boolean? = null,
                isPlayerWorldFlow: Boolean? = null,
                parentShowBackButton: Boolean? = null,
                replaceCurrent: Boolean = false
        ) {
                val lang = plugin.languageManager
                // セッションの更新
                if (showBackButton != null || isPlayerWorldFlow != null || parentShowBackButton != null) {
                        plugin.settingsSessionManager.updateSessionAction(
                                player,
                                worldData.uuid,
                                SettingsAction.VIEW_SETTINGS,
                                isGui = true,
                                isPlayerWorldFlow = isPlayerWorldFlow,
                                parentShowBackButton = parentShowBackButton
                        )
                        if (showBackButton != null) {
                            plugin.settingsSessionManager.getSession(player)?.showBackButton =
                                showBackButton
                        }
                } else {
                        plugin.settingsSessionManager.updateSessionAction(
                                player,
                                worldData.uuid,
                                SettingsAction.VIEW_SETTINGS,
                                isGui = true
                        )
                }

                val targetRoute = route(worldData.uuid)
                if (replaceCurrent) {
                        runtime.replace(player, targetRoute)
                } else {
                        runtime.navigate(player, targetRoute)
                }
        }

        private fun contractMenuActions(
                player: Player,
                worldData: WorldData,
                action: WorldSettingsAction,
                operation: WorldSettingsRuntimeOperation,
                actionTexts: List<String>,
        ): List<GuiMenuActionIntent> {
                val contract = plugin.worldSettingsActionService.contract(player, worldData, action)
                require(contract.options.size == actionTexts.size) {
                        "World settings action presentation mismatch: $action options=${contract.options.size} texts=${actionTexts.size}"
                }
                return contract.options.zip(actionTexts).map { (option, actionText) ->
                        menuGestureAction(
                                ACTION_RUNTIME_DISPATCH,
                                option.gesture,
                                actionText,
                                mapOf(ROUTE_OPERATION to operation.name),
                                enabled = contract.actionable,
                                safety = contractActionSafety(operation),
                        )
                }
        }

        private fun contractActionSafety(operation: WorldSettingsRuntimeOperation): MenuActionSafety = when (operation) {
                WorldSettingsRuntimeOperation.TOUR,
                WorldSettingsRuntimeOperation.MANAGE_MEMBERS,
                WorldSettingsRuntimeOperation.MANAGE_PORTALS -> MenuActionSafety.NAVIGATION_ONLY
                WorldSettingsRuntimeOperation.EDIT_INFO,
                WorldSettingsRuntimeOperation.SELECT_ICON,
                WorldSettingsRuntimeOperation.EDIT_ANNOUNCEMENT -> MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE
                WorldSettingsRuntimeOperation.SET_SPAWN -> MenuActionSafety.IRREVERSIBLE
                WorldSettingsRuntimeOperation.WARP -> MenuActionSafety.EXTERNAL_SIDE_EFFECT
                else -> error("Unsupported world settings action contract safety: $operation")
        }

        private fun renderWorldSettings(
                player: Player,
                worldData: WorldData,
        ): InventoryMenuView {
                // inspectionを含む描画時点で、公開操作を横取りするpolicyの可逆契約不足を検出します。
                plugin.worldPublishService.requireReversibleCycleContract(worldData)
                val lang = plugin.languageManager
                val title =
                        GuiHelper.inventoryTitle(
                                lang.getComponent(
                                        player,
                                        MyworldGuiSettingsKeys.GUI_SETTINGS_TITLE,
                                        mapOf("world" to worldData.name)
                                )
                        )
                // 権限判定
                val currentSession = plugin.settingsSessionManager.getSession(player)
                val isOwner = worldData.owner == player.uniqueId || currentSession?.isAdminFlow == true
                val isModerator = worldData.moderators.contains(player.uniqueId)
                val isMember = worldData.members.contains(player.uniqueId)
                val restriction = MyWorldManagerApi.getWorldSettingsStatePolicies()
                        .firstNotNullOfOrNull { policy ->
                                policy.restriction(
                                        WorldSettingsStateContext(
                                                player,
                                                worldData,
                                                isOwner,
                                                isModerator,
                                                isMember,
                                        ),
                                )
                        }
                val presentationMode = when (restriction) {
                        WorldSettingsRestriction.SUBMISSION_LOCKED ->
                                if (isOwner) {
                                        WorldSettingsDisplayMode.COMPACT_LIMITED_OWNER
                                } else {
                                        WorldSettingsDisplayMode.READ_ONLY_COMPACT
                                }
                        WorldSettingsRestriction.IMPORTED_ARCHIVE ->
                                if (isMember) {
                                        WorldSettingsDisplayMode.READ_ONLY_COMPACT
                                } else {
                                        WorldSettingsDisplayMode.READ_ONLY_DEFAULT
                                }
                        null -> WorldSettingsDisplayMode.DEFAULT
                }
                val ownerActionsAllowed =
                        isOwner && presentationMode == WorldSettingsDisplayMode.DEFAULT
                val hasManagePermission =
                        (isOwner || isModerator) &&
                                presentationMode == WorldSettingsDisplayMode.DEFAULT
                val canManageTour =
                        (isOwner || isModerator || isMember) &&
                                presentationMode == WorldSettingsDisplayMode.DEFAULT
                val isBedrock = plugin.playerPlatformResolver.isBedrock(player)

                val isMemberLayout = isMember && !hasManagePermission
                val useModeratorCenteredLayout = isModerator && !isOwner

                val inventorySize = when (presentationMode) {
                        WorldSettingsDisplayMode.COMPACT_LIMITED_OWNER,
                        WorldSettingsDisplayMode.READ_ONLY_COMPACT -> 45
                        WorldSettingsDisplayMode.READ_ONLY_DEFAULT -> 54
                        WorldSettingsDisplayMode.DEFAULT -> if (isMemberLayout) 45 else 54
                }
                val bottomRowStartSlot = inventorySize - 9
                // ワールド情報はヘッダー中央、戻るボタンはフッター中央へ固定して、ツアー/Chanpon側と視線を揃える。
                val backButtonSlot = bottomRowStartSlot + 4
                val worldInfoSlot = 4
                val tourSettingSlot = if (isMemberLayout) 42 else 47

                val infoSettingSlot = if (useModeratorCenteredLayout) 21 else 19
                val iconSettingSlot = if (useModeratorCenteredLayout) 22 else 20
                val spawnSettingSlot = if (useModeratorCenteredLayout) 23 else 21
                val tagsSettingSlot = if (useModeratorCenteredLayout) 30 else 28
                val announcementSettingSlot = if (useModeratorCenteredLayout) 31 else 29
                val notificationSettingSlot = if (useModeratorCenteredLayout) 32 else 30

                val inventory = RuntimeItemBuffer(inventorySize, player)

                // 背景 (黒の板ガラス)
                val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
                inventory.applyStandardFrame()

                // 戻るボタン
                if (GuiHelper.canGoBack(player)) {
                        inventory.setElement(
                                CCSystem.getAPI().getGuiElementService().backEntry(
                                        player,
                                        backButtonSlot,
                                        plugin.menuConfigManager.getIconMaterial("world_settings", "back", Material.REDSTONE),
                                )
                        )
                }

                if (canManageTour) {
                        inventory.setMenuEntry(
                                player,
                                GuiMenuEntrySpec(
                                        slot = tourSettingSlot,
                                        material = Material.PALE_OAK_BOAT,
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(
                                                lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_WORLDMENU_DISPLAY),
                                                GuiNameStyle.DEFAULT,
                                        ),
                                        role = GuiElementRole.ACTION,
                                        description = lang.getMessageList(player, MyworldGuiCommonKeys.GUI_TOUR_WORLDMENU_BLOCKS_DESCRIPTION),
                                        actions = contractMenuActions(
                                                player,
                                                worldData,
                                                WorldSettingsAction.MANAGE_TOUR,
                                                WorldSettingsRuntimeOperation.TOUR,
                                                listOf(lang.getMessage(player, MyworldGuiCommonKeys.GUI_TOUR_WORLDMENU_ACTION_OPEN)),
                                        ),
                                        sounds = plugin.worldSettingsActionService
                                                .contract(player, worldData, WorldSettingsAction.MANAGE_TOUR).sounds,
                                ),
                        )
                }

                // ワールド名・説明変更
                if (hasManagePermission) {
                        inventory.setMenuEntry(
                                player,
                                GuiMenuEntrySpec(
                                        slot = infoSettingSlot,
                                        material = plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "info",
                                                Material.NAME_TAG,
                                        ),
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_INFO_DISPLAY),
                                                GuiNameStyle.DEFAULT,
                                        ),
                                        role = GuiElementRole.ACTION,
                                        description = lang.getMessageList(player, MyworldGuiSettingsKeys.GUI_SETTINGS_INFO_BLOCKS_SUMMARY),
                                        actions = contractMenuActions(
                                                player,
                                                worldData,
                                                WorldSettingsAction.EDIT_INFO,
                                                WorldSettingsRuntimeOperation.EDIT_INFO,
                                                listOf(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_INFO_ACTION_OPEN_EDITOR)),
                                        ),
                                        sounds = plugin.worldSettingsActionService
                                                .contract(player, worldData, WorldSettingsAction.EDIT_INFO).sounds,
                                ),
                        )
                }

                // Check if player is in the world for restricted settings
                val targetWorldName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
                val isInWorld =
                        MyWorldManagerApi.getWorldService()?.isPlayerInWorld(player, worldData) == true
                val warningLore =
                        if (!isInWorld)
                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_COMMON_MUST_BE_IN_WORLD)
                        else null

                // アイコン変更
                if (hasManagePermission) {
                        inventory.setMenuEntry(
                                player,
                                GuiMenuEntrySpec(
                                        slot = iconSettingSlot,
                                        material = plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "icon",
                                                Material.ANVIL,
                                        ),
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_ICON_DISPLAY),
                                                GuiNameStyle.DEFAULT,
                                        ),
                                        role = GuiElementRole.ACTION,
                                        description = lang.getMessageList(player, MyworldGuiSettingsKeys.GUI_SETTINGS_ICON_BLOCKS_DESCRIPTION),
                                        actions = contractMenuActions(
                                                player,
                                                worldData,
                                                WorldSettingsAction.SELECT_ICON,
                                                WorldSettingsRuntimeOperation.SELECT_ICON,
                                                listOf(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_ICON_ACTION_START_SELECTION)),
                                        ),
                                        sounds = plugin.worldSettingsActionService
                                                .contract(player, worldData, WorldSettingsAction.SELECT_ICON).sounds,
                                ),
                        )
                }

                // スポーン位置変更
                if (hasManagePermission) {
                        val spawnActions = if (isBedrock) {
                                listOf(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_SPAWN_ACTION_SET_BOTH))
                        } else {
                                listOf(
                                        lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_SPAWN_ACTION_SET_GUEST),
                                        lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_SPAWN_ACTION_SET_MEMBER),
                                )
                        }
                        inventory.setMenuEntry(
                                player,
                                GuiMenuEntrySpec(
                                        slot = spawnSettingSlot,
                                        material = plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "spawn",
                                                Material.COMPASS,
                                        ),
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_SPAWN_DISPLAY),
                                                GuiNameStyle.DEFAULT,
                                        ),
                                        role = if (isInWorld) GuiElementRole.ACTION else GuiElementRole.CONTENT,
                                        description = lang.getMessageList(player, MyworldGuiSettingsKeys.GUI_SETTINGS_SPAWN_BLOCKS_DESCRIPTION),
                                        warnings = if (!isInWorld && warningLore != null) listOf(warningLore) else emptyList(),
                                        actions = contractMenuActions(
                                                player,
                                                worldData,
                                                WorldSettingsAction.SET_SPAWN,
                                                WorldSettingsRuntimeOperation.SET_SPAWN,
                                                spawnActions,
                                        ),
                                        sounds = plugin.worldSettingsActionService
                                                .contract(player, worldData, WorldSettingsAction.SET_SPAWN).sounds,
                                        // Bedrockは単一操作、Java版は左右で別操作のため共通仕様で条件判定します。
                                        interactionGuidance = GuiInteractionGuidance.SINGLE_ACTION_CLICK,
                                ),
                        )
                }

                // スロット23: ワールド拡張 (オーナーのみ)
                if (ownerActionsAllowed) {
                        val config = plugin.config
                        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                        val costsSection = config.getConfigurationSection("expansion.costs")
                        val maxLevel = costsSection?.getKeys(false)?.size ?: 3

                        val currentLevel = worldData.borderExpansionLevel
                        val cost = WorldRuntimePolicies.expansionCost(config, currentLevel + 1)
                        val borderInfo = buildCurrentBorderInfo(worldData, currentLevel)
                        val expansionData = buildList {
                                if (currentLevel != WorldData.EXPANSION_LEVEL_SPECIAL) {
                                        add(GuiMenuEntryData(
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_EXPAND_BLOCKS_CURRENT_LEVEL),
                                                "$currentLevel/$maxLevel",
                                                GuiValueTone.PRIMARY,
                                        ))
                                        if (currentLevel < maxLevel) {
                                                add(GuiMenuEntryData(
                                                        lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_EXPAND_BLOCKS_NEXT_LEVEL),
                                                        currentLevel + 1,
                                                        GuiValueTone.PRIMARY,
                                                ))
                                                if (MyWorldManagerApi.isWorldPointEconomyEnabled()) {
                                                        add(GuiMenuEntryData(
                                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_EXPAND_BLOCKS_COST),
                                                                cost,
                                                                if (stats.worldPoint < cost) GuiValueTone.DANGER else GuiValueTone.PRIMARY,
                                                        ))
                                                        add(GuiMenuEntryData(
                                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_EXPAND_BLOCKS_OWNED_POINTS),
                                                                stats.worldPoint,
                                                                GuiValueTone.PRIMARY,
                                                        ))
                                                }
                                        }
                                        add(GuiMenuEntryData(
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_EXPAND_BLOCKS_BORDER_CENTER),
                                                "X: ${formatDecimal(borderInfo.centerX)} / Z: ${formatDecimal(borderInfo.centerZ)}",
                                                GuiValueTone.PRIMARY,
                                        ))
                                        add(GuiMenuEntryData(
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_EXPAND_BLOCKS_BORDER_SIZE),
                                                formatDecimal(borderInfo.size),
                                                GuiValueTone.PRIMARY,
                                        ))
                                }
                        }
                        val expansionWarnings = buildList {
                                if (currentLevel == WorldData.EXPANSION_LEVEL_SPECIAL) {
                                        add(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_EXPAND_BLOCKS_NO_BORDER))
                                } else {
                                        if (currentLevel >= maxLevel) {
                                                add(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_EXPAND_BLOCKS_MAX_REACHED))
                                        } else if (MyWorldManagerApi.isWorldPointEconomyEnabled() && stats.worldPoint < cost) {
                                                add(lang.getMessage(
                                                        player,
                                                        MyworldGuiSettingsKeys.GUI_SETTINGS_EXPAND_BLOCKS_SHORTAGE,
                                                        mapOf("shortage" to cost - stats.worldPoint),
                                                ))
                                        }
                                        if (!isInWorld && warningLore != null) add(warningLore)
                                }
                        }
                        val expansionActions = buildList {
                                if (currentLevel != WorldData.EXPANSION_LEVEL_SPECIAL && isInWorld && currentLevel < maxLevel) {
                                        add(menuGestureAction(
                                                ACTION_RUNTIME_DISPATCH,
                                                MenuGesture.LEFT,
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_EXPAND_ACTION_OPEN_MENU),
                                                mapOf(ROUTE_OPERATION to WorldSettingsRuntimeOperation.EXPAND.name),
                                                safety = MenuActionSafety.NAVIGATION_ONLY,
                                        ))
                                }
                                if (currentLevel != WorldData.EXPANSION_LEVEL_SPECIAL && !isBedrock && isInWorld) {
                                        add(menuGestureAction(
                                                ACTION_RUNTIME_DISPATCH,
                                                MenuGesture.RIGHT,
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_EXPAND_ACTION_TELEPORT_CENTER),
                                                mapOf(ROUTE_OPERATION to WorldSettingsRuntimeOperation.EXPAND.name),
                                                safety = MenuActionSafety.EXTERNAL_SIDE_EFFECT,
                                        ))
                                }
                        }
                        inventory.setMenuEntry(
                                player,
                                GuiMenuEntrySpec(
                                        slot = 23,
                                        material = plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "expand",
                                                Material.FILLED_MAP,
                                        ),
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_EXPAND_DISPLAY),
                                                GuiNameStyle.DEFAULT,
                                        ),
                                        role = if (expansionActions.isEmpty()) GuiElementRole.CONTENT else GuiElementRole.ACTION,
                                        description = lang.getMessageList(player, MyworldGuiSettingsKeys.GUI_SETTINGS_EXPAND_BLOCKS_DESCRIPTION),
                                        data = expansionData,
                                        warnings = expansionWarnings,
                                        actions = expansionActions,
                                ),
                        )
                }

                // スロット24: 公開レベル変更 (オーナーのみ)
                if (ownerActionsAllowed) {
                        val levels =
                                listOf(
                                        Triple(
                                                PublishLevel.PUBLIC,
                                                lang.getMessage(player, MyworldPublishLevelKeys.PUBLISH_LEVEL_PUBLIC),
                                                lang.getMessage(
                                                        player,
                                                        MyworldPublishLevelKeys.PUBLISH_LEVEL_COLOR_PUBLIC
                                                )
                                        ),
                                        Triple(
                                                PublishLevel.FRIEND,
                                                lang.getMessage(player, MyworldPublishLevelKeys.PUBLISH_LEVEL_FRIEND),
                                                lang.getMessage(
                                                        player,
                                                        MyworldPublishLevelKeys.PUBLISH_LEVEL_COLOR_FRIEND
                                                )
                                        ),
                                        Triple(
                                                PublishLevel.PRIVATE,
                                                lang.getMessage(player, MyworldPublishLevelKeys.PUBLISH_LEVEL_PRIVATE),
                                                lang.getMessage(
                                                        player,
                                                        MyworldPublishLevelKeys.PUBLISH_LEVEL_COLOR_PRIVATE
                                                )
                                        ),
                                        Triple(
                                                PublishLevel.LOCKED,
                                                lang.getMessage(player, MyworldPublishLevelKeys.PUBLISH_LEVEL_LOCKED),
                                                lang.getMessage(
                                                        player,
                                                        MyworldPublishLevelKeys.PUBLISH_LEVEL_COLOR_LOCKED
                                                )
                                        )
                                )

                        inventory.setMenuEntry(
                                player,
                                GuiMenuEntrySpec(
                                        slot = 24,
                                        material = plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "publish",
                                                Material.OAK_DOOR,
                                        ),
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_PUBLISH_DISPLAY),
                                                GuiNameStyle.DEFAULT,
                                        ),
                                        role = GuiElementRole.ACTION,
                                        description = listOf(lang.getMessage(
                                                player,
                                                "publish_level.description.${worldData.publishLevel.name.lowercase()}",
                                        )),
                                        options = levels.map { (level, name, _) ->
                                                GuiMenuEntryOption(name, level == worldData.publishLevel)
                                        },
                                        actions = listOf(menuGestureAction(
                                                ACTION_RUNTIME_DISPATCH,
                                                MenuGesture.ANY,
                                                lang.getMessage(player, MyworldGuiCommonKeys.GUI_COMMON_ACTION_CYCLE),
                                                mapOf(ROUTE_OPERATION to WorldSettingsRuntimeOperation.CYCLE_PUBLISH.name),
                                                safety = MenuActionSafety.REVERSIBLE,
                                                reversibleContract = MwmMenuActionSemantics.contract("world-publish"),
                                        )),
                                ),
                        )
                }

                // スロット25: メンバー管理 (オーナーのみ)
                if (ownerActionsAllowed) {

                        val totalCount = worldData.members.size + worldData.moderators.size + 1

                        // メンバーリストの作成 (Owner > Moderator > Member)
                        val allMemberData = mutableListOf<Triple<UUID, String, String>>()
                        val ownerRoleColor = lang.getMessage(player, MyworldPublishLevelKeys.PUBLISH_LEVEL_COLOR_OWNER)
                        val moderatorRoleColor =
                                lang.getMessage(player, MyworldPublishLevelKeys.PUBLISH_LEVEL_COLOR_MODERATOR)
                        val memberRoleColor = lang.getMessage(player, MyworldPublishLevelKeys.PUBLISH_LEVEL_COLOR_MEMBER)

                        allMemberData.add(
                                Triple(
                                        worldData.owner,
                                        lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_MEMBER_ROLE_OWNER),
                                        ownerRoleColor
                                )
                        )
                        worldData.moderators.forEach {
                                allMemberData.add(
                                        Triple(
                                                it,
                                                lang.getMessage(
                                                        player,
                                                        MyworldGuiSettingsKeys.GUI_SETTINGS_MEMBER_ROLE_MODERATOR
                                                ),
                                                moderatorRoleColor
                                        )
                                )
                        }
                        worldData.members.forEach {
                                allMemberData.add(
                                        Triple(
                                                it,
                                                lang.getMessage(
                                                        player,
                                                        MyworldGuiSettingsKeys.GUI_SETTINGS_MEMBER_ROLE_MEMBER
                                                ),
                                                memberRoleColor
                                        )
                                )
                        }

                        val maxDisplay = 10
                        val displayList = allMemberData.take(maxDisplay).joinToString("\n") { (uuid, role, color) ->
                                val playerName = PlayerNameUtil.getNameOrDefault(uuid, lang.getMessage(player, CommonKeys.GENERAL_UNKNOWN))

                                val isOnline = Bukkit.getOfflinePlayer(uuid).isOnline
                                val nameColor = if (isOnline) "§a" else "§7"
                                val debugColor = "§8"

                                if (role == lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_MEMBER_ROLE_MEMBER)) {
                                    lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_MEMBER_LIST_ITEM_MEMBER, mapOf("name_color" to nameColor, "player" to playerName))
                                } else {
                                    lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_MEMBER_LIST_ITEM, mapOf("debug_color" to debugColor, "role_color" to color, "role" to role, "name_color" to nameColor, "player" to playerName))
                                }
                        }

                        val memberListString = if (allMemberData.size > maxDisplay) {
                                val remaining = allMemberData.size - maxDisplay
                                val onlineCount = allMemberData.drop(maxDisplay).count { Bukkit.getOfflinePlayer(it.first).isOnline }
                                displayList + "\n" + lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_MEMBER_MORE_MEMBERS, mapOf("remaining" to remaining, "online" to onlineCount))
                        } else {
                                displayList
                        }

                        val pendingInviteCount =
                                plugin.pendingInteractionRepository
                                        .findByWorldAndType(worldData.uuid, PendingInteractionType.MEMBER_INVITE)
                                        .size
                        val pendingRequestCount =
                                plugin.pendingInteractionRepository
                                        .findByWorldAndType(worldData.uuid, PendingInteractionType.MEMBER_REQUEST)
                                        .size

                        inventory.setMenuEntry(
                                player,
                                GuiMenuEntrySpec(
                                        slot = 25,
                                        material = plugin.menuConfigManager.getIconMaterial("world_settings", "members", Material.PLAYER_HEAD),
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_MEMBER_DISPLAY), GuiNameStyle.DEFAULT),
                                        role = GuiElementRole.ACTION,
                                        description = lang.getMessageList(player, MyworldGuiSettingsKeys.GUI_SETTINGS_MEMBER_BLOCKS_DESCRIPTION) +
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_MEMBER_BLOCKS_LIST_HEADER) +
                                                memberListString.lines().filter(String::isNotBlank).map(String::trim),
                                        data = listOf(
                                                GuiMenuEntryData(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_MEMBER_BLOCKS_COUNT), totalCount, GuiValueTone.INFO),
                                                GuiMenuEntryData(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_MEMBER_BLOCKS_PENDING_REQUESTS), pendingRequestCount, GuiValueTone.PRIMARY),
                                                GuiMenuEntryData(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_MEMBER_BLOCKS_PENDING_INVITES), pendingInviteCount, GuiValueTone.PRIMARY),
                                        ),
                                        actions = contractMenuActions(
                                                player, worldData, WorldSettingsAction.MANAGE_MEMBERS,
                                                WorldSettingsRuntimeOperation.MANAGE_MEMBERS,
                                                listOf(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_MEMBER_ACTION_OPEN_LIST)),
                                        ),
                                        sounds = plugin.worldSettingsActionService.contract(player, worldData, WorldSettingsAction.MANAGE_MEMBERS).sounds,
                                ),
                        )
                }

                // TODO: Chanpon表示ポリシー適用時のタグ非表示を2026-07-31時点の正仕様と照合し、必要な表示条件を復元する。
                // タグ設定
                if (hasManagePermission) {
                        val tagsList = if (worldData.tags.isEmpty()) {
                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_TAGS_LORE_EMPTY)
                        } else {
                                worldData.tags.joinToString(", ") { plugin.worldTagManager.getDisplayName(player, it) }
                        }

                        inventory.setMenuEntry(
                                player,
                                GuiMenuEntrySpec(
                                        slot = tagsSettingSlot,
                                        material = plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "tags",
                                                Material.BOOK,
                                        ),
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_TAGS_DISPLAY),
                                                GuiNameStyle.DEFAULT,
                                        ),
                                        role = GuiElementRole.ACTION,
                                        description = lang.getMessageList(
                                                player,
                                                MyworldGuiSettingsKeys.GUI_SETTINGS_TAGS_BLOCKS_DESCRIPTION,
                                        ),
                                        data = listOf(GuiMenuEntryData(
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_TAGS_BLOCKS_CURRENT_LABEL),
                                                tagsList,
                                                GuiValueTone.WARNING,
                                        )),
                                        actions = listOf(menuGestureAction(
                                                ACTION_RUNTIME_DISPATCH,
                                                MenuGesture.ANY,
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_TAGS_ACTION_EDIT),
                                                mapOf(ROUTE_OPERATION to WorldSettingsRuntimeOperation.EDIT_TAGS.name),
                                                safety = MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE,
                                        )),
                                ),
                        )
                }

                // 案内設定
                if (hasManagePermission) {
                        val messagePreview = worldData.announcementMessages

                        inventory.setMenuEntry(
                                player,
                                GuiMenuEntrySpec(
                                        slot = announcementSettingSlot,
                                        material = plugin.menuConfigManager.getIconMaterial("world_settings", "announcement", Material.OAK_SIGN),
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_ANNOUNCEMENT_DISPLAY), GuiNameStyle.DEFAULT),
                                        role = GuiElementRole.ACTION,
                                        description = lang.getMessageList(player, MyworldGuiSettingsKeys.GUI_SETTINGS_ANNOUNCEMENT_BLOCKS_DESCRIPTION) +
                                                if (messagePreview.isEmpty()) emptyList() else
                                                        listOf(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_ANNOUNCEMENT_PREVIEW_HEADER)) + messagePreview,
                                        actions = contractMenuActions(
                                                player, worldData, WorldSettingsAction.EDIT_ANNOUNCEMENT,
                                                WorldSettingsRuntimeOperation.EDIT_ANNOUNCEMENT,
                                                listOf(
                                                        lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_ANNOUNCEMENT_ACTION_SET_MESSAGE),
                                                        lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_ANNOUNCEMENT_ACTION_RESET_MESSAGE),
                                                ),
                                        ),
                                        sounds = plugin.worldSettingsActionService.contract(player, worldData, WorldSettingsAction.EDIT_ANNOUNCEMENT).sounds,
                                ),
                        )
                }

                // 通知設定
                if (hasManagePermission) {
                        val onlineColor = lang.getMessage(player, MyworldPublishLevelKeys.PUBLISH_LEVEL_COLOR_ONLINE)
                        val offlineColor = lang.getMessage(player, MyworldPublishLevelKeys.PUBLISH_LEVEL_COLOR_OFFLINE)
                        val statusColor =
                                if (worldData.notificationEnabled) onlineColor else offlineColor
                        val statusText =
                                if (worldData.notificationEnabled)
                                        lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_NOTIFICATION_ON)
                                else lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_NOTIFICATION_OFF)

                        inventory.setMenuEntry(
                                player,
                                GuiMenuEntrySpec(
                                        slot = notificationSettingSlot,
                                        material = Material.BELL,
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_NOTIFICATION_DISPLAY),
                                                GuiNameStyle.DEFAULT,
                                        ),
                                        role = GuiElementRole.ACTION,
                                        description = lang.getMessageList(
                                                player,
                                                MyworldGuiSettingsKeys.GUI_SETTINGS_NOTIFICATION_BLOCKS_DESCRIPTION,
                                        ),
                                        data = listOf(GuiMenuEntryData(
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_NOTIFICATION_BLOCKS_CURRENT_LABEL),
                                                statusText,
                                                if (worldData.notificationEnabled) GuiValueTone.SUCCESS else GuiValueTone.MUTED,
                                        )),
                                        actions = listOf(menuGestureAction(
                                                ACTION_RUNTIME_DISPATCH,
                                                MenuGesture.ANY,
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_NOTIFICATION_ACTION_TOGGLE),
                                                mapOf(ROUTE_OPERATION to WorldSettingsRuntimeOperation.TOGGLE_NOTIFICATION.name),
                                                safety = MenuActionSafety.REVERSIBLE,
                                                reversibleContract = MwmMenuActionSemantics.contract("world-notification"),
                                        )),
                                        glint = worldData.notificationEnabled,
                                ),
                        )
                }

                // スロット32: 環境設定 (オーナーのみ)
                if (ownerActionsAllowed && !isBedrock) {
                        inventory.setMenuEntry(
                                player,
                                GuiMenuEntrySpec(
                                        slot = 32,
                                        material = plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "environment",
                                                Material.GRASS_BLOCK,
                                        ),
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_ENVIRONMENT_DISPLAY),
                                                GuiNameStyle.DEFAULT,
                                        ),
                                        role = if (isInWorld) GuiElementRole.ACTION else GuiElementRole.CONTENT,
                                        description = lang.getMessageList(
                                                player,
                                                MyworldGuiSettingsKeys.GUI_SETTINGS_ENVIRONMENT_BLOCKS_SUMMARY,
                                        ),
                                        warnings = if (!isInWorld && warningLore != null) listOf(warningLore) else emptyList(),
                                        actions = if (isInWorld) listOf(menuGestureAction(
                                                ACTION_RUNTIME_DISPATCH,
                                                MenuGesture.ANY,
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_ENVIRONMENT_ACTION_OPEN),
                                                mapOf(ROUTE_OPERATION to WorldSettingsRuntimeOperation.OPEN_ENVIRONMENT.name),
                                                safety = MenuActionSafety.NAVIGATION_ONLY,
                                        )) else emptyList(),
                                ),
                        )
                }

                // スロット33: 重大な設定 (オーナーのみ)
                // スロット33: 重大な設定 (オーナーのみ)
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                if (ownerActionsAllowed && stats.criticalSettingsEnabled) {
                        inventory.setMenuEntry(
                                player,
                                GuiMenuEntrySpec(
                                        slot = 33,
                                        material = plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "critical",
                                                Material.TNT,
                                        ),
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_CRITICAL_DISPLAY),
                                                GuiNameStyle.DEFAULT,
                                        ),
                                        role = if (isInWorld) GuiElementRole.ACTION else GuiElementRole.CONTENT,
                                        description = lang.getMessageList(
                                                player,
                                                MyworldGuiSettingsKeys.GUI_SETTINGS_CRITICAL_BLOCKS_SUMMARY,
                                        ),
                                        warnings = if (!isInWorld && warningLore != null) listOf(warningLore) else emptyList(),
                                        actions = if (isInWorld) listOf(menuGestureAction(
                                                ACTION_RUNTIME_DISPATCH,
                                                MenuGesture.ANY,
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_CRITICAL_ACTION_OPEN),
                                                mapOf(ROUTE_OPERATION to WorldSettingsRuntimeOperation.OPEN_CRITICAL.name),
                                                safety = MenuActionSafety.NAVIGATION_ONLY,
                                        )) else emptyList(),
                                ),
                        )
                }

                // ワールド情報
                val currentLevel = worldData.borderExpansionLevel
                val costsSection = plugin.config.getConfigurationSection("expansion.costs")
                val maxLevel = costsSection?.getKeys(false)?.size ?: 3
                val totalCount = worldData.members.size + worldData.moderators.size + 1
                val onlineCount =
                        (worldData.members + worldData.moderators + worldData.owner).count {
                                Bukkit.getOfflinePlayer(it).isOnline
                        }

                // 公開レベル表示用
                val publishLevelColor =
                        when (worldData.publishLevel) {
                                PublishLevel.PUBLIC ->
                                        lang.getMessage(player, MyworldPublishLevelKeys.PUBLISH_LEVEL_COLOR_PUBLIC)
                                PublishLevel.FRIEND ->
                                        lang.getMessage(player, MyworldPublishLevelKeys.PUBLISH_LEVEL_COLOR_FRIEND)
                                PublishLevel.PRIVATE ->
                                        lang.getMessage(player, MyworldPublishLevelKeys.PUBLISH_LEVEL_COLOR_PRIVATE)
                                PublishLevel.LOCKED ->
                                        lang.getMessage(player, MyworldPublishLevelKeys.PUBLISH_LEVEL_COLOR_LOCKED)
                        }
                val publishLevelName =
                        lang.getMessage(
                                player,
                                "publish_level.${worldData.publishLevel.name.lowercase()}"
                        )

                // 有効期限の計算
                val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val expireDate =
                        try {
                                java.time.LocalDate.parse(worldData.expireDate, dateFormatter)
                        } catch (e: Exception) {
                                java.time.LocalDate.now().plusDays(7)
                        }
                val today = java.time.LocalDate.now()
                val daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(today, expireDate)
                val displayFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日")
                val dateStr = displayFormatter.format(expireDate)

                // 作成日の計算
                val createdAtDate =
                        try {
                                val dateTimeFormatter =
                                        java.time.format.DateTimeFormatter.ofPattern(
                                                "yyyy-MM-dd HH:mm:ss"
                                        )
                                java.time.LocalDateTime.parse(
                                                worldData.createdAt,
                                                dateTimeFormatter
                                        )
                                        .toLocalDate()
                        } catch (e: Exception) {
                                java.time.LocalDate.now()
                        }
                val daysSinceCreation =
                        java.time.temporal.ChronoUnit.DAYS.between(
                                createdAtDate,
                                java.time.LocalDate.now()
                        )
                val createdInfo =
                        if (daysSinceCreation == 0L) {
                                lang.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_WORLD_ITEM_CREATED_INFO_TODAY)
                        } else {
                                lang.getMessage(
                                        player,
                                        MyworldGuiAdminKeys.GUI_ADMIN_WORLD_ITEM_CREATED_INFO_DAYS,
                                        mapOf("days" to daysSinceCreation)
                                )
                        }

                val isSpecialExpansion = currentLevel == WorldData.EXPANSION_LEVEL_SPECIAL
                val warpContract = plugin.worldSettingsActionService
                        .contract(player, worldData, WorldSettingsAction.WARP)
                // TODO: main画面の所有はMWMに保ち、旧正仕様の中央ヘッダー要約を受け取るsummary contribution契約を検討する。
                inventory.setMenuEntry(
                        player,
                        GuiMenuEntrySpec(
                                slot = worldInfoSlot,
                                material = worldData.icon,
                                name = me.awabi2048.myworldmanager.util.fixedLabelName(
                                        lang.getMessage(
                                                player,
                                                MyworldGuiSettingsKeys.GUI_SETTINGS_MAIN_INFO_NAME,
                                                mapOf("world" to worldData.name),
                                        ),
                                        GuiNameStyle.DEFAULT,
                                ),
                                role = if (warpContract.actionable) GuiElementRole.ACTION else GuiElementRole.CONTENT,
                                description = if (worldData.description.isEmpty()) emptyList() else listOf(worldData.description),
                                data = buildList {
                                        add(GuiMenuEntryData(
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_MAIN_INFO_OWNER_LABEL),
                                                PlayerNameUtil.getNameOrDefault(worldData.owner, lang.getMessage(player, CommonKeys.GENERAL_UNKNOWN)),
                                                GuiValueTone.INFO,
                                        ))
                                        if (!isSpecialExpansion) {
                                                add(GuiMenuEntryData(
                                                        lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_MAIN_INFO_EXPANSION_LABEL),
                                                        "$currentLevel/$maxLevel",
                                                        GuiValueTone.PRIMARY,
                                                ))
                                        }
                                        add(GuiMenuEntryData(
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_MAIN_INFO_CREATED_LABEL),
                                                "${displayFormatter.format(createdAtDate)} ($createdInfo)",
                                                GuiValueTone.PRIMARY,
                                        ))
                                        if (!isSpecialExpansion) {
                                                add(GuiMenuEntryData(
                                                        lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_MAIN_INFO_ARCHIVE_LABEL),
                                                        lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_MAIN_INFO_ARCHIVE_VALUE, mapOf("days" to daysRemaining, "date" to dateStr)),
                                                        GuiValueTone.WARNING,
                                                ))
                                        }
                                        add(GuiMenuEntryData(
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_MAIN_INFO_MEMBERS_LABEL),
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_MAIN_INFO_MEMBERS_VALUE, mapOf("members" to totalCount, "online" to onlineCount)),
                                        ))
                                        add(GuiMenuEntryData(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_MAIN_INFO_PUBLISH_LABEL), publishLevelName))
                                        add(GuiMenuEntryData(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_MAIN_INFO_FAVORITES_LABEL), worldData.favorite, GuiValueTone.DANGER))
                                        add(GuiMenuEntryData(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_MAIN_INFO_VISITORS_LABEL), worldData.recentVisitors.sum(), GuiValueTone.INFO))
                                        add(GuiMenuEntryData("UUID", worldData.uuid, GuiValueTone.MUTED))
                                },
                                actions = contractMenuActions(
                                        player,
                                        worldData,
                                        WorldSettingsAction.WARP,
                                        WorldSettingsRuntimeOperation.WARP,
                                        listOf(lang.getMessage(player, MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_WORLD_ITEM_WARP)),
                                ),
                                sounds = warpContract.sounds,
                        ),
                )

                // スロット51: 訪問中のプレイヤー管理
                val visitors =
                        Bukkit.getWorld(targetWorldName)?.players?.filter {
                                it.uniqueId != worldData.owner &&
                                        !worldData.moderators.contains(it.uniqueId) &&
                                        !worldData.members.contains(it.uniqueId)
                        }
                                ?: emptyList()
                if (hasManagePermission && visitors.isNotEmpty()) {
                        inventory.setMenuEntry(
                                player,
                                GuiMenuEntrySpec(
                                        slot = 51,
                                        material = plugin.menuConfigManager.getIconMaterial("world_settings", "visitor", Material.SPYGLASS),
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_VISITORS_DISPLAY), GuiNameStyle.DEFAULT),
                                        role = GuiElementRole.ACTION,
                                        data = listOf(GuiMenuEntryData(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_VISITORS_BLOCKS_COUNT_LABEL), visitors.size, GuiValueTone.PRIMARY)),
                                        actions = listOf(menuGestureAction(
                                                ACTION_RUNTIME_DISPATCH,
                                                MenuGesture.ANY,
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_VISITORS_ACTION_OPEN),
                                                mapOf(ROUTE_OPERATION to WorldSettingsRuntimeOperation.MANAGE_VISITORS.name),
                                                safety = MenuActionSafety.NAVIGATION_ONLY,
                                        )),
                                ),
                        )
                }

                // スロット52: 設置済みポータルの管理
                // ワールドオーナーのみ表示
                val hasPortals =
                        ownerActionsAllowed &&
                                plugin.portalRepository.findAll().any {
                                        it.worldKey == worldData.worldKey
                                }
                if (hasPortals) {
                        inventory.setMenuEntry(
                                player,
                                GuiMenuEntrySpec(
                                        slot = 52,
                                        material = plugin.menuConfigManager.getIconMaterial("world_settings", "portals", Material.END_PORTAL_FRAME),
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_PORTALS_DISPLAY), GuiNameStyle.DEFAULT),
                                        role = GuiElementRole.ACTION,
                                        description = lang.getMessageList(player, MyworldGuiSettingsKeys.GUI_SETTINGS_PORTALS_BLOCKS_SUMMARY),
                                        actions = contractMenuActions(
                                                player, worldData, WorldSettingsAction.MANAGE_PORTALS,
                                                WorldSettingsRuntimeOperation.MANAGE_PORTALS,
                                                listOf(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_PORTALS_ACTION_OPEN)),
                                        ),
                                        sounds = plugin.worldSettingsActionService.contract(player, worldData, WorldSettingsAction.MANAGE_PORTALS).sounds,
                                ),
                        )
                }

                // 空きスロットを灰色板ガラスで埋める
                val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
                for (i in 0 until inventory.size) {
                        if (!inventory.isOccupied(i)) {
                                inventory.setItem(i, grayPane)
                        }
                }

                applyCapabilities(
                        inventory,
                        player,
                        WorldSettingsCapabilityPlacements.EXPANSION_ACTION,
                        listOf(23),
                        mapOf(WORLD_UUID_ARGUMENT to worldData.uuid.toString()),
                )
                applyCapabilities(
                        inventory,
                        player,
                        WorldSettingsCapabilityPlacements.PUBLISH_ACTION,
                        listOf(
                                if (presentationMode == WorldSettingsDisplayMode.COMPACT_LIMITED_OWNER) {
                                        21
                                } else {
                                        24
                                },
                        ),
                        mapOf(WORLD_UUID_ARGUMENT to worldData.uuid.toString()),
                )
                applyCapabilities(
                        inventory,
                        player,
                        WorldSettingsCapabilityPlacements.NOTIFICATION_ACTION,
                        listOf(
                                if (presentationMode == WorldSettingsDisplayMode.COMPACT_LIMITED_OWNER) {
                                        22
                                } else {
                                        notificationSettingSlot
                                },
                        ),
                        mapOf(WORLD_UUID_ARGUMENT to worldData.uuid.toString()),
                )
                applyCapabilities(
                        inventory,
                        player,
                        WorldSettingsCapabilityPlacements.ENVIRONMENT_ACTION,
                        listOf(32),
                        mapOf(WORLD_UUID_ARGUMENT to worldData.uuid.toString()),
                )
                applyCapabilities(
                        inventory,
                        player,
                        WorldSettingsCapabilityPlacements.CRITICAL_ACTION,
                        listOf(33),
                        mapOf(WORLD_UUID_ARGUMENT to worldData.uuid.toString()),
                )
                applyCapabilities(
                        inventory,
                        player,
                        WorldSettingsCapabilityPlacements.FOOTER_ACTIONS,
                        WORLD_SETTINGS_CAPABILITY_SLOTS,
                        mapOf(WORLD_UUID_ARGUMENT to worldData.uuid.toString()),
                )
                applyCapabilities(
                        inventory,
                        player,
                        WorldSettingsCapabilityPlacements.FOOTER_LEFT_ACTIONS,
                        listOf(
                                if (presentationMode == WorldSettingsDisplayMode.COMPACT_LIMITED_OWNER) {
                                        23
                                } else {
                                        bottomRowStartSlot + if (isOwner) 1 else 2
                                },
                        ),
                        mapOf(WORLD_UUID_ARGUMENT to worldData.uuid.toString()),
                )
                applyCapabilities(
                        inventory,
                        player,
                        WorldSettingsCapabilityPlacements.FOOTER_RIGHT_ACTIONS,
                        listOf(bottomRowStartSlot + 5),
                        mapOf(WORLD_UUID_ARGUMENT to worldData.uuid.toString()),
                )

                return InventoryMenuView(
                        size = inventory.size,
                        title = title,
                        elements = inventory.elements(),
                        standardFrame = false,
                        playerInventoryInteraction = PlayerInventoryInteraction.INTERACTIVE,
                )
        }

        private fun applyCapabilities(
                inventory: RuntimeItemBuffer,
                player: Player,
                placement: String,
                slots: List<Int>,
                arguments: Map<String, String>,
        ) {
                // TODO: availability・presentation・handlerを同じdecisionから生成できる一般契約を、このMWM合成境界へ導入する。
                val service = CCSystem.getAPI().getMenuCapabilityService()
                val validSlots = slots.asSequence().filter { it in 0 until inventory.size }
                service.definitions(placement)
                        .asSequence()
                        .mapNotNull { definition ->
                                service.resolve(definition.capabilityId, player, arguments)
                                        ?.requireExplicitActionSafety()
                        }
                        .zip(validSlots)
                        .forEach { (resolved, slot) ->
                                inventory.setElement(
                                        CCSystem.getAPI().getGuiElementService().menuCapabilityEntry(
                                                player,
                                                worldSettingsCapabilityInvocation(
                                                        slot,
                                                        resolved,
                                                        arguments,
                                                ),
                                        ),
                                )
                        }
        }

        fun openArchiveConfirmation(
                player: Player,
                worldData: WorldData,
                fromCriticalSettings: Boolean = false,
        ) {
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        if (fromCriticalSettings) {
                                SettingsAction.ARCHIVE_WORLD_FROM_CRITICAL
                        } else {
                                SettingsAction.ARCHIVE_WORLD
                        },
                        isGui = true
                )
                runtime.navigate(
                        player,
                        runtimeRoute(
                                if (fromCriticalSettings) {
                                        WorldSettingsRuntimeScreen.ARCHIVE_FROM_CRITICAL_CONFIRM
                                } else {
                                        WorldSettingsRuntimeScreen.ARCHIVE_CONFIRM
                                },
                                worldData.uuid,
                        ),
                )
        }

        private fun renderArchiveConfirmation(
                player: Player,
                worldData: WorldData,
        ): InventoryMenuView {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, MyworldGuiAdminKeys.GUI_ARCHIVE_CONFIRM_TITLE)
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size, player)
                inventory.applyStandardFrame()

                val infoItem =
                        createItem(
                                Material.PAPER,
                                lang.getMessage(player, MyworldGuiCommonKeys.GUI_ARCHIVE_QUESTION),
                                me.awabi2048.myworldmanager.util.semanticLore(lang.getMessageList(player, MyworldGuiCommonKeys.GUI_ARCHIVE_WARNING).map(GuiLoreLine::Warning), GuiLoreFrame.BOTH),
                                ItemTag.TYPE_GUI_INFO
                        )
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(player, MyworldGuiCommonKeys.GUI_ARCHIVE_CONFIRM),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(GuiLoreLine.Text(lang.getMessage(player, MyworldGuiCommonKeys.GUI_ARCHIVE_CONFIRM_DESC))), GuiLoreFrame.NONE),
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(player, MyworldGuiCommonKeys.GUI_ARCHIVE_CANCEL),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(GuiLoreLine.Text(lang.getMessage(player, MyworldGuiCommonKeys.GUI_ARCHIVE_CANCEL_DESC))), GuiLoreFrame.NONE),
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )

                inventory.bindConfirmation()
                return runtimeView(title, inventory)
        }

        fun openUnarchiveConfirmation(player: Player, worldData: WorldData) {
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.UNARCHIVE_CONFIRM,
                        isGui = true
                )
                runtime.navigate(
                        player,
                        runtimeRoute(WorldSettingsRuntimeScreen.UNARCHIVE_CONFIRM, worldData.uuid),
                )
        }

        private fun renderUnarchiveConfirmation(
                player: Player,
                worldData: WorldData,
        ): InventoryMenuView {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, MyworldGuiAdminKeys.GUI_UNARCHIVE_CONFIRM_TITLE)
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size, player)
                inventory.applyStandardFrame()

                val infoItem =
                        createItem(
                                Material.PAPER,
                                lang.getMessage(player, MyworldGuiAdminKeys.GUI_UNARCHIVE_CONFIRM_TITLE),
                                me.awabi2048.myworldmanager.util.semanticLore(
                                        lang.getMessageList(player, MyworldGuiAdminKeys.GUI_UNARCHIVE_CONFIRM_DESCRIPTION).map(::warningLine),
                                        GuiLoreFrame.BOTH,
                                ),
                                ItemTag.TYPE_GUI_INFO
                        )
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_CONCRETE,
                                lang.getMessage(player, CommonKeys.GUI_COMMON_CONFIRM),
                                GuiLoreSpec.None,
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )

                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_CONCRETE,
                                lang.getMessage(player, CommonKeys.GUI_COMMON_CANCEL),
                                GuiLoreSpec.None,
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )

                inventory.bindConfirmation()
                return runtimeView(title, inventory)
        }

        fun openExpansionMethodSelection(
                player: Player,
                worldData: WorldData
        ) {
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.EXPAND_SELECT_METHOD,
                        isGui = true
                )
                runtime.navigate(
                        player,
                        runtimeRoute(
                                WorldSettingsRuntimeScreen.EXPANSION_METHOD_SELECTION,
                                worldData.uuid,
                        ),
                )
        }

        private fun renderExpansionMethodSelection(
                player: Player,
                worldData: WorldData,
        ): InventoryMenuView {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, MyworldGuiCommonKeys.GUI_EXPANSION_METHOD_TITLE)
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size, player)
                // ヘッダー・フッター
                val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
                inventory.applyStandardFrame()

                inventory.setItem(
                        20,
                        createItem(
                                Material.MAP,
                                lang.getMessage(player, MyworldGuiCommonKeys.GUI_EXPANSION_CENTER_EXPAND_NAME),
                                me.awabi2048.myworldmanager.util.semanticLore(lang.getMessageList(player, MyworldGuiCommonKeys.GUI_EXPANSION_CENTER_EXPAND_LORE).map(::descriptionLine), GuiLoreFrame.BOTH),
                                null
                        )
                )
                inventory.bindRuntimeOperation(
                        20,
                        WorldSettingsRuntimeOperation.EXPAND_AUTOMATIC,
                )

                inventory.setItem(
                        24,
                        createItem(
                                Material.COMPASS,
                                lang.getMessage(player, MyworldGuiCommonKeys.GUI_EXPANSION_DIRECTION_EXPAND_NAME),
                                me.awabi2048.myworldmanager.util.semanticLore(lang.getMessageList(player, MyworldGuiCommonKeys.GUI_EXPANSION_DIRECTION_EXPAND_LORE).map(::descriptionLine), GuiLoreFrame.BOTH),
                                null
                        )
                )
                inventory.bindRuntimeOperation(
                        24,
                        WorldSettingsRuntimeOperation.EXPAND_DIRECTION,
                )

                // 戻るボタン
                inventory.setElement(
                        CCSystem.getAPI().getGuiElementService().backEntry(
                                player,
                                40,
                                plugin.menuConfigManager.getIconMaterial("world_settings", "back", Material.REDSTONE),
                        )
                )

                val canStepBack = worldData.latestBorderExpansionRecord() != null
                if (canStepBack) {
                        inventory.setItem(
                                42,
                                createItem(
                                        Material.RECOVERY_COMPASS,
                                        lang.getMessage(player, MyworldGuiCommonKeys.GUI_EXPANSION_STEP_BACK_NAME),
                                        me.awabi2048.myworldmanager.util.semanticLore(lang.getMessageList(player, MyworldGuiCommonKeys.GUI_EXPANSION_STEP_BACK_LORE).map(::descriptionLine), GuiLoreFrame.BOTH),
                                        null
                                )
                        )
                        inventory.bindRuntimeOperation(
                                42,
                                WorldSettingsRuntimeOperation.EXPANSION_STEP_BACK,
                        )
                }

                val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
                for (i in 0 until inventory.size) {
                        if (!inventory.isOccupied(i)) inventory.setItem(i, grayPane)
                }

                return runtimeView(title, inventory)
        }

        fun openExpansionStepBackConfirmation(player: Player, worldData: WorldData) {
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.STEP_BACK_EXPANSION_CONFIRM,
                        isGui = true
                )
                runtime.navigate(
                        player,
                        runtimeRoute(
                                WorldSettingsRuntimeScreen.EXPANSION_STEP_BACK_CONFIRM,
                                worldData.uuid,
                        ),
                )
        }

        private fun renderExpansionStepBackConfirmation(
                player: Player,
                worldData: WorldData,
        ): InventoryMenuView {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, MyworldGuiCommonKeys.GUI_CONFIRM_STEP_BACK_EXPANSION_TITLE)
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size, player)
                inventory.applyStandardFrame()

                val loreLines = mutableListOf<GuiLoreLine>(
                        GuiLoreLine.Warning(lang.getMessage(player, MyworldGuiCommonKeys.GUI_CONFIRM_STEP_BACK_EXPANSION_QUESTION))
                )
                loreLines += lang.getMessageList(player, MyworldGuiCommonKeys.GUI_CONFIRM_STEP_BACK_EXPANSION_DESCRIPTION).map(::warningLine)
                if (worldData.latestBorderExpansionRecord()?.modified == true) {
                        loreLines += lang.getMessageList(player, MyworldGuiCommonKeys.GUI_CONFIRM_STEP_BACK_EXPANSION_MODIFIED_WARNING).map(GuiLoreLine::Warning)
                }
                loreLines += getSpawnAdjustmentWarning(player, worldData, borderResetTargetForStepBack(worldData))
                val infoItem =
                        createItem(
                                Material.RECOVERY_COMPASS,
                                lang.getMessage(player, MyworldGuiCommonKeys.GUI_CONFIRM_STEP_BACK_EXPANSION_DISPLAY),
                                me.awabi2048.myworldmanager.util.semanticLore(loreLines, GuiLoreFrame.BOTH),
                                ItemTag.TYPE_GUI_INFO
                        )
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(player, CommonKeys.GUI_COMMON_CANCEL),
                                GuiLoreSpec.None,
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(player, CommonKeys.GUI_COMMON_CONFIRM),
                                GuiLoreSpec.None,
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )

                inventory.bindConfirmation(confirmSlot = 24, cancelSlot = 20)
                return runtimeView(title, inventory)
        }

        fun openExpansionConfirmation(
                player: Player,
                worldUuid: UUID,
                direction: org.bukkit.block.BlockFace?,
                cost: Int
        ) {
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldUuid,
                        SettingsAction.EXPAND_CONFIRM,
                        isGui = true
                )
                runtime.navigate(
                        player,
                        expansionConfirmationRoute(worldUuid, direction, cost),
                )
        }

        private fun renderExpansionConfirmation(
                player: Player,
                direction: org.bukkit.block.BlockFace?,
                cost: Int,
        ): InventoryMenuView {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, MyworldGuiCommonKeys.GUI_EXPANSION_CONFIRM_TITLE)
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size, player)
                // ヘッダー・フッター
                val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
                inventory.applyStandardFrame()

                val directionKey =
                        when (direction) {
                                org.bukkit.block.BlockFace.NORTH_WEST ->
                                        "general.direction.north_west"
                                org.bukkit.block.BlockFace.NORTH_EAST ->
                                        "general.direction.north_east"
                                org.bukkit.block.BlockFace.SOUTH_WEST ->
                                        "general.direction.south_west"
                                org.bukkit.block.BlockFace.SOUTH_EAST ->
                                        "general.direction.south_east"
                                else -> "general.direction.unknown"
                        }
                val directionName = lang.getMessage(player, directionKey)
                val methodText =
                        if (direction == null)
                                lang.getMessage(player, MyworldGuiCommonKeys.GUI_EXPANSION_METHOD_CENTER)
                        else
                                lang.getMessage(
                                        player,
                                        MyworldGuiCommonKeys.GUI_EXPANSION_METHOD_DIRECTION,
                                        mapOf("direction" to directionName)
                                )
                inventory.setItem(
                        22,
                        createItem(
                                Material.BOOK,
                                lang.getMessage(player, MyworldGuiCommonKeys.GUI_EXPANSION_CONFIRM_INFO),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(
                                        GuiLoreLine.Data(lang.getMessage(player, MyworldGuiCommonKeys.GUI_EXPANSION_METHOD_LABEL), methodText, "§f"),
                                        GuiLoreLine.Data(lang.getMessage(player, MyworldGuiCommonKeys.GUI_EXPANSION_COST_LABEL), cost, "§e"),
                                        GuiLoreLine.Spacer,
                                        GuiLoreLine.Warning(lang.getMessage(player, MyworldGuiCommonKeys.GUI_EXPANSION_WARNING))
                                ), GuiLoreFrame.BOTH),
                                ItemTag.TYPE_GUI_INFO
                        )
                )

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(player, MyworldGuiCommonKeys.GUI_EXPANSION_EXECUTE),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(GuiLoreLine.Text(lang.getMessage(player, MyworldGuiCommonKeys.GUI_EXPANSION_EXECUTE_DESC))), GuiLoreFrame.NONE),
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(player, MyworldGuiCommonKeys.GUI_EXPANSION_CANCEL),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(GuiLoreLine.Text(lang.getMessage(player, MyworldGuiCommonKeys.GUI_EXPANSION_CANCEL_DESC))), GuiLoreFrame.NONE),
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )

                val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
                for (i in 0 until inventory.size) {
                        if (!inventory.isOccupied(i)) inventory.setItem(i, grayPane)
                }

                inventory.bindConfirmation()
                return runtimeView(title, inventory)
        }

        fun openMemberManagement(
                player: Player,
                worldData: WorldData,
                page: Int = 0,
                playSound: Boolean = true,
                replaceCurrent: Boolean = false
        ) {
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.MANAGE_MEMBERS,
                        isGui = true,
                )
                val route = memberManagementRoute(worldData.uuid, page)
                if (replaceCurrent) {
                        runtime.replace(player, route)
                } else {
                        runtime.navigate(player, route)
                }
        }

        private fun renderMemberManagement(
                player: Player,
                worldData: WorldData,
                page: Int,
        ): InventoryMenuView {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_TITLE)

                val allEntries = mutableListOf<MemberManagementEntry>()
                allEntries.add(
                        MemberManagementEntry(
                                playerUuid = worldData.owner,
                                role = lang.getMessage(player, MyworldRoleKeys.ROLE_OWNER)
                        )
                )
                worldData.moderators.forEach {
                        allEntries.add(
                                MemberManagementEntry(
                                        playerUuid = it,
                                        role = lang.getMessage(player, MyworldRoleKeys.ROLE_MODERATOR)
                                )
                        )
                }
                worldData.members.forEach {
                        allEntries.add(
                                MemberManagementEntry(
                                        playerUuid = it,
                                        role = lang.getMessage(player, MyworldRoleKeys.ROLE_MEMBER)
                                )
                        )
                }

                val pendingInvites =
                        plugin.pendingInteractionRepository
                                .findByWorldAndType(worldData.uuid, PendingInteractionType.MEMBER_INVITE)
                pendingInvites.forEach { invite ->
                        allEntries.add(
                                MemberManagementEntry(
                                        playerUuid = invite.targetUuid,
                                        pendingDecisionId = invite.id,
                                        pendingCreatedAt = invite.createdAt,
                                        pendingType = PendingInteractionType.MEMBER_INVITE
                                )
                        )
                }

                val pendingRequests =
                        plugin.pendingInteractionRepository
                                .findByWorldAndType(worldData.uuid, PendingInteractionType.MEMBER_REQUEST)
                pendingRequests.forEach { request ->
                        allEntries.add(
                                MemberManagementEntry(
                                        playerUuid = request.actorUuid,
                                        pendingDecisionId = request.id,
                                        pendingCreatedAt = request.createdAt,
                                        pendingType = PendingInteractionType.MEMBER_REQUEST
                                )
                        )
                }

                val pageLayout = CCSystem.getAPI().getGuiLayoutService().sevenColumnPage(allEntries.size, page)
                val currentPage = pageLayout.page
                val startIndex = pageLayout.startIndex
                val currentPageMembers = allEntries.drop(startIndex).take(pageLayout.itemCount)
                val layout = pageLayout.layout
                val footerStart = layout.size - 9

                val inventory = RuntimeItemBuffer(layout.size, player)
                inventory.clear()

                val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
                inventory.applyStandardFrame()

                // メンバーリストの描画
                val isAdminFlow = plugin.settingsSessionManager.getSession(player)?.isAdminFlow == true
                val canManageRoles = worldData.owner == player.uniqueId || isAdminFlow
                currentPageMembers.forEachIndexed { index, entry ->
                        val slot = layout.itemSlots.getOrNull(index) ?: return@forEachIndexed
                        if (entry.pendingDecisionId != null) {
                                val pendingType =
                                        entry.pendingType ?: PendingInteractionType.MEMBER_INVITE
                                val worldName = worldData.name
                                inventory.setElement(
                                        PendingInteractionItemFactory.createElement(
                                                plugin = plugin,
                                                viewer = player,
                                                slot = slot,
                                                subjectUuid = entry.playerUuid,
                                                type = when (pendingType) {
                                                        PendingInteractionType.MEMBER_INVITE ->
                                                                me.awabi2048.myworldmanager.service.PendingDecisionManager.PendingType.MEMBER_INVITE
                                                        PendingInteractionType.MEMBER_REQUEST ->
                                                                me.awabi2048.myworldmanager.service.PendingDecisionManager.PendingType.MEMBER_REQUEST
                                                },
                                                worldName = worldName,
                                                createdAt = entry.pendingCreatedAt ?: 0L,
                                                actionMode = PendingInteractionActionMode.CANCEL,
                                                actionId = ACTION_RUNTIME_DISPATCH,
                                                actionPayload = mapOf(
                                                        ROUTE_OPERATION to when (pendingType) {
                                                                PendingInteractionType.MEMBER_REQUEST ->
                                                                        WorldSettingsRuntimeOperation.PENDING_REQUEST.name
                                                                PendingInteractionType.MEMBER_INVITE ->
                                                                        WorldSettingsRuntimeOperation.PENDING_INVITE.name
                                                        },
                                                        ROUTE_TARGET_UUID to entry.playerUuid.toString(),
                                                        ROUTE_DECISION_ID to entry.pendingDecisionId.toString(),
                                                ),
                                        ),
                                )
                        } else {
                                val capabilityArguments = mapOf(
                                        MemberManagementCapabilityContract.WORLD_UUID_ARGUMENT to worldData.uuid.toString(),
                                        MemberManagementCapabilityContract.TARGET_PLAYER_UUID_ARGUMENT to entry.playerUuid.toString(),
                                )
                                val service = CCSystem.getAPI().getMenuCapabilityService()
                                val capabilityView = resolveMemberManagementHostAugmentation(
                                        service.definitions(MemberManagementCapabilityContract.PLACEMENT),
                                ) { definition ->
                                        service.resolve(
                                                        definition.capabilityId,
                                                        player,
                                                        arguments = capabilityArguments,
                                        )?.requireExplicitActionSafety()
                                }
                                inventory.setElement(
                                        createMemberEntrySpec(
                                                player, slot, entry.playerUuid,
                                                entry.role ?: lang.getMessage(player, MyworldRoleKeys.ROLE_MEMBER),
                                                canManageRoles, capabilityView, capabilityArguments,
                                        ),
                                )
                        }
                }

                // ナビゲーション
                if (currentPage > 0) {
                        inventory.setItem(
                                layout.previousPageSlot,
                                pageItemSpec(player, previous = true),
                        )
                        inventory.bindRuntimeOperation(
                                layout.previousPageSlot,
                                WorldSettingsRuntimeOperation.PAGE,
                                payload = mapOf(ROUTE_PAGE to (currentPage - 1).toString()),
                        )
                }
                if (currentPage + 1 < pageLayout.totalPages) {
                        inventory.setItem(
                                layout.nextPageSlot,
                                pageItemSpec(player, previous = false),
                        )
                        inventory.bindRuntimeOperation(
                                layout.nextPageSlot,
                                WorldSettingsRuntimeOperation.PAGE,
                                payload = mapOf(ROUTE_PAGE to (currentPage + 1).toString()),
                        )
                }

                // 戻ると招待の位置は共通レイアウトから取得し、一覧本文の行数変更に追従させる。
                inventory.setElement(
                        CCSystem.getAPI().getGuiElementService().backEntry(
                                player,
                                footerStart + 4,
                                plugin.menuConfigManager.getIconMaterial("world_settings", "back", Material.REDSTONE),
                        )
                )

                // メンバー招待ボタン
                val canForceAddMember = PermissionManager.canForceAddMember(player)
                inventory.setMenuEntry(
                        player,
                        GuiMenuEntrySpec(
                                slot = footerStart + 6,
                                material = Material.PAPER,
                                name = me.awabi2048.myworldmanager.util.fixedLabelName(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_INVITE_NAME), GuiNameStyle.DEFAULT),
                                role = GuiElementRole.ACTION,
                                description = listOf(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_INVITE_DESC)),
                                actions = buildList {
                                        add(menuGestureAction(
                                                ACTION_RUNTIME_DISPATCH,
                                                MenuGesture.PLAIN_LEFT_RIGHT,
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_INVITE_ACTION_NORMAL),
                                                mapOf(ROUTE_OPERATION to WorldSettingsRuntimeOperation.INVITE_MEMBER.name),
                                                safety = MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE,
                                        ))
                                        if (canForceAddMember) add(menuGestureAction(
                                                ACTION_RUNTIME_DISPATCH,
                                                MenuGesture.SHIFT_LEFT_RIGHT,
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_INVITE_ACTION_FORCE),
                                                mapOf(ROUTE_OPERATION to WorldSettingsRuntimeOperation.INVITE_MEMBER.name),
                                                safety = MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE,
                                        ))
                                },
                                // 強制招待操作が無い場合だけ、通常操作を単一操作として案内します。
                                interactionGuidance = GuiInteractionGuidance.SINGLE_ACTION_CLICK,
                        ),
                )

                if (isAdminFlow) {
                        val ownerName =
                                PlayerNameUtil.getNameOrDefault(
                                        worldData.owner,
                                        lang.getMessage(player, CommonKeys.GENERAL_UNKNOWN)
                                )
                        inventory.setMenuEntry(
                                player,
                                GuiMenuEntrySpec(
                                        slot = footerStart + 2,
                                        material = Material.NAME_TAG,
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_ADMIN_OWNER_RESET_NAME), GuiNameStyle.DEFAULT),
                                        role = GuiElementRole.ACTION,
                                        data = listOf(GuiMenuEntryData(
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_ADMIN_OWNER_RESET_CURRENT_OWNER),
                                                ownerName,
                                                GuiValueTone.PRIMARY,
                                        )),
                                        actions = listOf(menuGestureAction(
                                                ACTION_RUNTIME_DISPATCH,
                                                MenuGesture.ANY,
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_ADMIN_OWNER_RESET_ACTION),
                                                mapOf(ROUTE_OPERATION to WorldSettingsRuntimeOperation.MEMBER_OWNER_RESET.name),
                                                safety = MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE,
                                        )),
                                ),
                        )
                }

                // 背景埋め
                val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
                for (i in 9 until footerStart) {
                        if (!inventory.isOccupied(i)) inventory.setItem(i, grayPane)
                }

                return InventoryMenuView(
                        size = inventory.size,
                        title = GuiHelper.inventoryTitle(title),
                        elements = inventory.elements(),
                        standardFrame = false,
                        playerInventoryInteraction = PlayerInventoryInteraction.INTERACTIVE,
                )
        }

        fun openMemberPendingInviteCancelConfirmation(
                player: Player,
                worldData: WorldData,
                targetUuid: UUID,
                decisionId: UUID
        ) {
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.MEMBER_PENDING_INVITE_CANCEL_CONFIRM,
                        isGui = true
                )
                runtime.navigate(
                        player,
                        runtimeRoute(
                                WorldSettingsRuntimeScreen.MEMBER_PENDING_INVITE_CANCEL_CONFIRM,
                                worldData.uuid,
                                targetUuid = targetUuid,
                                decisionId = decisionId,
                        ),
                )
        }

        private fun renderMemberPendingInviteCancelConfirmation(
                player: Player,
                targetUuid: UUID,
        ): InventoryMenuView {
                val lang = plugin.languageManager
                val targetName = PlayerNameUtil.getNameOrDefault(targetUuid, lang.getMessage(player, CommonKeys.GENERAL_UNKNOWN))
                val title = lang.getMessage(player, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_PENDING_CANCEL_CONFIRM_TITLE)
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size, player)
                inventory.applyStandardFrame()

                inventory.setDisplay(
                        GuiMenuDisplaySpec(
                                slot = 22,
                                item = GuiItemSpec(
                                        material = Material.PLAYER_HEAD,
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(
                                                lang.getMessage(
                                                        player,
                                                        MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_PENDING_ITEM_NAME,
                                                        mapOf("player" to targetName),
                                                ),
                                                GuiNameStyle.DEFAULT,
                                        ),
                                        lore = me.awabi2048.myworldmanager.util.semanticLore(
                                                listOf(
                                                        GuiLoreLine.Warning(
                                                                lang.getMessage(
                                                                        player,
                                                                        MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_PENDING_CANCEL_CONFIRM_BODY,
                                                                        mapOf("player" to targetName),
                                                                ),
                                                        ),
                                                ),
                                                GuiLoreFrame.BOTH,
                                        ),
                                        role = GuiElementRole.CONTENT,
                                        amount = 1,
                                ),
                                playerHeadOwner = targetUuid,
                        ),
                )

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_PENDING_CANCEL_CONFIRM_CANCEL),
                                GuiLoreSpec.None,
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_PENDING_CANCEL_CONFIRM_CONFIRM),
                                GuiLoreSpec.None,
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )

                inventory.bindConfirmation(confirmSlot = 24, cancelSlot = 20)
                return runtimeView(title, inventory)
        }

        fun openMemberRemoveConfirmation(
                player: Player,
                worldData: WorldData,
                targetUuid: java.util.UUID
        ) {
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.MEMBER_REMOVE_CONFIRM,
                        isGui = true
                )
                runtime.navigate(
                        player,
                        runtimeRoute(
                                WorldSettingsRuntimeScreen.MEMBER_REMOVE_CONFIRM,
                                worldData.uuid,
                                targetUuid = targetUuid,
                        ),
                )
        }

        private fun renderMemberRemoveConfirmation(
                player: Player,
                worldData: WorldData,
                targetUuid: UUID,
        ): InventoryMenuView {
                val lang = plugin.languageManager
                val targetName = PlayerNameUtil.getNameOrDefault(targetUuid, lang.getMessage(player, CommonKeys.GENERAL_UNKNOWN))
                val title =
                        lang.getMessage(
                                player,
                                MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_REMOVE_CONFIRM_TITLE,
                                mapOf("player" to targetName)
                        )
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size, player)
                inventory.applyStandardFrame()

                val lore = me.awabi2048.myworldmanager.util.semanticLore(listOf(
                        GuiLoreLine.Warning(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_REMOVE_CONFIRM_QUESTION)),
                        GuiLoreLine.Data(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_REMOVE_CONFIRM_PLAYER_LABEL), targetName, "§f"),
                        GuiLoreLine.Data(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_REMOVE_CONFIRM_WORLD_LABEL), worldData.name, "§f"),
                        GuiLoreLine.Danger(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_REMOVE_CONFIRM_ACCESS_WARNING))
                ), GuiLoreFrame.BOTH)

                val infoItem =
                        createItemComponent(
                                Material.PLAYER_HEAD,
                                lang.getMessage(
                                        player,
                                        MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_REMOVE_CONFIRM_TITLE,
                                        mapOf(
                                                "player" to PlayerNameUtil.getNameOrDefault(targetUuid, lang.getMessage(player, CommonKeys.GENERAL_UNKNOWN))

                                        )
                                ),
                                lore,
                                ItemTag.TYPE_GUI_INFO
                        )
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(
                                        player,
                                        MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_REMOVE_CONFIRM_CANCEL
                                ),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(GuiLoreLine.Text(
                                        lang.getMessage(
                                                player,
                                                MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_REMOVE_CONFIRM_CANCEL_DESC
                                        )
                                )), GuiLoreFrame.NONE),
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(
                                        player,
                                        MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_REMOVE_CONFIRM_CONFIRM
                                ),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(GuiLoreLine.Warning(
                                        lang.getMessage(
                                                player,
                                                MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_REMOVE_CONFIRM_CONFIRM_DESC
                                        )
                                )), GuiLoreFrame.NONE),
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )


                inventory.bindConfirmation(confirmSlot = 24, cancelSlot = 20)
                return runtimeView(title, inventory)
        }

        fun openMemberTransferConfirmation(
                player: Player,
                worldData: WorldData,
                targetUuid: java.util.UUID
        ) {
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.MEMBER_TRANSFER_CONFIRM,
                        isGui = true
                )
                runtime.navigate(
                        player,
                        runtimeRoute(
                                WorldSettingsRuntimeScreen.MEMBER_TRANSFER_CONFIRM,
                                worldData.uuid,
                                targetUuid = targetUuid,
                        ),
                )
        }

        private fun renderMemberTransferConfirmation(
                player: Player,
                worldData: WorldData,
                targetUuid: UUID,
        ): InventoryMenuView {
                val lang = plugin.languageManager
                val targetName = PlayerNameUtil.getNameOrDefault(targetUuid, lang.getMessage(player, CommonKeys.GENERAL_UNKNOWN))
                val title =
                        lang.getMessage(
                                player,
                                MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_TRANSFER_CONFIRM_TITLE,
                                mapOf("player" to targetName)
                        )
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size, player)
                inventory.applyStandardFrame()

                val lore = me.awabi2048.myworldmanager.util.semanticLore(listOf(
                        GuiLoreLine.Warning(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_TRANSFER_CONFIRM_QUESTION)),
                        GuiLoreLine.Data(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_TRANSFER_CONFIRM_PLAYER_LABEL), targetName, "§f"),
                        GuiLoreLine.Data(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_TRANSFER_CONFIRM_WORLD_LABEL), worldData.name, "§f"),
                        GuiLoreLine.Danger(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_TRANSFER_CONFIRM_OWNER_WARNING))
                ), GuiLoreFrame.BOTH)

                val infoItem =
                        createItemComponent(
                                Material.PLAYER_HEAD,
                                lang.getMessage(
                                        player,
                                        MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_TRANSFER_CONFIRM_TITLE,
                                        mapOf("player" to targetName)
                                ),
                                lore,
                                ItemTag.TYPE_GUI_INFO
                        )
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(
                                        player,
                                        MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_TRANSFER_CONFIRM_CANCEL
                                ),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(GuiLoreLine.Text(
                                        lang.getMessage(
                                                player,
                                                MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_TRANSFER_CONFIRM_CANCEL_DESC
                                        )
                                )), GuiLoreFrame.NONE),
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(
                                        player,
                                        MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_TRANSFER_CONFIRM_CONFIRM
                                ),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(GuiLoreLine.Warning(
                                        lang.getMessage(
                                                player,
                                                MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_TRANSFER_CONFIRM_CONFIRM_DESC
                                        )
                                )), GuiLoreFrame.NONE),
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )

                inventory.bindConfirmation(confirmSlot = 24, cancelSlot = 20)
                return runtimeView(title, inventory)
        }

        private fun createMemberEntrySpec(
                viewer: Player,
                slot: Int,
                uuid: java.util.UUID,
                role: String,
                isOwner: Boolean,
                capabilityView: com.awabi2048.ccsystem.api.gui.ResolvedMenuCapability? = null,
                capabilityArguments: Map<String, String> = emptyMap(),
        ): MenuElement {
                val lang = plugin.languageManager
                val player = Bukkit.getOfflinePlayer(uuid)
                val stats = plugin.playerStatsRepository.findByUuid(uuid)
                val isOnline = player.isOnline
                val color = if (isOnline) "§a" else "§c"

                var displayName = player.name
                if (displayName == null) {
                        val stats = plugin.playerStatsRepository.findByUuid(uuid)
                        displayName = stats.lastName ?: lang.getMessage(viewer, CommonKeys.GENERAL_UNKNOWN)
                }

                val payload = mapOf(
                        ROUTE_OPERATION to WorldSettingsRuntimeOperation.MEMBER.name,
                        ROUTE_TARGET_UUID to uuid.toString(),
                        MemberManagementCapabilityContract.WORLD_UUID_ARGUMENT to requireNotNull(
                                capabilityArguments[MemberManagementCapabilityContract.WORLD_UUID_ARGUMENT],
                        ),
                        MemberManagementCapabilityContract.TARGET_PLAYER_UUID_ARGUMENT to uuid.toString(),
                )
                val actions = mutableListOf<GuiMenuActionIntent>()
                val presentationActionLines = mutableListOf<GuiLoreLine.Interaction>()
                val hostActions = mutableListOf<MenuInteraction.Action>()
                fun addHostAction(
                        gesture: MenuGesture,
                        label: String,
                        safety: MenuActionSafety,
                ) {
                        val reversibleContract = if (safety == MenuActionSafety.REVERSIBLE) {
                                MwmMenuActionSemantics.contract("member-role")
                        } else null
                        actions += menuGestureAction(
                                ACTION_RUNTIME_DISPATCH,
                                gesture,
                                label,
                                payload,
                                safety = safety,
                                reversibleContract = reversibleContract,
                        )
                        presentationActionLines += GuiLoreLine.Interaction(viewer, gesture.clicks, label)
                        hostActions += MenuInteraction.Action(
                                actionId = ACTION_RUNTIME_DISPATCH,
                                acceptedClicks = gesture.clicks,
                                payload = payload,
                                safety = safety,
                                safetyByClick = gesture.clicks.associateWith { safety },
                                reversibleContract = reversibleContract,
                        )
                }
                capabilityView?.actions?.forEach { action ->
                        val gesture = MenuGesture.fromClicks(action.trigger.clicks)
                        actions += menuGestureAction(
                                ACTION_RUNTIME_DISPATCH,
                                gesture,
                                action.text,
                                payload,
                                safety = action.safety,
                                reversibleContract = action.reversibleContract,
                        )
                        presentationActionLines += GuiLoreLine.Interaction(viewer, gesture.clicks, action.text)
                }
                if (capabilityView == null && isOwner && role != lang.getMessage(viewer, MyworldRoleKeys.ROLE_OWNER)) {
                        val nextRole = if (role == lang.getMessage(null as Player?, MyworldRoleKeys.ROLE_MEMBER)) {
                                lang.getMessage(null as Player?, MyworldRoleKeys.ROLE_MODERATOR)
                        } else {
                                lang.getMessage(null as Player?, MyworldRoleKeys.ROLE_MEMBER)
                        }
                        addHostAction(
                                MenuGesture.PLAIN_LEFT,
                                lang.getMessage(viewer, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_ITEM_ACTION_CHANGE_ROLE, mapOf("next_role" to nextRole)),
                                MenuActionSafety.REVERSIBLE,
                        )
                }
                if (isOwner && role != lang.getMessage(viewer, MyworldRoleKeys.ROLE_OWNER)) {
                        addHostAction(
                                MenuGesture.SHIFT_LEFT,
                                lang.getMessage(viewer, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_ITEM_ACTION_TRANSFER_OWNER),
                                MenuActionSafety.CONFIRM_ENTRY,
                        )
                        addHostAction(
                                MenuGesture.SHIFT_RIGHT,
                                lang.getMessage(viewer, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_ITEM_ACTION_REMOVE_MEMBER),
                                MenuActionSafety.CONFIRM_ENTRY,
                        )
                }
                val targetInfoLines = buildList {
                        if (isOnline) {
                                add(GuiLoreLine.Text(lang.getMessage(viewer, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_ITEM_ONLINE_LABEL)))
                        } else {
                                add(GuiLoreLine.Data(
                                        lang.getMessage(viewer, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_ITEM_LAST_ONLINE_LABEL),
                                        stats.lastOnline?.let { formatStoredDateTimeForPlayer(viewer, it) }
                                                ?: lang.getMessage(viewer, CommonKeys.GENERAL_UNKNOWN),
                                        GuiValueTone.DEFAULT.colorCode,
                                ))
                        }
                        add(GuiLoreLine.Data(
                                lang.getMessage(viewer, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_ITEM_ROLE_LABEL),
                                role,
                                GuiValueTone.DEFAULT.colorCode,
                        ))
                }
                val hostBlocks = listOf(GuiLoreBlock(targetInfoLines))
                val hostItem = GuiItemSpec(
                        material = Material.PLAYER_HEAD,
                        name = GuiNameSpec.TargetIdentity(Component.text("$color$displayName").decoration(TextDecoration.ITALIC, false)),
                        lore = GuiLoreSpec.Blocks(hostBlocks),
                        role = if (actions.isEmpty()) GuiElementRole.CONTENT else GuiElementRole.ACTION,
                        amount = 1,
                )
                val composition = composeMemberManagementHost(
                        capabilityView,
                        hostItem,
                        hostBlocks,
                        presentationActionLines,
                        capabilityArguments,
                )
                val spec = GuiMenuEntrySpec(
                        slot = slot,
                        material = Material.PLAYER_HEAD,
                        name = GuiNameSpec.TargetIdentity(Component.text("$color$displayName").decoration(TextDecoration.ITALIC, false)),
                        role = if (actions.isEmpty()) GuiElementRole.CONTENT else GuiElementRole.ACTION,
                        semanticLoreBlocks = composition.semanticLoreBlocks,
                        actions = actions,
                        playerHeadOwner = uuid,
                )
                val element = CCSystem.getAPI().getGuiElementService().menuEntry(viewer, spec).copyWithPresentationSemantics(
                        interaction = memberManagementEntryInteraction(
                                capabilityView,
                                hostActions,
                                capabilityArguments,
                        ),
                )
                return if (capabilityView == null) element
                else element.withCapabilityComposition(capabilityView, composition.snapshot)
        }

        private fun formatPendingInviteDateTimeForPlayer(player: Player, timestamp: Long): String {
                val dateTime = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()
                return dateTime.format(pendingInviteDateTimeFormatterFor(player))
        }

        private fun toneFor(colorCode: String): GuiValueTone =
                GuiValueTone.entries.firstOrNull { it.colorCode == colorCode } ?: GuiValueTone.DEFAULT

        private fun pendingInviteDateTimeFormatterFor(player: Player): DateTimeFormatter {
                val language = plugin.languageManager.resolveLocale(player).lowercase(Locale.ROOT)
                return if (language == "ja_jp") {
                        DateTimeFormatter.ofPattern("yyyy/M/d H:mm")
                } else {
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                }
        }

        private fun formatStoredDateTimeForPlayer(player: Player, stored: String): String {
                val parsed = runCatching {
                        java.time.LocalDateTime.parse(
                                stored,
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        )
                }.getOrNull() ?: return stored
                val language = plugin.languageManager.resolveLocale(player).lowercase(Locale.ROOT)
                val formatter =
                        if (language == "ja_jp") {
                                DateTimeFormatter.ofPattern("yyyy/M/d HH:mm")
                        } else {
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                        }
                return parsed.format(formatter)
        }

        fun openVisitorManagement(
                player: Player,
                worldData: WorldData,
                page: Int = 0,
                replaceCurrent: Boolean = false,
        ) {
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.MANAGE_VISITORS,
                        isGui = true
                )
                val targetRoute = runtimeRoute(
                        WorldSettingsRuntimeScreen.VISITOR_MANAGEMENT,
                        worldData.uuid,
                        page = page,
                )
                if (replaceCurrent) {
                        runtime.replace(player, targetRoute)
                } else {
                        runtime.navigate(player, targetRoute)
                }
        }

        private fun renderVisitorManagement(
                player: Player,
                worldData: WorldData,
                page: Int,
        ): InventoryMenuView {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, MyworldGuiSettingsKeys.GUI_VISITOR_MANAGEMENT_TITLE)
                val world = Bukkit.getWorld("my_world.${worldData.uuid}")
                val visitorPlayers =
                        world?.players.orEmpty().filter {
                                it.uniqueId != worldData.owner &&
                                        !worldData.moderators.contains(it.uniqueId) &&
                                        !worldData.members.contains(it.uniqueId)
                        }

                val visitorPage = CCSystem.getAPI().getGuiLayoutService()
                        .sevenColumnPage(visitorPlayers.size, page)
                val layout = visitorPage.layout
                val currentPageVisitors = visitorPlayers
                        .drop(visitorPage.startIndex)
                        .take(visitorPage.itemCount)
                val inventory = RuntimeItemBuffer(layout.size, player)
                inventory.applyStandardFrame()

                // プレイヤーリストの描画
                val isAdminFlow = plugin.settingsSessionManager.getSession(player)?.isAdminFlow == true
                val canKick =
                        worldData.owner == player.uniqueId ||
                                worldData.moderators.contains(player.uniqueId) ||
                                isAdminFlow

                currentPageVisitors.forEachIndexed { index, visitor ->
                        val slot = layout.itemSlots[index]
                        inventory.setMenuEntry(player, createVisitorEntrySpec(player, slot, visitor.uniqueId, canKick))
                }

                // ナビゲーション
                if (visitorPage.page > 0) {
                        inventory.setItem(
                                layout.previousPageSlot,
                                createItem(
                                        Material.ARROW,
                                        lang.getMessage(player, CommonKeys.GUI_COMMON_PREV_PAGE),
                                        GuiLoreSpec.None,
                                        null
                                )
                        )
                        inventory.bindRuntimeOperation(
                                layout.previousPageSlot,
                                WorldSettingsRuntimeOperation.PAGE,
                                payload = mapOf(ROUTE_PAGE to (visitorPage.page - 1).toString()),
                        )
                }
                if (visitorPage.page < visitorPage.totalPages - 1) {
                        inventory.setItem(
                                layout.nextPageSlot,
                                createItem(
                                        Material.ARROW,
                                        lang.getMessage(player, CommonKeys.GUI_COMMON_NEXT_PAGE),
                                        GuiLoreSpec.None,
                                        null
                                )
                        )
                        inventory.bindRuntimeOperation(
                                layout.nextPageSlot,
                                WorldSettingsRuntimeOperation.PAGE,
                                payload = mapOf(ROUTE_PAGE to (visitorPage.page + 1).toString()),
                        )
                }

                // 戻るボタン
                inventory.setElement(
                        CCSystem.getAPI().getGuiElementService().backEntry(
                                player,
                                layout.actionSlot,
                                plugin.menuConfigManager.getIconMaterial("world_settings", "back", Material.REDSTONE),
                        )
                )

                // 背景埋め
                val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
                for (i in 9 until layout.size - 9) {
                        if (!inventory.isOccupied(i)) inventory.setItem(i, grayPane)
                }

                return runtimeView(title, inventory)
        }

        fun openVisitorKickConfirmation(
                player: Player,
                worldData: WorldData,
                targetUuid: java.util.UUID
        ) {
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.VISITOR_KICK_CONFIRM,
                        isGui = true
                )
                runtime.navigate(
                        player,
                        runtimeRoute(
                                WorldSettingsRuntimeScreen.VISITOR_KICK_CONFIRM,
                                worldData.uuid,
                                targetUuid = targetUuid,
                        ),
                )
        }

        private fun renderVisitorKickConfirmation(
                player: Player,
                targetUuid: UUID,
        ): InventoryMenuView {
                val lang = plugin.languageManager
                val targetName = PlayerNameUtil.getNameOrDefault(targetUuid, lang.getMessage(player, CommonKeys.GENERAL_UNKNOWN))
                val title =
                        lang.getMessage(
                                player,
                                MyworldGuiSettingsKeys.GUI_VISITOR_MANAGEMENT_KICK_CONFIRM_TITLE,
                                mapOf("player" to targetName)
                        )
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size, player)
                inventory.applyStandardFrame()

                inventory.setItem(
                        22,
                        createItem(
                                Material.PAPER,
                                lang.getMessage(
                                        player,
                                        MyworldGuiSettingsKeys.GUI_VISITOR_MANAGEMENT_KICK_CONFIRM_QUESTION
                                ),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(
                                        GuiLoreLine.Data(
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_VISITOR_MANAGEMENT_KICK_CONFIRM_PLAYER_LABEL),
                                                targetName,
                                                "§f"
                                        ),
                                        GuiLoreLine.Spacer,
                                        GuiLoreLine.Metadata("UUID", targetUuid)
                                ), GuiLoreFrame.BOTH),
                                ItemTag.TYPE_GUI_INFO
                        )
                )

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(
                                        player,
                                        MyworldGuiSettingsKeys.GUI_VISITOR_MANAGEMENT_KICK_CONFIRM_CANCEL
                                ),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(GuiLoreLine.Text(
                                        lang.getMessage(
                                                player,
                                                MyworldGuiSettingsKeys.GUI_VISITOR_MANAGEMENT_KICK_CONFIRM_CANCEL_DESC
                                        )
                                )), GuiLoreFrame.NONE),
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(
                                        player,
                                        MyworldGuiSettingsKeys.GUI_VISITOR_MANAGEMENT_KICK_CONFIRM_CONFIRM
                                ),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(GuiLoreLine.Warning(
                                        lang.getMessage(
                                                player,
                                                MyworldGuiSettingsKeys.GUI_VISITOR_MANAGEMENT_KICK_CONFIRM_CONFIRM_DESC
                                        )
                                )), GuiLoreFrame.NONE),
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )

                inventory.bindConfirmation(confirmSlot = 24, cancelSlot = 20)
                return runtimeView(title, inventory)
        }

        private fun createVisitorEntrySpec(
                viewer: Player,
                slot: Int,
                uuid: java.util.UUID,
                canKick: Boolean
        ): GuiMenuEntrySpec {
                val lang = plugin.languageManager
                val player = Bukkit.getOfflinePlayer(uuid)
                val isOnline = player.isOnline
                val onlineColor = lang.getMessage(viewer, MyworldPublishLevelKeys.PUBLISH_LEVEL_COLOR_ONLINE)
                val offlineColor = lang.getMessage(viewer, MyworldPublishLevelKeys.PUBLISH_LEVEL_COLOR_OFFLINE)
                val color = if (isOnline) onlineColor else offlineColor

                val statusText =
                        if (isOnline) lang.getMessage(viewer, CommonKeys.STATUS_ONLINE)
                        else lang.getMessage(viewer, CommonKeys.STATUS_OFFLINE)
                return GuiMenuEntrySpec(
                        slot = slot,
                        material = Material.PLAYER_HEAD,
                        name = GuiNameSpec.FixedLabel(
                                LegacyComponentSerializer.legacySection().deserialize(
                                        "$color${player.name ?: lang.getMessage(viewer, CommonKeys.GENERAL_UNKNOWN)}"
                                ).decoration(TextDecoration.ITALIC, false),
                        ),
                        role = if (canKick) GuiElementRole.ACTION else GuiElementRole.CONTENT,
                        data = listOf(GuiMenuEntryData(
                                lang.getMessage(viewer, MyworldGuiSettingsKeys.GUI_MEMBER_MANAGEMENT_ITEM_ONLINE_LABEL),
                                statusText,
                                if (isOnline) GuiValueTone.SUCCESS else GuiValueTone.DANGER,
                        )),
                        actions = if (canKick) listOf(menuGestureAction(
                                ACTION_RUNTIME_DISPATCH,
                                MenuGesture.ANY,
                                lang.getMessage(viewer, MyworldGuiSettingsKeys.GUI_VISITOR_MANAGEMENT_ITEM_KICK),
                                mapOf(
                                        ROUTE_OPERATION to WorldSettingsRuntimeOperation.VISITOR.name,
                                        ROUTE_TARGET_UUID to uuid.toString(),
                                ),
                                safety = MenuActionSafety.CONFIRM_ENTRY,
                        )) else emptyList(),
                        playerHeadOwner = uuid,
                )
        }

        private fun createItem(
                material: Material,
                name: String,
                lore: GuiLoreSpec,
                tag: String?
        ): GuiItemSpec =
                GuiItemSpec(
                        material = material,
                        name = if (tag == ItemTag.TYPE_GUI_CONFIRM || tag == ItemTag.TYPE_GUI_CANCEL) {
                                me.awabi2048.myworldmanager.util.confirmationButtonName(name)
                        } else {
                                me.awabi2048.myworldmanager.util.fixedLabelName(name, GuiNameStyle.DEFAULT)
                        },
                        lore = lore,
                        role = GuiElementRole.CONTENT,
                        amount = 1,
                )

        private data class BorderInfo(
                val centerX: Double,
                val centerZ: Double,
                val size: Double
        )

        private fun buildCurrentBorderInfo(worldData: WorldData, currentLevel: Int): BorderInfo {
                val worldName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
                val world = Bukkit.getWorld(worldName)
                if (world != null) {
                        val border = world.worldBorder
                        return BorderInfo(
                                centerX = border.center.x,
                                centerZ = border.center.z,
                                size = border.size
                        )
                }

                val center =
                        worldData.borderCenterPos
                                ?: worldData.spawnPosMember
                                ?: worldData.spawnPosGuest
                                ?: plugin.server.worlds.firstOrNull()?.spawnLocation
                val initialSize = plugin.config.getDouble("expansion.initial_size", 100.0)
                val size = initialSize * Math.pow(2.0, currentLevel.toDouble())
                return BorderInfo(
                        centerX = center?.x ?: 0.0,
                        centerZ = center?.z ?: 0.0,
                        size = size
                )
        }

        private fun formatDecimal(value: Double): String {
                val rounded = Math.round(value)
                return if (kotlin.math.abs(value - rounded.toDouble()) < 0.000001) {
                        rounded.toString()
                } else {
                        String.format(Locale.US, "%.1f", value)
                }
        }

        private fun createItemComponent(
                material: Material,
                name: String,
                lore: GuiLoreSpec,
                tag: String?
        ): GuiItemSpec =
                GuiItemSpec(
                        material = material,
                        name = if (tag == ItemTag.TYPE_GUI_CONFIRM || tag == ItemTag.TYPE_GUI_CANCEL) {
                                me.awabi2048.myworldmanager.util.confirmationButtonName(name)
                        } else {
                                me.awabi2048.myworldmanager.util.fixedLabelName(name, GuiNameStyle.DEFAULT)
                        },
                        lore = lore,
                        role = GuiElementRole.CONTENT,
                        amount = 1,
                )

        fun openCriticalSettings(player: Player, worldData: WorldData) {
                if (!plugin.playerStatsRepository.findByUuid(player.uniqueId).criticalSettingsEnabled) {
                        return
                }
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.CRITICAL_SETTINGS,
                        isGui = true
                )
                runtime.navigate(
                        player,
                        runtimeRoute(WorldSettingsRuntimeScreen.CRITICAL_SETTINGS, worldData.uuid),
                )
        }

        private fun renderCriticalSettings(
                player: Player,
                worldData: WorldData,
        ): InventoryMenuView {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, MyworldGuiCommonKeys.GUI_CRITICAL_TITLE)
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size, player)
                val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
                inventory.applyStandardFrame()

                val middleGrayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
                for (i in 9..35) inventory.setItem(i, middleGrayPane)

                // 払い戻し額の計算
                val refundRate = plugin.config.getDouble("critical_settings.refund_percentage", 0.5)
                 val refund = if (MyWorldManagerApi.isWorldPointEconomyEnabled()) {
                         (worldData.cumulativePoints * refundRate).toInt()
                 } else {
                         0
                 }
                val percent = (refundRate * 100).toInt()

                // プレイヤーごとのクールタイムチェック
                val cooldownHours = plugin.config.getLong("critical_settings.archive_cooldown_hours", 24L)
                val playerStats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                val lastActionAt = playerStats.lastArchiveActionAt
                val dtFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                val isOnCooldown = if (lastActionAt != null) {
                    try {
                        val lastAction = java.time.LocalDateTime.parse(lastActionAt, dtFormatter)
                        val elapsed = java.time.Duration.between(lastAction, java.time.LocalDateTime.now()).toHours()
                        elapsed < cooldownHours
                    } catch (e: Exception) { false }
                } else false
                val hoursRemaining = if (isOnCooldown && lastActionAt != null) {
                    try {
                        val lastAction = java.time.LocalDateTime.parse(lastActionAt, dtFormatter)
                        val elapsed = java.time.Duration.between(lastAction, java.time.LocalDateTime.now()).toHours()
                        (cooldownHours - elapsed).coerceAtLeast(0L)
                    } catch (e: Exception) { 0L }
                } else 0L

                val archiveDescription = lang.getMessageList(player, MyworldGuiCommonKeys.GUI_CRITICAL_ARCHIVE_WORLD_DESCRIPTION) +
                        if (isOnCooldown) listOf(lang.getMessage(player, MyworldGuiCommonKeys.GUI_CRITICAL_ARCHIVE_WORLD_REMAINING, mapOf("hours_remaining" to hoursRemaining))) else emptyList()
                val archiveWarnings = if (isOnCooldown) {
                        listOf(lang.getMessage(player, MyworldGuiCommonKeys.GUI_CRITICAL_ARCHIVE_WORLD_COOLDOWN_WARNING, mapOf("cooldown_hours" to cooldownHours)))
                } else emptyList()

                // 動的スロット配置: ボーダー拡張の有無に応じて決定
                // - 拡張あり (level > 0): スロット20=リセット, 22=アーカイブ, 24=削除
                // - 拡張なし (level == 0): スロット20=削除, 24=アーカイブ
                val isExpansionEnabled = worldData.borderExpansionLevel > 0
                val hasSpecialExpansion = worldData.borderExpansionLevel == WorldData.EXPANSION_LEVEL_SPECIAL

                if (isExpansionEnabled || hasSpecialExpansion) {
                    // 拡張リセットボタン (スロット20)
                    val currentLevel = worldData.borderExpansionLevel
                    if (currentLevel > 0) {
                        val resetRefund = (calculateTotalExpansionCost(currentLevel) * refundRate).toInt()
                        inventory.setMenuEntry(
                                player,
                                GuiMenuEntrySpec(
                                        slot = 20,
                                        material = Material.BARRIER,
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(lang.getMessage(player, MyworldGuiCommonKeys.GUI_CRITICAL_RESET_EXPANSION_DISPLAY), GuiNameStyle.DEFAULT),
                                        role = GuiElementRole.ACTION,
                                        data = listOf(
                                                GuiMenuEntryData(lang.getMessage(player, MyworldGuiCommonKeys.GUI_CRITICAL_RESET_EXPANSION_LEVEL_LABEL), currentLevel, GuiValueTone.PRIMARY),
                                                GuiMenuEntryData(lang.getMessage(player, MyworldGuiCommonKeys.GUI_CRITICAL_RESET_EXPANSION_REFUND_LABEL), resetRefund, GuiValueTone.PRIMARY),
                                        ),
                                        warnings = listOf(lang.getMessage(player, MyworldGuiCommonKeys.GUI_CRITICAL_RESET_EXPANSION_WARNING)),
                                        actions = listOf(menuGestureAction(
                                                ACTION_RUNTIME_DISPATCH,
                                                MenuGesture.ANY,
                                                lang.getMessage(player, MyworldGuiCommonKeys.GUI_CRITICAL_RESET_EXPANSION_ACTION),
                                                mapOf(ROUTE_OPERATION to WorldSettingsRuntimeOperation.RESET_EXPANSION.name),
                                                safety = MenuActionSafety.CONFIRM_ENTRY,
                                        )),
                                ),
                        )
                    } else {
                        inventory.setMenuEntry(
                                player,
                                GuiMenuEntrySpec(
                                        slot = 20,
                                        material = Material.BARRIER,
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(lang.getMessage(player, MyworldGuiCommonKeys.GUI_CRITICAL_RESET_EXPANSION_DISPLAY), GuiNameStyle.DEFAULT),
                                        role = GuiElementRole.CONTENT,
                                        warnings = listOf(lang.getMessage(player, MyworldGuiCommonKeys.GUI_CRITICAL_RESET_EXPANSION_UNAVAILABLE)),
                                ),
                        )
                    }

                    // アーカイブボタン (スロット22)
                    inventory.setMenuEntry(player, criticalActionSpec(
                            player, 22,
                            plugin.menuConfigManager.getIconMaterial("world_settings", "critical", Material.CHEST),
                            lang.getMessage(player, MyworldGuiCommonKeys.GUI_CRITICAL_ARCHIVE_WORLD_DISPLAY),
                            archiveDescription, archiveWarnings, !isOnCooldown,
                            WorldSettingsRuntimeOperation.ARCHIVE,
                            lang.getMessage(player, MyworldGuiCommonKeys.GUI_CRITICAL_ARCHIVE_WORLD_ACTION),
                    ))
                } else {
                    // 拡張なし: アーカイブは slot 24
                    inventory.setMenuEntry(player, criticalActionSpec(
                            player, 24,
                            plugin.menuConfigManager.getIconMaterial("world_settings", "critical", Material.CHEST),
                            lang.getMessage(player, MyworldGuiCommonKeys.GUI_CRITICAL_ARCHIVE_WORLD_DISPLAY),
                            archiveDescription, archiveWarnings, !isOnCooldown,
                            WorldSettingsRuntimeOperation.ARCHIVE,
                            lang.getMessage(player, MyworldGuiCommonKeys.GUI_CRITICAL_ARCHIVE_WORLD_ACTION),
                    ))
                }

                // 削除ボタン
                val ownerStats = plugin.playerStatsRepository.findByUuid(worldData.owner)
                val canDeleteWorld = !MyWorldManagerApi.isWorldSlotSystemEnabled() ||
                        ownerStats.unlockedWorldSlot > 0
                val deleteDisplayName = if (canDeleteWorld) {
                        lang.getMessage(player, MyworldGuiCommonKeys.GUI_CRITICAL_DELETE_WORLD_DISPLAY)
                } else {
                        "§8§m${lang.getMessage(player, MyworldGuiCommonKeys.GUI_CRITICAL_DELETE_WORLD_DISPLAY)}"
                }

                val deleteSlot = if (isExpansionEnabled || hasSpecialExpansion) 24 else 20
                inventory.setMenuEntry(
                        player,
                        GuiMenuEntrySpec(
                                slot = deleteSlot,
                                material = Material.LAVA_BUCKET,
                                name = me.awabi2048.myworldmanager.util.fixedLabelName(deleteDisplayName, GuiNameStyle.DEFAULT),
                                role = if (canDeleteWorld) GuiElementRole.ACTION else GuiElementRole.CONTENT,
                                description = listOf(lang.getMessage(player, MyworldGuiCommonKeys.GUI_CRITICAL_DELETE_WORLD_DESCRIPTION)) +
                                        if (canDeleteWorld) listOf(lang.getMessage(player, MyworldGuiCommonKeys.GUI_CRITICAL_DELETE_WORLD_REFUND_NOTE, mapOf("percent" to percent))) else emptyList(),
                                data = if (canDeleteWorld) {
                                        listOf(GuiMenuEntryData(lang.getMessage(player, MyworldGuiCommonKeys.GUI_CRITICAL_DELETE_WORLD_REFUND_LABEL), refund, GuiValueTone.PRIMARY))
                                } else {
                                        listOf(GuiMenuEntryData(lang.getMessage(player, MyworldGuiCommonKeys.GUI_CRITICAL_DELETE_WORLD_OWNER_SLOTS_LABEL), ownerStats.unlockedWorldSlot, GuiValueTone.PRIMARY))
                                },
                                warnings = listOf(lang.getMessage(player, if (canDeleteWorld) MyworldGuiCommonKeys.GUI_CRITICAL_DELETE_WORLD_WARNING else MyworldGuiCommonKeys.GUI_CRITICAL_DELETE_WORLD_UNAVAILABLE_SLOT)),
                                actions = if (canDeleteWorld) listOf(menuGestureAction(
                                        ACTION_RUNTIME_DISPATCH,
                                        MenuGesture.ANY,
                                        lang.getMessage(player, MyworldGuiCommonKeys.GUI_CRITICAL_DELETE_WORLD_ACTION),
                                        mapOf(ROUTE_OPERATION to WorldSettingsRuntimeOperation.DELETE_WORLD.name),
                                        safety = MenuActionSafety.CONFIRM_ENTRY,
                                )) else emptyList(),
                        ),
                )

                // 戻るボタン (スロット36から40へ移動)
                inventory.setElement(
                        CCSystem.getAPI().getGuiElementService().backEntry(
                                player,
                                40,
                                plugin.menuConfigManager.getIconMaterial("world_settings", "back", Material.REDSTONE),
                        )
                )

                return runtimeView(title, inventory)
        }

        private fun calculateTotalExpansionCost(level: Int): Int {
                return WorldRuntimePolicies.totalExpansionCost(plugin.config, level)
        }

        private fun criticalActionSpec(
                player: Player,
                slot: Int,
                material: Material,
                name: String,
                description: List<String>,
                warnings: List<String>,
                enabled: Boolean,
                operation: WorldSettingsRuntimeOperation,
                actionText: String,
        ): GuiMenuEntrySpec = GuiMenuEntrySpec(
                slot = slot,
                material = material,
                name = me.awabi2048.myworldmanager.util.fixedLabelName(name, GuiNameStyle.DEFAULT),
                role = if (enabled) GuiElementRole.ACTION else GuiElementRole.CONTENT,
                description = description,
                warnings = warnings,
                actions = if (enabled) listOf(menuGestureAction(
                        ACTION_RUNTIME_DISPATCH,
                        MenuGesture.ANY,
                        actionText,
                        mapOf(ROUTE_OPERATION to operation.name),
                        safety = criticalActionSafety(operation),
                )) else emptyList(),
        )

        private fun criticalActionSafety(operation: WorldSettingsRuntimeOperation): MenuActionSafety = when (operation) {
                WorldSettingsRuntimeOperation.ARCHIVE -> MenuActionSafety.CONFIRM_ENTRY
                else -> error("Critical action must declare a dedicated safety mapping: $operation")
        }

        fun openResetExpansionConfirmation(player: Player, worldData: WorldData) {
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.RESET_EXPANSION_CONFIRM,
                        isGui = true
                )
                runtime.navigate(
                        player,
                        runtimeRoute(WorldSettingsRuntimeScreen.RESET_EXPANSION_CONFIRM, worldData.uuid),
                )
        }

        private fun renderResetExpansionConfirmation(
                player: Player,
                worldData: WorldData,
        ): InventoryMenuView {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, MyworldGuiCommonKeys.GUI_CONFIRM_RESET_EXPANSION_TITLE)
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size, player)
                inventory.applyStandardFrame()

                val loreLines = mutableListOf<GuiLoreLine>(
                        GuiLoreLine.Warning(lang.getMessage(player, MyworldGuiCommonKeys.GUI_CONFIRM_RESET_EXPANSION_QUESTION))
                )
                loreLines += lang.getMessageList(player, MyworldGuiCommonKeys.GUI_CONFIRM_RESET_EXPANSION_DESCRIPTION).map(::dangerLine)
                if (worldData.hasModifiedBorderExpansion()) {
                        loreLines += lang.getMessageList(player, MyworldGuiCommonKeys.GUI_CONFIRM_RESET_EXPANSION_MODIFIED_WARNING).map(GuiLoreLine::Warning)
                }
                loreLines += getSpawnAdjustmentWarning(player, worldData, borderResetTargetForReset(worldData))
                val infoItem =
                        createItem(
                                Material.PAPER,
                                lang.getMessage(player, MyworldGuiCommonKeys.GUI_CONFIRM_RESET_EXPANSION_DISPLAY),
                                me.awabi2048.myworldmanager.util.semanticLore(loreLines, GuiLoreFrame.BOTH),
                                ItemTag.TYPE_GUI_INFO
                        )
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(player, CommonKeys.GUI_COMMON_CANCEL),
                                GuiLoreSpec.None,
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(player, CommonKeys.GUI_COMMON_CONFIRM),
                                GuiLoreSpec.None,
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )

                inventory.bindConfirmation(confirmSlot = 24, cancelSlot = 20)
                return runtimeView(title, inventory)
        }

        fun openResetExpansionSpawnUnsafeConfirmation(player: Player, worldData: WorldData) {
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.RESET_EXPANSION_CONFIRM_SPAWN_UNSAFE,
                        isGui = true
                )
                runtime.navigate(
                        player,
                        runtimeRoute(
                                WorldSettingsRuntimeScreen.RESET_EXPANSION_SPAWN_UNSAFE_CONFIRM,
                                worldData.uuid,
                        ),
                )
        }

        private fun renderResetExpansionSpawnUnsafeConfirmation(
                player: Player,
                worldData: WorldData,
        ): InventoryMenuView {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, MyworldGuiCommonKeys.GUI_CONFIRM_RESET_EXPANSION_SPAWN_UNSAFE_TITLE)
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size, player)
                inventory.applyStandardFrame()

                val loreLines = mutableListOf<GuiLoreLine>(
                        GuiLoreLine.Danger(lang.getMessage(player, MyworldGuiCommonKeys.GUI_CONFIRM_RESET_EXPANSION_SPAWN_UNSAFE_WARNING))
                )
                loreLines += lang.getMessageList(player, MyworldGuiCommonKeys.GUI_CONFIRM_RESET_EXPANSION_SPAWN_UNSAFE_DESCRIPTION).map(GuiLoreLine::Warning)
                if (worldData.hasModifiedBorderExpansion()) {
                        loreLines += lang.getMessageList(player, MyworldGuiCommonKeys.GUI_CONFIRM_RESET_EXPANSION_MODIFIED_WARNING).map(GuiLoreLine::Warning)
                }
                loreLines += getSpawnAdjustmentWarning(player, worldData, borderResetTargetForReset(worldData))
                val infoItem =
                        createItem(
                                Material.MAGMA_BLOCK,
                                lang.getMessage(player, MyworldGuiCommonKeys.GUI_CONFIRM_RESET_EXPANSION_SPAWN_UNSAFE_DISPLAY),
                                me.awabi2048.myworldmanager.util.semanticLore(loreLines, GuiLoreFrame.BOTH),
                                ItemTag.TYPE_GUI_INFO
                        )
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(player, CommonKeys.GUI_COMMON_CANCEL),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(GuiLoreLine.Text(lang.getMessage(player, CommonKeys.GUI_COMMON_BACK))), GuiLoreFrame.NONE),
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(player, CommonKeys.GUI_COMMON_CONFIRM),
                                GuiLoreSpec.None,
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )

                inventory.bindConfirmation(confirmSlot = 24, cancelSlot = 20)
                return runtimeView(title, inventory)
        }

        private fun borderResetTargetForReset(worldData: WorldData): Pair<Location, Double>? {
                val world = resolveWorld(worldData) ?: return null
                val center = world.spawnLocation.clone()
                val size = plugin.config.getDouble("expansion.initial_size", 100.0)
                return center to size
        }

        private fun borderResetTargetForStepBack(worldData: WorldData): Pair<Location, Double>? {
                val record = worldData.latestBorderExpansionRecord() ?: return null
                val world = resolveWorld(worldData) ?: return null
                return Location(world, record.oldCenterX, world.spawnLocation.y, record.oldCenterZ) to record.oldSize
        }

        private fun getSpawnAdjustmentWarning(
                player: Player,
                worldData: WorldData,
                target: Pair<Location, Double>?
        ): List<GuiLoreLine> {
                if (target == null) return emptyList()
                val (center, size) = target
                val world = center.world ?: return emptyList()
                if (!borderResetSpawnService.preview(world, worldData, center, size).hasChanges) {
                        return emptyList()
                }
                return plugin.languageManager.getMessageList(
                        player,
                        MyworldGuiCommonKeys.GUI_CONFIRM_SPAWN_ADJUSTMENT_WARNING
                ).map(GuiLoreLine::Warning)
        }

        private fun resolveWorld(worldData: WorldData): org.bukkit.World? {
                val worldName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
                return Bukkit.getWorld(worldName)
        }

        fun openDeleteWorldConfirmation1(player: Player, worldData: WorldData) {
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.DELETE_WORLD_CONFIRM,
                        isGui = true
                )
                runtime.navigate(
                        player,
                        runtimeRoute(WorldSettingsRuntimeScreen.DELETE_WORLD_CONFIRM, worldData.uuid),
                )
        }

        private fun renderDeleteWorldConfirmation1(
                player: Player,
        ): InventoryMenuView {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, MyworldGuiCommonKeys.GUI_CONFIRM_DELETE_1_TITLE)
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size, player)
                inventory.applyStandardFrame()

                val lore = me.awabi2048.myworldmanager.util.semanticLore(
                        listOf(
                                GuiLoreLine.Danger(lang.getMessage(player, MyworldGuiCommonKeys.GUI_CONFIRM_DELETE_1_QUESTION)),
                                GuiLoreLine.Warning(lang.getMessage(player, MyworldGuiCommonKeys.GUI_CONFIRM_DELETE_1_WARNING)),
                                GuiLoreLine.Warning(lang.getMessage(player, MyworldGuiCommonKeys.GUI_CONFIRM_DELETE_1_NEXT_WARNING))
                        ),
                        GuiLoreFrame.BOTH
                )
                val infoItem =
                        createItem(
                                Material.PAPER,
                                lang.getMessage(player, MyworldGuiCommonKeys.GUI_CONFIRM_DELETE_1_DISPLAY),
                                lore,
                                ItemTag.TYPE_GUI_INFO
                        )
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(player, CommonKeys.GUI_COMMON_CANCEL),
                                GuiLoreSpec.None,
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(player, MyworldGuiCommonKeys.GUI_CONFIRM_DELETE_1_NEXT),
                                GuiLoreSpec.None,
                                ItemTag.TYPE_GUI_SETTING_DELETE_WORLD
                        )
                )

                inventory.bindConfirmation(
                        confirmOperation = WorldSettingsRuntimeOperation.DELETE_WORLD,
                        confirmSlot = 24,
                        cancelSlot = 20,
                )
                return runtimeView(title, inventory)
        }

        fun openDeleteWorldConfirmation2(player: Player, worldData: WorldData) {
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.DELETE_WORLD_CONFIRM_FINAL,
                        isGui = true
                )
                runtime.navigate(
                        player,
                        runtimeRoute(
                                WorldSettingsRuntimeScreen.DELETE_WORLD_FINAL_CONFIRM,
                                worldData.uuid,
                        ),
                )
        }

        private fun renderDeleteWorldConfirmation2(
                player: Player,
        ): InventoryMenuView {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, MyworldGuiCommonKeys.GUI_CONFIRM_DELETE_2_TITLE)
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size, player)
                inventory.applyStandardFrame()

                val lore = me.awabi2048.myworldmanager.util.semanticLore(
                                listOf(
                                        com.awabi2048.ccsystem.api.gui.GuiLoreLine.Danger(
                                                lang.getMessage(player, MyworldGuiCommonKeys.GUI_CONFIRM_DELETE_2_DANGER)
                                        ),
                                        com.awabi2048.ccsystem.api.gui.GuiLoreLine.Warning(
                                                lang.getMessage(player, MyworldGuiCommonKeys.GUI_CONFIRM_DELETE_2_WARNING)
                                        )
                                ),
                                com.awabi2048.ccsystem.api.gui.GuiLoreFrame.BOTH
                )
                val infoItem =
                        createItemComponent(
                                Material.LAVA_BUCKET,
                                lang.getMessage(player, MyworldGuiCommonKeys.GUI_CONFIRM_DELETE_2_DISPLAY),
                                lore,
                                ItemTag.TYPE_GUI_INFO
                        )
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(player, CommonKeys.GUI_COMMON_CANCEL),
                                GuiLoreSpec.None,
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(player, MyworldGuiCommonKeys.GUI_CONFIRM_DELETE_2_CONFIRM_BTN),
                                GuiLoreSpec.None,
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )

                inventory.bindConfirmation(confirmSlot = 24, cancelSlot = 20)
                return runtimeView(title, inventory)
        }

        fun openPortalManagement(
                player: Player,
                worldData: WorldData,
                page: Int = 0,
                replaceCurrent: Boolean = false,
        ) {
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.MANAGE_PORTALS,
                        isGui = true
                )
                val targetRoute = runtimeRoute(
                        WorldSettingsRuntimeScreen.PORTAL_MANAGEMENT,
                        worldData.uuid,
                        page = page,
                )
                if (replaceCurrent) {
                        runtime.replace(player, targetRoute)
                } else {
                        runtime.navigate(player, targetRoute)
                }
        }

        private fun renderPortalManagement(
                player: Player,
                worldData: WorldData,
                page: Int,
        ): InventoryMenuView {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_PORTALS_DISPLAY)
                val allPortals =
                        plugin.portalRepository.findAll().filter { it.worldKey == worldData.worldKey }

                val pageLayout =
                        CCSystem.getAPI().getGuiLayoutService().sevenColumnPage(allPortals.size, page)
                val currentPage = pageLayout.page
                val layout = pageLayout.layout
                val currentPagePortals =
                        allPortals.drop(pageLayout.startIndex).take(pageLayout.itemCount)

                val inventory = RuntimeItemBuffer(layout.size, player)
                // 背景
                val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
                inventory.applyStandardFrame()

                currentPagePortals.forEachIndexed { index, portal ->
                        val slot = layout.itemSlots[index]
                        inventory.setMenuEntry(player, createPortalManagementEntrySpec(player, slot, portal))
                }

                // ナビゲーション
                if (currentPage > 0) {
                        inventory.setItem(
                                layout.previousPageSlot,
                                pageItemSpec(player, previous = true),
                        )
                        inventory.bindRuntimeOperation(
                                layout.previousPageSlot,
                                WorldSettingsRuntimeOperation.PAGE,
                                payload = mapOf(ROUTE_PAGE to (currentPage - 1).toString()),
                        )
                }
                if (currentPage + 1 < pageLayout.totalPages) {
                        inventory.setItem(
                                layout.nextPageSlot,
                                pageItemSpec(player, previous = false),
                        )
                        inventory.bindRuntimeOperation(
                                layout.nextPageSlot,
                                WorldSettingsRuntimeOperation.PAGE,
                                payload = mapOf(ROUTE_PAGE to (currentPage + 1).toString()),
                        )
                }

                // 戻るボタン
                inventory.setElement(
                        CCSystem.getAPI().getGuiElementService().backEntry(
                                player,
                                layout.backSlot,
                                plugin.menuConfigManager.getIconMaterial("world_settings", "back", Material.REDSTONE),
                        )
                )

                return runtimeView(title, inventory)
        }

        private fun createPortalManagementEntrySpec(player: Player, slot: Int, portal: PortalData): GuiMenuEntrySpec {
                val lang = plugin.languageManager

                val destName =
                        if (portal.worldUuid != null) {
                                val worldData =
                                        plugin.worldConfigRepository.findByUuid(portal.worldUuid!!)
                                worldData?.name ?: "Unknown World"
                        } else {
                                val configName =
                                        plugin.config.getString(
                                                "portal_targets.${portal.targetRuntimeName}"
                                        )
                                configName ?: portal.targetRuntimeName ?: "Unknown"
                        }

                val displayTitle =
                        lang.getMessage(
                                player,
                                MyworldGuiPortalKeys.GUI_ADMIN_PORTALS_PORTAL_ITEM_NAME,
                                mapOf("id" to destName)
                        )
                val payload = mapOf(
                        ROUTE_OPERATION to WorldSettingsRuntimeOperation.PORTAL.name,
                        ROUTE_TARGET_UUID to portal.id.toString(),
                )
                return GuiMenuEntrySpec(
                        slot = slot,
                        material = Material.END_PORTAL_FRAME,
                        name = GuiNameSpec.FixedLabel(
                                LegacyComponentSerializer.legacySection().deserialize(displayTitle)
                                        .decoration(TextDecoration.ITALIC, false),
                        ),
                        role = GuiElementRole.ACTION,
                        data = listOf(
                                GuiMenuEntryData(
                                        lang.getMessage(player, MyworldGuiPortalKeys.GUI_ADMIN_PORTALS_PORTAL_ITEM_COORDINATES),
                                        "${portal.x}, ${portal.y}, ${portal.z}",
                                ),
                        ),
                        actions = listOf(
                                menuGestureAction(ACTION_RUNTIME_DISPATCH, MenuGesture.PLAIN_LEFT, lang.getMessage(player, MyworldGuiPortalKeys.GUI_ADMIN_PORTALS_PORTAL_ITEM_ACTION_TELEPORT), payload, safety = MenuActionSafety.EXTERNAL_SIDE_EFFECT),
                                menuGestureAction(ACTION_RUNTIME_DISPATCH, MenuGesture.PLAIN_RIGHT, lang.getMessage(player, MyworldGuiPortalKeys.GUI_ADMIN_PORTALS_PORTAL_ITEM_ACTION_REMOVE), payload, safety = MenuActionSafety.IRREVERSIBLE),
                        ),
                )
        }

        private fun renderRuntimeRoute(player: Player, route: MenuRoute): InventoryMenuView {
                val screen = runtimeScreen(route)
                        ?: error("ワールド設定Runtimeの画面種別がありません")
                val worldUuid = runtimeWorldUuid(route)
                        ?: error("ワールド設定RuntimeのワールドUUIDがありません")
                val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
                        ?: error("ワールド設定Runtimeの対象ワールドが見つかりません: $worldUuid")

                val view = when (screen) {
                                WorldSettingsRuntimeScreen.WORLD_SETTINGS,
                                WorldSettingsRuntimeScreen.ICON_SELECTION ->
                                        renderWorldSettings(player, worldData)
                                WorldSettingsRuntimeScreen.MEMBER_MANAGEMENT ->
                                        renderMemberManagement(
                                                        player,
                                                        worldData,
                                                        runtimePage(route) ?: 0,
                                        )
                                WorldSettingsRuntimeScreen.MEMBER_PENDING_INVITE_CANCEL_CONFIRM ->
                                        renderMemberPendingInviteCancelConfirmation(
                                                        player,
                                                        requireNotNull(runtimeTargetUuid(route)),
                                        )
                                WorldSettingsRuntimeScreen.MEMBER_REMOVE_CONFIRM ->
                                        renderMemberRemoveConfirmation(
                                                        player,
                                                        worldData,
                                                        requireNotNull(runtimeTargetUuid(route)),
                                        )
                                WorldSettingsRuntimeScreen.MEMBER_TRANSFER_CONFIRM ->
                                        renderMemberTransferConfirmation(
                                                        player,
                                                        worldData,
                                                        requireNotNull(runtimeTargetUuid(route)),
                                        )
                                WorldSettingsRuntimeScreen.VISITOR_MANAGEMENT ->
                                        renderVisitorManagement(
                                                        player,
                                                        worldData,
                                                        runtimePage(route) ?: 0,
                                        )
                                WorldSettingsRuntimeScreen.VISITOR_KICK_CONFIRM ->
                                        renderVisitorKickConfirmation(
                                                        player,
                                                        requireNotNull(runtimeTargetUuid(route)),
                                        )
                                WorldSettingsRuntimeScreen.EXPANSION_METHOD_SELECTION ->
                                        renderExpansionMethodSelection(player, worldData)
                                WorldSettingsRuntimeScreen.EXPANSION_CONFIRM ->
                                        renderExpansionConfirmation(
                                                        player,
                                                route.payload[ROUTE_EXPANSION_DIRECTION]
                                                        ?.takeUnless { it == ROUTE_NULL_VALUE }
                                                        ?.let(org.bukkit.block.BlockFace::valueOf),
                                                route.payload[ROUTE_EXPANSION_COST]?.toIntOrNull()
                                                        ?: error("ワールド拡張コストがありません"),
                                        )
                                WorldSettingsRuntimeScreen.EXPANSION_STEP_BACK_CONFIRM ->
                                        renderExpansionStepBackConfirmation(player, worldData)
                                WorldSettingsRuntimeScreen.CRITICAL_SETTINGS ->
                                        renderCriticalSettings(player, worldData)
                                WorldSettingsRuntimeScreen.RESET_EXPANSION_CONFIRM ->
                                        renderResetExpansionConfirmation(player, worldData)
                                WorldSettingsRuntimeScreen.RESET_EXPANSION_SPAWN_UNSAFE_CONFIRM ->
                                        renderResetExpansionSpawnUnsafeConfirmation(
                                                        player,
                                                        worldData,
                                        )
                                WorldSettingsRuntimeScreen.DELETE_WORLD_CONFIRM ->
                                        renderDeleteWorldConfirmation1(player)
                                WorldSettingsRuntimeScreen.DELETE_WORLD_FINAL_CONFIRM ->
                                        renderDeleteWorldConfirmation2(player)
                                WorldSettingsRuntimeScreen.ARCHIVE_CONFIRM ->
                                        renderArchiveConfirmation(player, worldData)
                                WorldSettingsRuntimeScreen.ARCHIVE_FROM_CRITICAL_CONFIRM ->
                                        renderArchiveConfirmation(player, worldData)
                                WorldSettingsRuntimeScreen.UNARCHIVE_CONFIRM ->
                                        renderUnarchiveConfirmation(player, worldData)
                        WorldSettingsRuntimeScreen.PORTAL_MANAGEMENT ->
                                        renderPortalManagement(
                                                player,
                                                worldData,
                                                runtimePage(route) ?: 0,
                                        )
                }
                return if (screen in WORLD_SETTINGS_CONFIRMATION_SCREENS) {
                        view.withCategory(MenuViewCategory.CONFIRMATION)
                } else {
                        view
                }
        }

        private fun runtimeScreen(route: MenuRoute): WorldSettingsRuntimeScreen? =
                route.payload[ROUTE_SCREEN]
                        ?.let { runCatching { WorldSettingsRuntimeScreen.valueOf(it) }.getOrNull() }

        private fun runtimeWorldUuid(route: MenuRoute): UUID? =
                runtimeUuid(route, ROUTE_WORLD_UUID)

        private fun runtimeTargetUuid(route: MenuRoute): UUID? =
                runtimeUuid(route, ROUTE_TARGET_UUID)

        private fun runtimeDecisionId(route: MenuRoute): UUID? =
                runtimeUuid(route, ROUTE_DECISION_ID)

        private fun runtimeOperation(payload: Map<String, String>): WorldSettingsRuntimeOperation? =
                payload[ROUTE_OPERATION]
                        ?.let { runCatching { WorldSettingsRuntimeOperation.valueOf(it) }.getOrNull() }

        private fun runtimeUuid(route: MenuRoute, key: String): UUID? =
                route.payload[key]
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        private fun runtimePage(route: MenuRoute): Int? =
                route.payload[ROUTE_PAGE]?.toIntOrNull()?.coerceAtLeast(0)

        private fun runtimeView(
                title: String,
                inventory: RuntimeItemBuffer,
        ): InventoryMenuView =
                runtimeView(GuiHelper.inventoryTitle(title), inventory)

        private fun runtimeView(
                title: Component,
                inventory: RuntimeItemBuffer,
        ): InventoryMenuView =
                InventoryMenuView(
                        size = inventory.size,
                        title = title,
                        elements = inventory.elements(),
                        standardFrame = false,
                        playerInventoryInteraction = PlayerInventoryInteraction.INTERACTIVE,
                )

        private fun createDecorationItem(material: Material): GuiItemSpec =
                GuiItemSpec(
                        material = material,
                        name = GuiNameSpec.Empty,
                        lore = GuiLoreSpec.None,
                        role = GuiElementRole.DECORATION,
                        amount = 1,
                )

        private fun pageItemSpec(player: Player, previous: Boolean): GuiItemSpec =
                GuiItemSpec(
                        material = plugin.menuConfigManager.getIconMaterial(
                                "world_settings",
                                if (previous) "prev_page" else "next_page",
                                Material.ARROW,
                        ),
                        name = me.awabi2048.myworldmanager.util.fixedLabelName(
                                plugin.languageManager.getMessage(
                                        player,
                                        if (previous) "gui.common.prev_page" else "gui.common.next_page",
                                ),
                                GuiNameStyle.DEFAULT,
                        ),
                        lore = GuiLoreSpec.None,
                        role = GuiElementRole.NAVIGATION,
                        amount = 1,
                )

        fun editWorldInfo(player: Player, worldData: WorldData) {
                plugin.worldSettingsInputService.editInfo(player, worldData)
        }

        fun handleIconSelection(player: Player, clickedItem: ItemStack): MenuActionResult {
                return plugin.worldSettingsIconSelectionService.select(player, clickedItem)
        }

        fun handleManagedMenuClose(player: Player, reason: MenuCloseReason) {
                plugin.worldSettingsListener.onRuntimeInventoryClose(player, reason)
        }

        fun enterSpawnSetting(player: Player, worldData: WorldData, isGuest: Boolean) {
                val action = if (isGuest) SettingsAction.SET_SPAWN_GUEST else SettingsAction.SET_SPAWN_MEMBER
                plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, action, isGui = true)
                runtime.suspendForExternal(player)

                plugin.worldSettingsSpawnPreviewService.start(player)

                val typeKey = if (isGuest) "gui.settings.spawn.type.guest" else "gui.settings.spawn.type.member"
                val typeName = plugin.languageManager.getMessage(player, typeKey)
                player.sendMessage(
                        plugin.languageManager.getMessage(
                                player, MyworldMessagesKeys.MESSAGES_SPAWN_SETTING_STARTED, mapOf("type" to typeName)
                        )
                )
        }

        fun toggleNotification(player: Player, worldData: WorldData) {
                worldData.notificationEnabled = !worldData.notificationEnabled
                plugin.worldConfigRepository.save(worldData)
        }

        fun editAnnouncement(player: Player, worldData: WorldData) {
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.SET_ANNOUNCEMENT
                )
                AnnouncementDialogManager.showAnnouncementEditDialog(player, worldData)
        }

        fun clearAnnouncements(player: Player, worldData: WorldData) {
                worldData.announcementMessages.clear()
                plugin.worldConfigRepository.save(worldData)
                player.sendMessage(
                        plugin.languageManager.getMessage(player, MyworldMessagesKeys.MESSAGES_ANNOUNCEMENT_CLEARED)
                )
        }

        companion object {
                const val RUNTIME_OWNER = "myworldmanager"
                const val RUNTIME_ROUTE = "world_settings_runtime"
                const val RUNTIME_SELECTION_ROUTE = "world_settings_runtime_icon_selection"
                const val RUNTIME_MEMBER_MANAGEMENT_ROUTE = "member_management"
                const val ACTION_RUNTIME_DISPATCH = "dispatch"
                private const val WORLD_UUID_ARGUMENT = "world_uuid"
                private val WORLD_SETTINGS_CAPABILITY_SLOTS = listOf(51)
                private const val ROUTE_SCREEN = "screen"
                const val ROUTE_WORLD_UUID = "world_uuid"
                const val ROUTE_PAGE = "page"
                const val ROUTE_TARGET_UUID = "target_uuid"
                const val ROUTE_DECISION_ID = "decision_id"
                private const val ROUTE_OPERATION = "operation"
                private const val ROUTE_EXPANSION_COST = "expansion_cost"
                private const val ROUTE_EXPANSION_DIRECTION = "expansion_direction"
                private const val ROUTE_NULL_VALUE = "none"
        }
}

internal fun worldSettingsCapabilityInvocation(
        slot: Int,
        capability: com.awabi2048.ccsystem.api.gui.ResolvedMenuCapability,
        arguments: Map<String, String>,
): GuiMenuCapabilityInvocationSpec = GuiMenuCapabilityInvocationSpec(
        slot = slot,
        capability = capability.requireExplicitActionSafety(),
        arguments = arguments,
)
