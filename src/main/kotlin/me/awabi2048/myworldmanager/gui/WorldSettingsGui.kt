package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.util.descriptionLine
import me.awabi2048.myworldmanager.util.warningLine
import me.awabi2048.myworldmanager.util.dangerLine


import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
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
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuRuntimeActions
import com.awabi2048.ccsystem.api.gui.MenuUpdate
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
import me.awabi2048.myworldmanager.api.extension.MemberManagementCapabilitySubject
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
                                WorldSettingsRuntimeOperation.BACK -> plugin.languageManager.getMessage(viewer, "gui.common.return")
                                WorldSettingsRuntimeOperation.CONFIRM -> plugin.languageManager.getMessage(viewer, "gui.common.confirm")
                                WorldSettingsRuntimeOperation.CANCEL -> plugin.languageManager.getMessage(viewer, "gui.common.cancel")
                                WorldSettingsRuntimeOperation.PAGE -> plugin.languageManager.getMessage(viewer, "gui.common.action.page")
                                WorldSettingsRuntimeOperation.TOUR -> plugin.languageManager.getMessage(viewer, "gui.tour.worldmenu.action.open")
                                WorldSettingsRuntimeOperation.EDIT_INFO -> plugin.languageManager.getMessage(viewer, "gui.settings.info.action.open_editor")
                                WorldSettingsRuntimeOperation.SELECT_ICON -> plugin.languageManager.getMessage(viewer, "gui.settings.icon.action.start_selection")
                                WorldSettingsRuntimeOperation.SET_SPAWN -> plugin.languageManager.getMessage(viewer, "gui.settings.spawn.action.set_both")
                                WorldSettingsRuntimeOperation.EXPAND -> plugin.languageManager.getMessage(viewer, "gui.settings.expand.action.open_menu")
                                WorldSettingsRuntimeOperation.CYCLE_PUBLISH -> plugin.languageManager.getMessage(viewer, "gui.common.action.cycle")
                                WorldSettingsRuntimeOperation.MANAGE_MEMBERS -> plugin.languageManager.getMessage(viewer, "gui.settings.member.action.open_list")
                                WorldSettingsRuntimeOperation.EDIT_TAGS -> plugin.languageManager.getMessage(viewer, "gui.settings.tags.action.edit")
                                WorldSettingsRuntimeOperation.EDIT_ANNOUNCEMENT -> plugin.languageManager.getMessage(viewer, "gui.settings.announcement.action.set_message")
                                WorldSettingsRuntimeOperation.TOGGLE_NOTIFICATION -> plugin.languageManager.getMessage(viewer, "gui.settings.notification.action.toggle")
                                WorldSettingsRuntimeOperation.OPEN_ENVIRONMENT -> plugin.languageManager.getMessage(viewer, "gui.settings.environment.action.open")
                                WorldSettingsRuntimeOperation.OPEN_CRITICAL -> plugin.languageManager.getMessage(viewer, "gui.settings.critical.action.open")
                                WorldSettingsRuntimeOperation.MANAGE_VISITORS -> plugin.languageManager.getMessage(viewer, "gui.settings.visitors.action.open")
                                WorldSettingsRuntimeOperation.MANAGE_PORTALS -> plugin.languageManager.getMessage(viewer, "gui.settings.portals.action.open")
                                else -> plugin.languageManager.getMessage(viewer, "gui.common.action.open")
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
                val titleKey = "gui.settings.title"
                if (!lang.hasKey(player, titleKey)) {
                        player.sendMessage(
                                "§c[MyWorldManager] Error: Missing translation key: $titleKey"
                        )
                }

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
                                        "gui.settings.title",
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

                if (hasManagePermission && !isMemberLayout) {
                        inventory.setMenuEntry(
                                player,
                                GuiMenuEntrySpec(
                                        slot = tourSettingSlot,
                                        material = Material.PALE_OAK_BOAT,
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(
                                                lang.getMessage(player, "gui.tour.worldmenu.display"),
                                                GuiNameStyle.DEFAULT,
                                        ),
                                        role = GuiElementRole.ACTION,
                                        description = lang.getMessageList(player, "gui.tour.worldmenu.blocks.description"),
                                        actions = contractMenuActions(
                                                player,
                                                worldData,
                                                WorldSettingsAction.MANAGE_TOUR,
                                                WorldSettingsRuntimeOperation.TOUR,
                                                listOf(lang.getMessage(player, "gui.tour.worldmenu.action.open")),
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
                                                lang.getMessage(player, "gui.settings.info.display"),
                                                GuiNameStyle.DEFAULT,
                                        ),
                                        role = GuiElementRole.ACTION,
                                        description = lang.getMessageList(player, "gui.settings.info.blocks.summary"),
                                        actions = contractMenuActions(
                                                player,
                                                worldData,
                                                WorldSettingsAction.EDIT_INFO,
                                                WorldSettingsRuntimeOperation.EDIT_INFO,
                                                listOf(lang.getMessage(player, "gui.settings.info.action.open_editor")),
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
                                lang.getMessage(player, "gui.settings.common.must_be_in_world")
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
                                                lang.getMessage(player, "gui.settings.icon.display"),
                                                GuiNameStyle.DEFAULT,
                                        ),
                                        role = GuiElementRole.ACTION,
                                        description = lang.getMessageList(player, "gui.settings.icon.blocks.description"),
                                        actions = contractMenuActions(
                                                player,
                                                worldData,
                                                WorldSettingsAction.SELECT_ICON,
                                                WorldSettingsRuntimeOperation.SELECT_ICON,
                                                listOf(lang.getMessage(player, "gui.settings.icon.action.start_selection")),
                                        ),
                                        sounds = plugin.worldSettingsActionService
                                                .contract(player, worldData, WorldSettingsAction.SELECT_ICON).sounds,
                                ),
                        )
                }

                // スポーン位置変更
                if (hasManagePermission) {
                        val spawnActions = if (isBedrock) {
                                listOf(lang.getMessage(player, "gui.settings.spawn.action.set_both"))
                        } else {
                                listOf(
                                        lang.getMessage(player, "gui.settings.spawn.action.set_guest"),
                                        lang.getMessage(player, "gui.settings.spawn.action.set_member"),
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
                                                lang.getMessage(player, "gui.settings.spawn.display"),
                                                GuiNameStyle.DEFAULT,
                                        ),
                                        role = if (isInWorld) GuiElementRole.ACTION else GuiElementRole.CONTENT,
                                        description = lang.getMessageList(player, "gui.settings.spawn.blocks.description"),
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
                                                lang.getMessage(player, "gui.settings.expand.blocks.current_level"),
                                                "$currentLevel/$maxLevel",
                                                GuiValueTone.PRIMARY,
                                        ))
                                        if (currentLevel < maxLevel) {
                                                add(GuiMenuEntryData(
                                                        lang.getMessage(player, "gui.settings.expand.blocks.next_level"),
                                                        currentLevel + 1,
                                                        GuiValueTone.PRIMARY,
                                                ))
                                                if (MyWorldManagerApi.isWorldPointEconomyEnabled()) {
                                                        add(GuiMenuEntryData(
                                                                lang.getMessage(player, "gui.settings.expand.blocks.cost"),
                                                                cost,
                                                                if (stats.worldPoint < cost) GuiValueTone.DANGER else GuiValueTone.PRIMARY,
                                                        ))
                                                        add(GuiMenuEntryData(
                                                                lang.getMessage(player, "gui.settings.expand.blocks.owned_points"),
                                                                stats.worldPoint,
                                                                GuiValueTone.PRIMARY,
                                                        ))
                                                }
                                        }
                                        add(GuiMenuEntryData(
                                                lang.getMessage(player, "gui.settings.expand.blocks.border_center"),
                                                "X: ${formatDecimal(borderInfo.centerX)} / Z: ${formatDecimal(borderInfo.centerZ)}",
                                                GuiValueTone.PRIMARY,
                                        ))
                                        add(GuiMenuEntryData(
                                                lang.getMessage(player, "gui.settings.expand.blocks.border_size"),
                                                formatDecimal(borderInfo.size),
                                                GuiValueTone.PRIMARY,
                                        ))
                                }
                        }
                        val expansionWarnings = buildList {
                                if (currentLevel == WorldData.EXPANSION_LEVEL_SPECIAL) {
                                        add(lang.getMessage(player, "gui.settings.expand.blocks.no_border"))
                                } else {
                                        if (currentLevel >= maxLevel) {
                                                add(lang.getMessage(player, "gui.settings.expand.blocks.max_reached"))
                                        } else if (MyWorldManagerApi.isWorldPointEconomyEnabled() && stats.worldPoint < cost) {
                                                add(lang.getMessage(
                                                        player,
                                                        "gui.settings.expand.blocks.shortage",
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
                                                lang.getMessage(player, "gui.settings.expand.action.open_menu"),
                                                mapOf(ROUTE_OPERATION to WorldSettingsRuntimeOperation.EXPAND.name),
                                                safety = MenuActionSafety.NAVIGATION_ONLY,
                                        ))
                                }
                                if (currentLevel != WorldData.EXPANSION_LEVEL_SPECIAL && !isBedrock && isInWorld) {
                                        add(menuGestureAction(
                                                ACTION_RUNTIME_DISPATCH,
                                                MenuGesture.RIGHT,
                                                lang.getMessage(player, "gui.settings.expand.action.teleport_center"),
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
                                                lang.getMessage(player, "gui.settings.expand.display"),
                                                GuiNameStyle.DEFAULT,
                                        ),
                                        role = if (expansionActions.isEmpty()) GuiElementRole.CONTENT else GuiElementRole.ACTION,
                                        description = lang.getMessageList(player, "gui.settings.expand.blocks.description"),
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
                                                lang.getMessage(player, "publish_level.public"),
                                                lang.getMessage(
                                                        player,
                                                        "publish_level.color.public"
                                                )
                                        ),
                                        Triple(
                                                PublishLevel.FRIEND,
                                                lang.getMessage(player, "publish_level.friend"),
                                                lang.getMessage(
                                                        player,
                                                        "publish_level.color.friend"
                                                )
                                        ),
                                        Triple(
                                                PublishLevel.PRIVATE,
                                                lang.getMessage(player, "publish_level.private"),
                                                lang.getMessage(
                                                        player,
                                                        "publish_level.color.private"
                                                )
                                        ),
                                        Triple(
                                                PublishLevel.LOCKED,
                                                lang.getMessage(player, "publish_level.locked"),
                                                lang.getMessage(
                                                        player,
                                                        "publish_level.color.locked"
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
                                                lang.getMessage(player, "gui.settings.publish.display"),
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
                                                lang.getMessage(player, "gui.common.action.cycle"),
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
                        val ownerRoleColor = lang.getMessage(player, "publish_level.color.owner")
                        val moderatorRoleColor =
                                lang.getMessage(player, "publish_level.color.moderator")
                        val memberRoleColor = lang.getMessage(player, "publish_level.color.member")

                        allMemberData.add(
                                Triple(
                                        worldData.owner,
                                        lang.getMessage(player, "gui.settings.member.role_owner"),
                                        ownerRoleColor
                                )
                        )
                        worldData.moderators.forEach {
                                allMemberData.add(
                                        Triple(
                                                it,
                                                lang.getMessage(
                                                        player,
                                                        "gui.settings.member.role_moderator"
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
                                                        "gui.settings.member.role_member"
                                                ),
                                                memberRoleColor
                                        )
                                )
                        }

                        val maxDisplay = 10
                        val displayList = allMemberData.take(maxDisplay).joinToString("\n") { (uuid, role, color) ->
                                val playerName = PlayerNameUtil.getNameOrDefault(uuid, lang.getMessage(player, "general.unknown"))

                                val isOnline = Bukkit.getOfflinePlayer(uuid).isOnline
                                val nameColor = if (isOnline) "§a" else "§7"
                                val debugColor = "§8"

                                if (role == lang.getMessage(player, "gui.settings.member.role_member")) {
                                    lang.getMessage(player, "gui.settings.member.list_item_member", mapOf("name_color" to nameColor, "player" to playerName))
                                } else {
                                    lang.getMessage(player, "gui.settings.member.list_item", mapOf("debug_color" to debugColor, "role_color" to color, "role" to role, "name_color" to nameColor, "player" to playerName))
                                }
                        }

                        val memberListString = if (allMemberData.size > maxDisplay) {
                                val remaining = allMemberData.size - maxDisplay
                                val onlineCount = allMemberData.drop(maxDisplay).count { Bukkit.getOfflinePlayer(it.first).isOnline }
                                displayList + "\n" + lang.getMessage(player, "gui.settings.member.more_members", mapOf("remaining" to remaining, "online" to onlineCount))
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
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(lang.getMessage(player, "gui.settings.member.display"), GuiNameStyle.DEFAULT),
                                        role = GuiElementRole.ACTION,
                                        description = lang.getMessageList(player, "gui.settings.member.blocks.description") +
                                                lang.getMessage(player, "gui.settings.member.blocks.list_header") +
                                                memberListString.lines().filter(String::isNotBlank).map(String::trim),
                                        data = listOf(
                                                GuiMenuEntryData(lang.getMessage(player, "gui.settings.member.blocks.count"), totalCount, GuiValueTone.INFO),
                                                GuiMenuEntryData(lang.getMessage(player, "gui.settings.member.blocks.pending_requests"), pendingRequestCount, GuiValueTone.PRIMARY),
                                                GuiMenuEntryData(lang.getMessage(player, "gui.settings.member.blocks.pending_invites"), pendingInviteCount, GuiValueTone.PRIMARY),
                                        ),
                                        actions = contractMenuActions(
                                                player, worldData, WorldSettingsAction.MANAGE_MEMBERS,
                                                WorldSettingsRuntimeOperation.MANAGE_MEMBERS,
                                                listOf(lang.getMessage(player, "gui.settings.member.action.open_list")),
                                        ),
                                        sounds = plugin.worldSettingsActionService.contract(player, worldData, WorldSettingsAction.MANAGE_MEMBERS).sounds,
                                ),
                        )
                }

                // タグ設定
                if (hasManagePermission) {
                        val tagsList = if (worldData.tags.isEmpty()) {
                                lang.getMessage(player, "gui.settings.tags.lore_empty")
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
                                                lang.getMessage(player, "gui.settings.tags.display"),
                                                GuiNameStyle.DEFAULT,
                                        ),
                                        role = GuiElementRole.ACTION,
                                        description = lang.getMessageList(
                                                player,
                                                "gui.settings.tags.blocks.description",
                                        ),
                                        data = listOf(GuiMenuEntryData(
                                                lang.getMessage(player, "gui.settings.tags.blocks.current_label"),
                                                tagsList,
                                                GuiValueTone.WARNING,
                                        )),
                                        actions = listOf(menuGestureAction(
                                                ACTION_RUNTIME_DISPATCH,
                                                MenuGesture.ANY,
                                                lang.getMessage(player, "gui.settings.tags.action.edit"),
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
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(lang.getMessage(player, "gui.settings.announcement.display"), GuiNameStyle.DEFAULT),
                                        role = GuiElementRole.ACTION,
                                        description = lang.getMessageList(player, "gui.settings.announcement.blocks.description") +
                                                if (messagePreview.isEmpty()) emptyList() else
                                                        listOf(lang.getMessage(player, "gui.settings.announcement.preview_header")) + messagePreview,
                                        actions = contractMenuActions(
                                                player, worldData, WorldSettingsAction.EDIT_ANNOUNCEMENT,
                                                WorldSettingsRuntimeOperation.EDIT_ANNOUNCEMENT,
                                                listOf(
                                                        lang.getMessage(player, "gui.settings.announcement.action.set_message"),
                                                        lang.getMessage(player, "gui.settings.announcement.action.reset_message"),
                                                ),
                                        ),
                                        sounds = plugin.worldSettingsActionService.contract(player, worldData, WorldSettingsAction.EDIT_ANNOUNCEMENT).sounds,
                                ),
                        )
                }

                // 通知設定
                if (hasManagePermission) {
                        val onlineColor = lang.getMessage(player, "publish_level.color.online")
                        val offlineColor = lang.getMessage(player, "publish_level.color.offline")
                        val statusColor =
                                if (worldData.notificationEnabled) onlineColor else offlineColor
                        val statusText =
                                if (worldData.notificationEnabled)
                                        lang.getMessage(player, "gui.settings.notification.on")
                                else lang.getMessage(player, "gui.settings.notification.off")

                        inventory.setMenuEntry(
                                player,
                                GuiMenuEntrySpec(
                                        slot = notificationSettingSlot,
                                        material = Material.BELL,
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(
                                                lang.getMessage(player, "gui.settings.notification.display"),
                                                GuiNameStyle.DEFAULT,
                                        ),
                                        role = GuiElementRole.ACTION,
                                        description = lang.getMessageList(
                                                player,
                                                "gui.settings.notification.blocks.description",
                                        ),
                                        data = listOf(GuiMenuEntryData(
                                                lang.getMessage(player, "gui.settings.notification.blocks.current_label"),
                                                statusText,
                                                if (worldData.notificationEnabled) GuiValueTone.SUCCESS else GuiValueTone.MUTED,
                                        )),
                                        actions = listOf(menuGestureAction(
                                                ACTION_RUNTIME_DISPATCH,
                                                MenuGesture.ANY,
                                                lang.getMessage(player, "gui.settings.notification.action.toggle"),
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
                                                lang.getMessage(player, "gui.settings.environment.display"),
                                                GuiNameStyle.DEFAULT,
                                        ),
                                        role = if (isInWorld) GuiElementRole.ACTION else GuiElementRole.CONTENT,
                                        description = lang.getMessageList(
                                                player,
                                                "gui.settings.environment.blocks.summary",
                                        ),
                                        warnings = if (!isInWorld && warningLore != null) listOf(warningLore) else emptyList(),
                                        actions = if (isInWorld) listOf(menuGestureAction(
                                                ACTION_RUNTIME_DISPATCH,
                                                MenuGesture.ANY,
                                                lang.getMessage(player, "gui.settings.environment.action.open"),
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
                                                lang.getMessage(player, "gui.settings.critical.display"),
                                                GuiNameStyle.DEFAULT,
                                        ),
                                        role = if (isInWorld) GuiElementRole.ACTION else GuiElementRole.CONTENT,
                                        description = lang.getMessageList(
                                                player,
                                                "gui.settings.critical.blocks.summary",
                                        ),
                                        warnings = if (!isInWorld && warningLore != null) listOf(warningLore) else emptyList(),
                                        actions = if (isInWorld) listOf(menuGestureAction(
                                                ACTION_RUNTIME_DISPATCH,
                                                MenuGesture.ANY,
                                                lang.getMessage(player, "gui.settings.critical.action.open"),
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
                                        lang.getMessage(player, "publish_level.color.public")
                                PublishLevel.FRIEND ->
                                        lang.getMessage(player, "publish_level.color.friend")
                                PublishLevel.PRIVATE ->
                                        lang.getMessage(player, "publish_level.color.private")
                                PublishLevel.LOCKED ->
                                        lang.getMessage(player, "publish_level.color.locked")
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
                                lang.getMessage(player, "gui.admin.world_item.created_info_today")
                        } else {
                                lang.getMessage(
                                        player,
                                        "gui.admin.world_item.created_info_days",
                                        mapOf("days" to daysSinceCreation)
                                )
                        }

                val isSpecialExpansion = currentLevel == WorldData.EXPANSION_LEVEL_SPECIAL
                val warpContract = plugin.worldSettingsActionService
                        .contract(player, worldData, WorldSettingsAction.WARP)
                inventory.setMenuEntry(
                        player,
                        GuiMenuEntrySpec(
                                slot = worldInfoSlot,
                                material = worldData.icon,
                                name = me.awabi2048.myworldmanager.util.fixedLabelName(
                                        lang.getMessage(
                                                player,
                                                "gui.settings.main_info.name",
                                                mapOf("world" to worldData.name),
                                        ),
                                        GuiNameStyle.DEFAULT,
                                ),
                                role = if (warpContract.actionable) GuiElementRole.ACTION else GuiElementRole.CONTENT,
                                description = if (worldData.description.isEmpty()) emptyList() else listOf(worldData.description),
                                data = buildList {
                                        add(GuiMenuEntryData(
                                                lang.getMessage(player, "gui.settings.main_info.owner_label"),
                                                PlayerNameUtil.getNameOrDefault(worldData.owner, lang.getMessage(player, "general.unknown")),
                                                GuiValueTone.INFO,
                                        ))
                                        if (!isSpecialExpansion) {
                                                add(GuiMenuEntryData(
                                                        lang.getMessage(player, "gui.settings.main_info.expansion_label"),
                                                        "$currentLevel/$maxLevel",
                                                        GuiValueTone.PRIMARY,
                                                ))
                                        }
                                        add(GuiMenuEntryData(
                                                lang.getMessage(player, "gui.settings.main_info.created_label"),
                                                "${displayFormatter.format(createdAtDate)} ($createdInfo)",
                                                GuiValueTone.PRIMARY,
                                        ))
                                        if (!isSpecialExpansion) {
                                                add(GuiMenuEntryData(
                                                        lang.getMessage(player, "gui.settings.main_info.archive_label"),
                                                        lang.getMessage(player, "gui.settings.main_info.archive_value", mapOf("days" to daysRemaining, "date" to dateStr)),
                                                        GuiValueTone.WARNING,
                                                ))
                                        }
                                        add(GuiMenuEntryData(
                                                lang.getMessage(player, "gui.settings.main_info.members_label"),
                                                lang.getMessage(player, "gui.settings.main_info.members_value", mapOf("members" to totalCount, "online" to onlineCount)),
                                        ))
                                        add(GuiMenuEntryData(lang.getMessage(player, "gui.settings.main_info.publish_label"), publishLevelName))
                                        add(GuiMenuEntryData(lang.getMessage(player, "gui.settings.main_info.favorites_label"), worldData.favorite, GuiValueTone.DANGER))
                                        add(GuiMenuEntryData(lang.getMessage(player, "gui.settings.main_info.visitors_label"), worldData.recentVisitors.sum(), GuiValueTone.INFO))
                                        add(GuiMenuEntryData("UUID", worldData.uuid, GuiValueTone.MUTED))
                                },
                                actions = contractMenuActions(
                                        player,
                                        worldData,
                                        WorldSettingsAction.WARP,
                                        WorldSettingsRuntimeOperation.WARP,
                                        listOf(lang.getMessage(player, "gui.player_world.world_item.warp")),
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
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(lang.getMessage(player, "gui.settings.visitors.display"), GuiNameStyle.DEFAULT),
                                        role = GuiElementRole.ACTION,
                                        data = listOf(GuiMenuEntryData(lang.getMessage(player, "gui.settings.visitors.blocks.count_label"), visitors.size, GuiValueTone.PRIMARY)),
                                        actions = listOf(menuGestureAction(
                                                ACTION_RUNTIME_DISPATCH,
                                                MenuGesture.ANY,
                                                lang.getMessage(player, "gui.settings.visitors.action.open"),
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
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(lang.getMessage(player, "gui.settings.portals.display"), GuiNameStyle.DEFAULT),
                                        role = GuiElementRole.ACTION,
                                        description = lang.getMessageList(player, "gui.settings.portals.blocks.summary"),
                                        actions = contractMenuActions(
                                                player, worldData, WorldSettingsAction.MANAGE_PORTALS,
                                                WorldSettingsRuntimeOperation.MANAGE_PORTALS,
                                                listOf(lang.getMessage(player, "gui.settings.portals.action.open")),
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
                val title = lang.getMessage(player, "gui.archive_confirm.title")
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size, player)
                inventory.applyStandardFrame()

                val infoItem =
                        createItem(
                                Material.PAPER,
                                lang.getMessage(player, "gui.archive.question"),
                                me.awabi2048.myworldmanager.util.semanticLore(lang.getMessageList(player, "gui.archive.warning").map(GuiLoreLine::Warning), GuiLoreFrame.BOTH),
                                ItemTag.TYPE_GUI_INFO
                        )
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(player, "gui.archive.confirm"),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(GuiLoreLine.Text(lang.getMessage(player, "gui.archive.confirm_desc"))), GuiLoreFrame.NONE),
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(player, "gui.archive.cancel"),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(GuiLoreLine.Text(lang.getMessage(player, "gui.archive.cancel_desc"))), GuiLoreFrame.NONE),
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
                val title = lang.getMessage(player, "gui.unarchive_confirm.title")
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size, player)
                inventory.applyStandardFrame()

                val infoItem =
                        createItem(
                                Material.PAPER,
                                lang.getMessage(player, "gui.unarchive_confirm.title"),
                                me.awabi2048.myworldmanager.util.semanticLore(
                                        lang.getMessageList(player, "gui.unarchive_confirm.description").map(::warningLine),
                                        GuiLoreFrame.BOTH,
                                ),
                                ItemTag.TYPE_GUI_INFO
                        )
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_CONCRETE,
                                lang.getMessage(player, "gui.common.confirm"),
                                GuiLoreSpec.None,
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )

                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_CONCRETE,
                                lang.getMessage(player, "gui.common.cancel"),
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
                val title = lang.getMessage(player, "gui.expansion.method_title")
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size, player)
                // ヘッダー・フッター
                val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
                inventory.applyStandardFrame()

                inventory.setItem(
                        20,
                        createItem(
                                Material.MAP,
                                lang.getMessage(player, "gui.expansion.center_expand.name"),
                                me.awabi2048.myworldmanager.util.semanticLore(lang.getMessageList(player, "gui.expansion.center_expand.lore").map(::descriptionLine), GuiLoreFrame.BOTH),
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
                                lang.getMessage(player, "gui.expansion.direction_expand.name"),
                                me.awabi2048.myworldmanager.util.semanticLore(lang.getMessageList(player, "gui.expansion.direction_expand.lore").map(::descriptionLine), GuiLoreFrame.BOTH),
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
                                        lang.getMessage(player, "gui.expansion.step_back.name"),
                                        me.awabi2048.myworldmanager.util.semanticLore(lang.getMessageList(player, "gui.expansion.step_back.lore").map(::descriptionLine), GuiLoreFrame.BOTH),
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
                val title = lang.getMessage(player, "gui.confirm.step_back_expansion.title")
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size, player)
                inventory.applyStandardFrame()

                val loreLines = mutableListOf<GuiLoreLine>(
                        GuiLoreLine.Warning(lang.getMessage(player, "gui.confirm.step_back_expansion.question"))
                )
                loreLines += lang.getMessageList(player, "gui.confirm.step_back_expansion.description").map(::warningLine)
                if (worldData.latestBorderExpansionRecord()?.modified == true) {
                        loreLines += lang.getMessageList(player, "gui.confirm.step_back_expansion.modified_warning").map(GuiLoreLine::Warning)
                }
                loreLines += getSpawnAdjustmentWarning(player, worldData, borderResetTargetForStepBack(worldData))
                val infoItem =
                        createItem(
                                Material.RECOVERY_COMPASS,
                                lang.getMessage(player, "gui.confirm.step_back_expansion.display"),
                                me.awabi2048.myworldmanager.util.semanticLore(loreLines, GuiLoreFrame.BOTH),
                                ItemTag.TYPE_GUI_INFO
                        )
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(player, "gui.common.cancel"),
                                GuiLoreSpec.None,
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(player, "gui.common.confirm"),
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
                val title = lang.getMessage(player, "gui.expansion.confirm_title")
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
                                lang.getMessage(player, "gui.expansion.method_center")
                        else
                                lang.getMessage(
                                        player,
                                        "gui.expansion.method_direction",
                                        mapOf("direction" to directionName)
                                )
                inventory.setItem(
                        22,
                        createItem(
                                Material.BOOK,
                                lang.getMessage(player, "gui.expansion.confirm_info"),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(
                                        GuiLoreLine.Data(lang.getMessage(player, "gui.expansion.method_label"), methodText, "§f"),
                                        GuiLoreLine.Data(lang.getMessage(player, "gui.expansion.cost_label"), cost, "§e"),
                                        GuiLoreLine.Spacer,
                                        GuiLoreLine.Warning(lang.getMessage(player, "gui.expansion.warning"))
                                ), GuiLoreFrame.BOTH),
                                ItemTag.TYPE_GUI_INFO
                        )
                )

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(player, "gui.expansion.execute"),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(GuiLoreLine.Text(lang.getMessage(player, "gui.expansion.execute_desc"))), GuiLoreFrame.NONE),
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(player, "gui.expansion.cancel"),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(GuiLoreLine.Text(lang.getMessage(player, "gui.expansion.cancel_desc"))), GuiLoreFrame.NONE),
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
                val title = lang.getMessage(player, "gui.member_management.title")

                val allEntries = mutableListOf<MemberManagementEntry>()
                allEntries.add(
                        MemberManagementEntry(
                                playerUuid = worldData.owner,
                                role = lang.getMessage(player, "role.owner")
                        )
                )
                worldData.moderators.forEach {
                        allEntries.add(
                                MemberManagementEntry(
                                        playerUuid = it,
                                        role = lang.getMessage(player, "role.moderator")
                                )
                        )
                }
                worldData.members.forEach {
                        allEntries.add(
                                MemberManagementEntry(
                                        playerUuid = it,
                                        role = lang.getMessage(player, "role.member")
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
                                val subject = MemberManagementCapabilitySubject(
                                                player,
                                                worldData,
                                                entry.playerUuid,
                                )
                                val capabilityAttributes: Map<String, Any> = mapOf(
                                        MemberManagementCapabilityContract.SUBJECT_ATTRIBUTE to subject,
                                )
                                val service = CCSystem.getAPI().getMenuCapabilityService()
                                val capabilityView = service
                                        .definitions(MemberManagementCapabilityContract.PLACEMENT)
                                        .firstNotNullOfOrNull { definition ->
                                                service.resolve(
                                                        definition.capabilityId,
                                                        player,
                                                        attributes = capabilityAttributes,
                                                )
                                        }?.requireExplicitActionSafety()
                                inventory.setElement(
                                        createMemberEntrySpec(
                                                player, slot, entry.playerUuid,
                                                entry.role ?: lang.getMessage(player, "role.member"),
                                                canManageRoles, capabilityView, capabilityAttributes,
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
                                name = me.awabi2048.myworldmanager.util.fixedLabelName(lang.getMessage(player, "gui.member_management.invite.name"), GuiNameStyle.DEFAULT),
                                role = GuiElementRole.ACTION,
                                description = listOf(lang.getMessage(player, "gui.member_management.invite.desc")),
                                actions = buildList {
                                        add(menuGestureAction(
                                                ACTION_RUNTIME_DISPATCH,
                                                MenuGesture.PLAIN_LEFT_RIGHT,
                                                lang.getMessage(player, "gui.member_management.invite.action.normal"),
                                                mapOf(ROUTE_OPERATION to WorldSettingsRuntimeOperation.INVITE_MEMBER.name),
                                                safety = MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE,
                                        ))
                                        if (canForceAddMember) add(menuGestureAction(
                                                ACTION_RUNTIME_DISPATCH,
                                                MenuGesture.SHIFT_LEFT_RIGHT,
                                                lang.getMessage(player, "gui.member_management.invite.action.force"),
                                                mapOf(ROUTE_OPERATION to WorldSettingsRuntimeOperation.INVITE_MEMBER.name),
                                                safety = MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE,
                                        ))
                                },
                        ),
                )

                if (isAdminFlow) {
                        val ownerName =
                                PlayerNameUtil.getNameOrDefault(
                                        worldData.owner,
                                        lang.getMessage(player, "general.unknown")
                                )
                        inventory.setMenuEntry(
                                player,
                                GuiMenuEntrySpec(
                                        slot = footerStart + 2,
                                        material = Material.NAME_TAG,
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(lang.getMessage(player, "gui.member_management.admin_owner_reset.name"), GuiNameStyle.DEFAULT),
                                        role = GuiElementRole.ACTION,
                                        data = listOf(GuiMenuEntryData(
                                                lang.getMessage(player, "gui.member_management.admin_owner_reset.current_owner"),
                                                ownerName,
                                                GuiValueTone.PRIMARY,
                                        )),
                                        actions = listOf(menuGestureAction(
                                                ACTION_RUNTIME_DISPATCH,
                                                MenuGesture.ANY,
                                                lang.getMessage(player, "gui.member_management.admin_owner_reset.action"),
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
                val targetName = PlayerNameUtil.getNameOrDefault(targetUuid, lang.getMessage(player, "general.unknown"))
                val title = lang.getMessage(player, "gui.member_management.pending_cancel_confirm.title")
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
                                                        "gui.member_management.pending_item.name",
                                                        mapOf("player" to targetName),
                                                ),
                                                GuiNameStyle.DEFAULT,
                                        ),
                                        lore = me.awabi2048.myworldmanager.util.semanticLore(
                                                listOf(
                                                        GuiLoreLine.Warning(
                                                                lang.getMessage(
                                                                        player,
                                                                        "gui.member_management.pending_cancel_confirm.body",
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
                                lang.getMessage(player, "gui.member_management.pending_cancel_confirm.cancel"),
                                GuiLoreSpec.None,
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(player, "gui.member_management.pending_cancel_confirm.confirm"),
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
                val targetName = PlayerNameUtil.getNameOrDefault(targetUuid, lang.getMessage(player, "general.unknown"))
                val title =
                        lang.getMessage(
                                player,
                                "gui.member_management.remove_confirm.title",
                                mapOf("player" to targetName)
                        )
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size, player)
                inventory.applyStandardFrame()

                val lore = me.awabi2048.myworldmanager.util.semanticLore(listOf(
                        GuiLoreLine.Warning(lang.getMessage(player, "gui.member_management.remove_confirm.question")),
                        GuiLoreLine.Data(lang.getMessage(player, "gui.member_management.remove_confirm.player_label"), targetName, "§f"),
                        GuiLoreLine.Data(lang.getMessage(player, "gui.member_management.remove_confirm.world_label"), worldData.name, "§f"),
                        GuiLoreLine.Danger(lang.getMessage(player, "gui.member_management.remove_confirm.access_warning"))
                ), GuiLoreFrame.BOTH)

                val infoItem =
                        createItemComponent(
                                Material.PLAYER_HEAD,
                                lang.getMessage(
                                        player,
                                        "gui.member_management.remove_confirm.title",
                                        mapOf(
                                                "player" to PlayerNameUtil.getNameOrDefault(targetUuid, lang.getMessage(player, "general.unknown"))

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
                                        "gui.member_management.remove_confirm.cancel"
                                ),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(GuiLoreLine.Text(
                                        lang.getMessage(
                                                player,
                                                "gui.member_management.remove_confirm.cancel_desc"
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
                                        "gui.member_management.remove_confirm.confirm"
                                ),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(GuiLoreLine.Warning(
                                        lang.getMessage(
                                                player,
                                                "gui.member_management.remove_confirm.confirm_desc"
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
                val targetName = PlayerNameUtil.getNameOrDefault(targetUuid, lang.getMessage(player, "general.unknown"))
                val title =
                        lang.getMessage(
                                player,
                                "gui.member_management.transfer_confirm.title",
                                mapOf("player" to targetName)
                        )
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size, player)
                inventory.applyStandardFrame()

                val lore = me.awabi2048.myworldmanager.util.semanticLore(listOf(
                        GuiLoreLine.Warning(lang.getMessage(player, "gui.member_management.transfer_confirm.question")),
                        GuiLoreLine.Data(lang.getMessage(player, "gui.member_management.transfer_confirm.player_label"), targetName, "§f"),
                        GuiLoreLine.Data(lang.getMessage(player, "gui.member_management.transfer_confirm.world_label"), worldData.name, "§f"),
                        GuiLoreLine.Danger(lang.getMessage(player, "gui.member_management.transfer_confirm.owner_warning"))
                ), GuiLoreFrame.BOTH)

                val infoItem =
                        createItemComponent(
                                Material.PLAYER_HEAD,
                                lang.getMessage(
                                        player,
                                        "gui.member_management.transfer_confirm.title",
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
                                        "gui.member_management.transfer_confirm.cancel"
                                ),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(GuiLoreLine.Text(
                                        lang.getMessage(
                                                player,
                                                "gui.member_management.transfer_confirm.cancel_desc"
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
                                        "gui.member_management.transfer_confirm.confirm"
                                ),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(GuiLoreLine.Warning(
                                        lang.getMessage(
                                                player,
                                                "gui.member_management.transfer_confirm.confirm_desc"
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
                capabilityAttributes: Map<String, Any> = emptyMap(),
        ): MenuElement {
                val lang = plugin.languageManager
                val player = Bukkit.getOfflinePlayer(uuid)
                val stats = plugin.playerStatsRepository.findByUuid(uuid)
                val isOnline = player.isOnline
                val color = if (isOnline) "§a" else "§c"

                var displayName = player.name
                if (displayName == null) {
                        val stats = plugin.playerStatsRepository.findByUuid(uuid)
                        displayName = stats.lastName ?: lang.getMessage(viewer, "general.unknown")
                }

                val embeddedBlocks = capabilityView?.presentation?.embeddedLoreBlocks.orEmpty()
                val payload = mapOf(
                        ROUTE_OPERATION to WorldSettingsRuntimeOperation.MEMBER.name,
                        ROUTE_TARGET_UUID to uuid.toString(),
                )
                val actions = mutableListOf<GuiMenuActionIntent>()
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
                        actions += menuGestureAction(
                                ACTION_RUNTIME_DISPATCH,
                                MenuGesture.fromClicks(action.trigger.clicks),
                                action.text,
                                payload,
                                safety = action.safety,
                                reversibleContract = action.reversibleContract,
                        )
                }
                if (capabilityView == null && isOwner && role != lang.getMessage(viewer, "role.owner")) {
                        val nextRole = if (role == lang.getMessage(null as Player?, "role.member")) {
                                lang.getMessage(null as Player?, "role.moderator")
                        } else {
                                lang.getMessage(null as Player?, "role.member")
                        }
                        addHostAction(
                                MenuGesture.PLAIN_LEFT,
                                lang.getMessage(viewer, "gui.member_management.item.action.change_role", mapOf("next_role" to nextRole)),
                                MenuActionSafety.REVERSIBLE,
                        )
                }
                if (isOwner && role != lang.getMessage(viewer, "role.owner")) {
                        addHostAction(
                                MenuGesture.SHIFT_LEFT,
                                lang.getMessage(viewer, "gui.member_management.item.action.transfer_owner"),
                                MenuActionSafety.CONFIRM_ENTRY,
                        )
                        addHostAction(
                                MenuGesture.SHIFT_RIGHT,
                                lang.getMessage(viewer, "gui.member_management.item.action.remove_member"),
                                MenuActionSafety.CONFIRM_ENTRY,
                        )
                }
                val targetInfoLines = buildList {
                        if (isOnline) {
                                add(GuiLoreLine.Text(lang.getMessage(viewer, "gui.member_management.item.online_label")))
                        } else {
                                add(GuiLoreLine.Data(
                                        lang.getMessage(viewer, "gui.member_management.item.last_online_label"),
                                        stats.lastOnline?.let { formatStoredDateTimeForPlayer(viewer, it) }
                                                ?: lang.getMessage(viewer, "general.unknown"),
                                        GuiValueTone.DEFAULT.colorCode,
                                ))
                        }
                        add(GuiLoreLine.Data(
                                lang.getMessage(viewer, "gui.member_management.item.role_label"),
                                role,
                                GuiValueTone.DEFAULT.colorCode,
                        ))
                }
                val spec = GuiMenuEntrySpec(
                        slot = slot,
                        material = Material.PLAYER_HEAD,
                        name = GuiNameSpec.Opaque(Component.text("$color$displayName").decoration(TextDecoration.ITALIC, false)),
                        role = if (actions.isEmpty()) GuiElementRole.CONTENT else GuiElementRole.ACTION,
                        description = if (capabilityView == null) {
                                targetInfoLines.mapNotNull { (it as? GuiLoreLine.Text)?.text }
                        } else emptyList(),
                        data = if (capabilityView == null) {
                                targetInfoLines.filterIsInstance<GuiLoreLine.Data>()
                                        .map { GuiMenuEntryData(it.label, it.value, toneFor(it.valueColor)) }
                        } else emptyList(),
                        semanticLoreBlocks = if (capabilityView == null) emptyList()
                                else listOf(GuiLoreBlock(targetInfoLines)) + embeddedBlocks,
                        actions = actions,
                        glint = capabilityView?.presentation?.glint,
                        playerHeadOwner = uuid,
                )
                return CCSystem.getAPI().getGuiElementService().menuEntry(viewer, spec).copy(
                        interaction = memberManagementEntryInteraction(
                                capabilityView,
                                capabilityAttributes,
                                hostActions,
                        ),
                )
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
                val title = lang.getMessage(player, "gui.visitor_management.title")
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
                                        lang.getMessage(player, "gui.common.prev_page"),
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
                                        lang.getMessage(player, "gui.common.next_page"),
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
                val targetName = PlayerNameUtil.getNameOrDefault(targetUuid, lang.getMessage(player, "general.unknown"))
                val title =
                        lang.getMessage(
                                player,
                                "gui.visitor_management.kick_confirm.title",
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
                                        "gui.visitor_management.kick_confirm.question"
                                ),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(
                                        GuiLoreLine.Data(
                                                lang.getMessage(player, "gui.visitor_management.kick_confirm.player_label"),
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
                                        "gui.visitor_management.kick_confirm.cancel"
                                ),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(GuiLoreLine.Text(
                                        lang.getMessage(
                                                player,
                                                "gui.visitor_management.kick_confirm.cancel_desc"
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
                                        "gui.visitor_management.kick_confirm.confirm"
                                ),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(GuiLoreLine.Warning(
                                        lang.getMessage(
                                                player,
                                                "gui.visitor_management.kick_confirm.confirm_desc"
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
                val onlineColor = lang.getMessage(viewer, "publish_level.color.online")
                val offlineColor = lang.getMessage(viewer, "publish_level.color.offline")
                val color = if (isOnline) onlineColor else offlineColor

                val statusText =
                        if (isOnline) lang.getMessage(viewer, "status.online")
                        else lang.getMessage(viewer, "status.offline")
                return GuiMenuEntrySpec(
                        slot = slot,
                        material = Material.PLAYER_HEAD,
                        name = GuiNameSpec.FixedLabel(
                                LegacyComponentSerializer.legacySection().deserialize(
                                        "$color${player.name ?: lang.getMessage(viewer, "general.unknown")}"
                                ).decoration(TextDecoration.ITALIC, false),
                        ),
                        role = if (canKick) GuiElementRole.ACTION else GuiElementRole.CONTENT,
                        data = listOf(GuiMenuEntryData(
                                lang.getMessage(viewer, "gui.member_management.item.online_label"),
                                statusText,
                                if (isOnline) GuiValueTone.SUCCESS else GuiValueTone.DANGER,
                        )),
                        actions = if (canKick) listOf(menuGestureAction(
                                ACTION_RUNTIME_DISPATCH,
                                MenuGesture.ANY,
                                lang.getMessage(viewer, "gui.visitor_management.item.kick"),
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
                        name = me.awabi2048.myworldmanager.util.fixedLabelName(name, GuiNameStyle.DEFAULT),
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
                        name = me.awabi2048.myworldmanager.util.fixedLabelName(name, GuiNameStyle.DEFAULT),
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
                val title = lang.getMessage(player, "gui.critical.title")
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

                val archiveDescription = lang.getMessageList(player, "gui.critical.archive_world.description") +
                        if (isOnCooldown) listOf(lang.getMessage(player, "gui.critical.archive_world.remaining", mapOf("hours_remaining" to hoursRemaining))) else emptyList()
                val archiveWarnings = if (isOnCooldown) {
                        listOf(lang.getMessage(player, "gui.critical.archive_world.cooldown_warning", mapOf("cooldown_hours" to cooldownHours)))
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
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(lang.getMessage(player, "gui.critical.reset_expansion.display"), GuiNameStyle.DEFAULT),
                                        role = GuiElementRole.ACTION,
                                        data = listOf(
                                                GuiMenuEntryData(lang.getMessage(player, "gui.critical.reset_expansion.level_label"), currentLevel, GuiValueTone.PRIMARY),
                                                GuiMenuEntryData(lang.getMessage(player, "gui.critical.reset_expansion.refund_label"), resetRefund, GuiValueTone.PRIMARY),
                                        ),
                                        warnings = listOf(lang.getMessage(player, "gui.critical.reset_expansion.warning")),
                                        actions = listOf(menuGestureAction(
                                                ACTION_RUNTIME_DISPATCH,
                                                MenuGesture.ANY,
                                                lang.getMessage(player, "gui.critical.reset_expansion.action"),
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
                                        name = me.awabi2048.myworldmanager.util.fixedLabelName(lang.getMessage(player, "gui.critical.reset_expansion.display"), GuiNameStyle.DEFAULT),
                                        role = GuiElementRole.CONTENT,
                                        warnings = listOf(lang.getMessage(player, "gui.critical.reset_expansion.unavailable")),
                                ),
                        )
                    }

                    // アーカイブボタン (スロット22)
                    inventory.setMenuEntry(player, criticalActionSpec(
                            player, 22,
                            plugin.menuConfigManager.getIconMaterial("world_settings", "critical", Material.CHEST),
                            lang.getMessage(player, "gui.critical.archive_world.display"),
                            archiveDescription, archiveWarnings, !isOnCooldown,
                            WorldSettingsRuntimeOperation.ARCHIVE,
                            lang.getMessage(player, "gui.critical.archive_world.action"),
                    ))
                } else {
                    // 拡張なし: アーカイブは slot 24
                    inventory.setMenuEntry(player, criticalActionSpec(
                            player, 24,
                            plugin.menuConfigManager.getIconMaterial("world_settings", "critical", Material.CHEST),
                            lang.getMessage(player, "gui.critical.archive_world.display"),
                            archiveDescription, archiveWarnings, !isOnCooldown,
                            WorldSettingsRuntimeOperation.ARCHIVE,
                            lang.getMessage(player, "gui.critical.archive_world.action"),
                    ))
                }

                // 削除ボタン
                val ownerStats = plugin.playerStatsRepository.findByUuid(worldData.owner)
                val canDeleteWorld = !MyWorldManagerApi.isWorldSlotSystemEnabled() ||
                        ownerStats.unlockedWorldSlot > 0
                val deleteDisplayName = if (canDeleteWorld) {
                        lang.getMessage(player, "gui.critical.delete_world.display")
                } else {
                        "§8§m${lang.getMessage(player, "gui.critical.delete_world.display")}"
                }

                val deleteSlot = if (isExpansionEnabled || hasSpecialExpansion) 24 else 20
                inventory.setMenuEntry(
                        player,
                        GuiMenuEntrySpec(
                                slot = deleteSlot,
                                material = Material.LAVA_BUCKET,
                                name = me.awabi2048.myworldmanager.util.fixedLabelName(deleteDisplayName, GuiNameStyle.DEFAULT),
                                role = if (canDeleteWorld) GuiElementRole.ACTION else GuiElementRole.CONTENT,
                                description = listOf(lang.getMessage(player, "gui.critical.delete_world.description")) +
                                        if (canDeleteWorld) listOf(lang.getMessage(player, "gui.critical.delete_world.refund_note", mapOf("percent" to percent))) else emptyList(),
                                data = if (canDeleteWorld) {
                                        listOf(GuiMenuEntryData(lang.getMessage(player, "gui.critical.delete_world.refund_label"), refund, GuiValueTone.PRIMARY))
                                } else {
                                        listOf(GuiMenuEntryData(lang.getMessage(player, "gui.critical.delete_world.owner_slots_label"), ownerStats.unlockedWorldSlot, GuiValueTone.PRIMARY))
                                },
                                warnings = listOf(lang.getMessage(player, if (canDeleteWorld) "gui.critical.delete_world.warning" else "gui.critical.delete_world.unavailable_slot")),
                                actions = if (canDeleteWorld) listOf(menuGestureAction(
                                        ACTION_RUNTIME_DISPATCH,
                                        MenuGesture.ANY,
                                        lang.getMessage(player, "gui.critical.delete_world.action"),
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
                val title = lang.getMessage(player, "gui.confirm.reset_expansion.title")
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size, player)
                inventory.applyStandardFrame()

                val loreLines = mutableListOf<GuiLoreLine>(
                        GuiLoreLine.Warning(lang.getMessage(player, "gui.confirm.reset_expansion.question"))
                )
                loreLines += lang.getMessageList(player, "gui.confirm.reset_expansion.description").map(::dangerLine)
                if (worldData.hasModifiedBorderExpansion()) {
                        loreLines += lang.getMessageList(player, "gui.confirm.reset_expansion.modified_warning").map(GuiLoreLine::Warning)
                }
                loreLines += getSpawnAdjustmentWarning(player, worldData, borderResetTargetForReset(worldData))
                val infoItem =
                        createItem(
                                Material.PAPER,
                                lang.getMessage(player, "gui.confirm.reset_expansion.display"),
                                me.awabi2048.myworldmanager.util.semanticLore(loreLines, GuiLoreFrame.BOTH),
                                ItemTag.TYPE_GUI_INFO
                        )
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(player, "gui.common.cancel"),
                                GuiLoreSpec.None,
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(player, "gui.common.confirm"),
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
                val title = lang.getMessage(player, "gui.confirm.reset_expansion_spawn_unsafe.title")
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size, player)
                inventory.applyStandardFrame()

                val loreLines = mutableListOf<GuiLoreLine>(
                        GuiLoreLine.Danger(lang.getMessage(player, "gui.confirm.reset_expansion_spawn_unsafe.warning"))
                )
                loreLines += lang.getMessageList(player, "gui.confirm.reset_expansion_spawn_unsafe.description").map(GuiLoreLine::Warning)
                if (worldData.hasModifiedBorderExpansion()) {
                        loreLines += lang.getMessageList(player, "gui.confirm.reset_expansion.modified_warning").map(GuiLoreLine::Warning)
                }
                loreLines += getSpawnAdjustmentWarning(player, worldData, borderResetTargetForReset(worldData))
                val infoItem =
                        createItem(
                                Material.MAGMA_BLOCK,
                                lang.getMessage(player, "gui.confirm.reset_expansion_spawn_unsafe.display"),
                                me.awabi2048.myworldmanager.util.semanticLore(loreLines, GuiLoreFrame.BOTH),
                                ItemTag.TYPE_GUI_INFO
                        )
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(player, "gui.common.cancel"),
                                me.awabi2048.myworldmanager.util.semanticLore(listOf(GuiLoreLine.Text(lang.getMessage(player, "gui.common.back"))), GuiLoreFrame.NONE),
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(player, "gui.common.confirm"),
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
                        "gui.confirm.spawn_adjustment_warning"
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
                val title = lang.getMessage(player, "gui.confirm.delete_1.title")
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size, player)
                inventory.applyStandardFrame()

                val lore = me.awabi2048.myworldmanager.util.semanticLore(
                        listOf(
                                GuiLoreLine.Danger(lang.getMessage(player, "gui.confirm.delete_1.question")),
                                GuiLoreLine.Warning(lang.getMessage(player, "gui.confirm.delete_1.warning")),
                                GuiLoreLine.Warning(lang.getMessage(player, "gui.confirm.delete_1.next_warning"))
                        ),
                        GuiLoreFrame.BOTH
                )
                val infoItem =
                        createItem(
                                Material.PAPER,
                                lang.getMessage(player, "gui.confirm.delete_1.display"),
                                lore,
                                ItemTag.TYPE_GUI_INFO
                        )
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(player, "gui.common.cancel"),
                                GuiLoreSpec.None,
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(player, "gui.confirm.delete_1.next"),
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
                val title = lang.getMessage(player, "gui.confirm.delete_2.title")
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size, player)
                inventory.applyStandardFrame()

                val lore = me.awabi2048.myworldmanager.util.semanticLore(
                                listOf(
                                        com.awabi2048.ccsystem.api.gui.GuiLoreLine.Danger(
                                                lang.getMessage(player, "gui.confirm.delete_2.danger")
                                        ),
                                        com.awabi2048.ccsystem.api.gui.GuiLoreLine.Warning(
                                                lang.getMessage(player, "gui.confirm.delete_2.warning")
                                        )
                                ),
                                com.awabi2048.ccsystem.api.gui.GuiLoreFrame.BOTH
                )
                val infoItem =
                        createItemComponent(
                                Material.LAVA_BUCKET,
                                lang.getMessage(player, "gui.confirm.delete_2.display"),
                                lore,
                                ItemTag.TYPE_GUI_INFO
                        )
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(player, "gui.common.cancel"),
                                GuiLoreSpec.None,
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(player, "gui.confirm.delete_2.confirm_btn"),
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
                val title = lang.getMessage(player, "gui.settings.portals.display")
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
                                "gui.admin_portals.portal_item.name",
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
                                        lang.getMessage(player, "gui.admin_portals.portal_item.coordinates"),
                                        "${portal.x}, ${portal.y}, ${portal.z}",
                                ),
                        ),
                        actions = listOf(
                                menuGestureAction(ACTION_RUNTIME_DISPATCH, MenuGesture.PLAIN_LEFT, lang.getMessage(player, "gui.admin_portals.portal_item.action.teleport"), payload, safety = MenuActionSafety.EXTERNAL_SIDE_EFFECT),
                                menuGestureAction(ACTION_RUNTIME_DISPATCH, MenuGesture.PLAIN_RIGHT, lang.getMessage(player, "gui.admin_portals.portal_item.action.remove"), payload, safety = MenuActionSafety.IRREVERSIBLE),
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

                return when (screen) {
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
                                player, "messages.spawn_setting_started", mapOf("type" to typeName)
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
                        plugin.languageManager.getMessage(player, "messages.announcement_cleared")
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
