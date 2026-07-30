package me.awabi2048.myworldmanager.gui


import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuIconData
import com.awabi2048.ccsystem.api.gui.GuiMenuIconSpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuAcceptedClicks
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
import me.awabi2048.myworldmanager.api.extension.WorldSettingsLayoutMode
import me.awabi2048.myworldmanager.api.extension.WorldSettingsPresentationContext
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
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.GuiLoreActions
import me.awabi2048.myworldmanager.util.GuiLoreAction
import me.awabi2048.myworldmanager.util.GuiLoreBuilder
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PermissionManager
import me.awabi2048.myworldmanager.util.StructuredLore
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
import org.bukkit.inventory.meta.SkullMeta

class WorldSettingsGui(private val plugin: MyWorldManager) {
        private val borderResetSpawnService = BorderResetSpawnService()
        private val runtime = CCSystem.getAPI().getMenuRuntimeService()
        private val runtimeRenderCapture = ThreadLocal<InventoryMenuView?>()
        private val runtimeRenderCaptureActive = ThreadLocal.withInitial { false }

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
                                                                context.payload["slot"]?.toIntOrNull()
                                                                        ?: return@MenuActionHandler MenuActionResult.Ignored,
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
                                                                context.payload["slot"]?.toIntOrNull()
                                                                        ?: return@MenuActionHandler MenuActionResult.Ignored,
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
                                                        plugin.worldSettingsListener.handleRuntimeIconSelection(
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
                                                                context.payload["slot"]?.toIntOrNull()
                                                                        ?: return@MenuActionHandler MenuActionResult.Ignored,
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
                                        ACTION_CAPABILITY to
                                                MenuActionHandler(::handleCapability),
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

        fun enableRuntimeIconSelection(player: Player) {
                val route = CCSystem.getAPI().getMenuNavigationService().currentRoute(player) ?: return
                if (route.owner != RUNTIME_OWNER || route.id != RUNTIME_ROUTE) return
                runtime.replace(player, route.copy(id = RUNTIME_SELECTION_ROUTE))
        }

        fun disableRuntimeIconSelection(player: Player, worldData: WorldData) {
                open(player, worldData, replaceCurrent = true)
        }

        internal fun route(worldUuid: UUID): MenuRoute =
                runtimeRoute(WorldSettingsRuntimeScreen.WORLD_SETTINGS, worldUuid)

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

        private class RuntimeItemBuffer(val size: Int) {
                private val items = arrayOfNulls<ItemStack>(size)
                private val semantics = mutableMapOf<Int, RuntimeItemSemantics>()

                val contents: Array<ItemStack?>
                        get() = items.copyOf()

                fun setItem(slot: Int, item: ItemStack?) {
                        require(slot in items.indices) { "slot is outside Runtime buffer: $slot/$size" }
                        items[slot] = item
                        semantics.remove(slot)
                }

                fun setSemanticItem(
                        slot: Int,
                        item: ItemStack,
                        interaction: MenuInteraction,
                        role: GuiElementRole = when (interaction) {
                                is MenuInteraction.Back -> GuiElementRole.BACK
                                else -> GuiElementRole.ACTION
                        },
                ) {
                        require(slot in items.indices) { "slot is outside Runtime buffer: $slot/$size" }
                        items[slot] = item
                        semantics[slot] = RuntimeItemSemantics(role, interaction)
                }

                fun setRuntimeOperation(
                        slot: Int,
                        item: ItemStack,
                        operation: WorldSettingsRuntimeOperation,
                        acceptedClicks: Set<org.bukkit.event.inventory.ClickType> =
                                MenuAcceptedClicks.LEFT_RIGHT,
                        payload: Map<String, String> = emptyMap(),
                ) {
                        setSemanticItem(
                                slot,
                                item,
                                MenuInteraction.Action(
                                        ACTION_RUNTIME_DISPATCH,
                                        acceptedClicks,
                                        mapOf(
                                                "slot" to slot.toString(),
                                                ROUTE_OPERATION to operation.name,
                                        ) + payload,
                                ),
                                role = when (operation) {
                                        WorldSettingsRuntimeOperation.BACK -> GuiElementRole.BACK
                                        WorldSettingsRuntimeOperation.PAGE -> GuiElementRole.NAVIGATION
                                        WorldSettingsRuntimeOperation.CONFIRM -> GuiElementRole.CONFIRM
                                        WorldSettingsRuntimeOperation.CANCEL -> GuiElementRole.CANCEL
                                        else -> GuiElementRole.ACTION
                                },
                        )
                }

                fun bindRuntimeOperation(
                        slot: Int,
                        operation: WorldSettingsRuntimeOperation,
                        acceptedClicks: Set<org.bukkit.event.inventory.ClickType> =
                                MenuAcceptedClicks.LEFT_RIGHT,
                        payload: Map<String, String> = emptyMap(),
                ) {
                        val item = requireNotNull(getItem(slot)) {
                                "Runtime operation item is missing: $slot/$operation"
                        }
                        setRuntimeOperation(slot, item, operation, acceptedClicks, payload)
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

                fun getItem(slot: Int): ItemStack? = items.getOrNull(slot)

                fun clear() {
                        items.fill(null)
                        semantics.clear()
                }

                fun applyStandardFrame() {
                        val black = GuiItemFactory.decoration(Material.BLACK_STAINED_GLASS_PANE)
                        val gray = GuiItemFactory.decoration(Material.GRAY_STAINED_GLASS_PANE)
                        for (slot in items.indices) {
                                items[slot] = when {
                                        slot < 9 || slot >= size - 9 -> black.clone()
                                        else -> gray.clone()
                                }
                        }
                }

                fun elements(): List<MenuElement> =
                        items.mapIndexedNotNull { slot, item ->
                                item ?: return@mapIndexedNotNull null
                                val semantic = semantics[slot]
                                MenuElement(
                                        slot = slot,
                                        item = item,
                                        role = semantic?.role
                                                ?: if (item.type.name.endsWith("_STAINED_GLASS_PANE")) {
                                                        GuiElementRole.DECORATION
                                                } else {
                                                        GuiElementRole.CONTENT
                                                },
                                        interaction = semantic?.interaction
                                                ?: MenuInteraction.DisplayOnly,
                                )
                        }

                companion object {
                        private const val ACTION_RUNTIME_DISPATCH = "dispatch"
                }

                private data class RuntimeItemSemantics(
                        val role: GuiElementRole,
                        val interaction: MenuInteraction,
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

        private fun renderWorldSettings(
                player: Player,
                worldData: WorldData,
        ): InventoryMenuView {
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
                val presentationMode = MyWorldManagerApi.getWorldSettingsPresentationPolicies()
                        .firstNotNullOfOrNull { policy ->
                                policy.evaluate(
                                        WorldSettingsPresentationContext(
                                                player,
                                                worldData,
                                                isOwner,
                                                isModerator,
                                                isMember,
                                        ),
                                )?.layoutMode
                        } ?: WorldSettingsLayoutMode.DEFAULT
                val ownerActionsAllowed =
                        isOwner && presentationMode == WorldSettingsLayoutMode.DEFAULT
                val hasManagePermission =
                        (isOwner || isModerator) &&
                                presentationMode == WorldSettingsLayoutMode.DEFAULT
                val isBedrock = plugin.playerPlatformResolver.isBedrock(player)

                val isMemberLayout = isMember && !hasManagePermission
                val useModeratorCenteredLayout = isModerator && !isOwner

                val inventorySize = when (presentationMode) {
                        WorldSettingsLayoutMode.COMPACT_LIMITED_OWNER,
                        WorldSettingsLayoutMode.READ_ONLY_COMPACT -> 45
                        WorldSettingsLayoutMode.READ_ONLY_DEFAULT -> 54
                        WorldSettingsLayoutMode.DEFAULT -> if (isMemberLayout) 45 else 54
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

                val inventory = RuntimeItemBuffer(inventorySize)

                // 背景 (黒の板ガラス)
                val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
                inventory.applyStandardFrame()

                // 戻るボタン
                if (GuiHelper.canGoBack(player)) {
                        inventory.setItem(
                                backButtonSlot,
                                createItem(Material.REDSTONE, "§7戻る", GuiLoreSpec.None, null)
                        )
                        inventory.bindRuntimeOperation(
                                backButtonSlot,
                                WorldSettingsRuntimeOperation.BACK,
                        )
                }

                if (hasManagePermission && !isMemberLayout) {
                        inventory.setItem(
                                tourSettingSlot,
                                createItemComponent(
                                        Material.PALE_OAK_BOAT,
                                        lang.getMessage(player, "gui.tour.worldmenu.display"),
                                        GuiLoreSpec.Blocks(listOf(
                                                GuiLoreBlock(lang.getMessageList(player, "gui.tour.worldmenu.blocks.description").map(GuiLoreLine::Text)),
                                                GuiLoreBlock(listOf(GuiLoreActions.singleClick(lang, player, lang.getMessage(player, "gui.tour.worldmenu.action.open"))))
                                        )),
                                        null
                                )
                        )
                        inventory.bindRuntimeOperation(
                                tourSettingSlot,
                                WorldSettingsRuntimeOperation.TOUR,
                        )
                }

                // ワールド名・説明変更
                if (hasManagePermission) {
                        val infoLoreBuilder =
                                GuiLoreBuilder(lang, player)
                                        .block(lang.getMessageList(player, "gui.settings.info.blocks.summary").map(GuiLoreLine::Text))
                                        .actions(lang.getMessage(player, "gui.settings.info.action.open_editor"))

                        inventory.setItem(
                                infoSettingSlot,
                                createItemComponent(
                                        plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "info",
                                                Material.NAME_TAG
                                        ),
                                        lang.getMessage(player, "gui.settings.info.display"),
                                        infoLoreBuilder.buildSpec(),
                                        null
                                )
                        )
                        inventory.bindRuntimeOperation(
                                infoSettingSlot,
                                WorldSettingsRuntimeOperation.EDIT_INFO,
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
                        val iconLore =
                                GuiLoreBuilder(lang, player)
                                        .block(lang.getMessageList(player, "gui.settings.icon.blocks.description").map(GuiLoreLine::Text))
                                        .actions(
                                                lang.getMessage(
                                                        player,
                                                        "gui.settings.icon.action.start_selection"
                                                )
                                        )
                                        .buildSpec()

                        inventory.setItem(
                                iconSettingSlot,
                                createItemComponent(
                                        plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "icon",
                                                Material.ANVIL
                                        ),
                                        lang.getMessage(player, "gui.settings.icon.display"),
                                        iconLore,
                                        null
                                )
                        )
                        inventory.bindRuntimeOperation(
                                iconSettingSlot,
                                WorldSettingsRuntimeOperation.SELECT_ICON,
                        )
                }

                // スポーン位置変更
                if (hasManagePermission) {
                        val spawnLoreBuilder =
                                GuiLoreBuilder(lang, player)
                                        .block(
                                                lang.getMessageList(
                                                        player,
                                                        "gui.settings.spawn.blocks.description"
                                                ).map(GuiLoreLine::Text)
                                        )

                        if (!isInWorld && warningLore != null) {
                                spawnLoreBuilder.warning(warningLore)
                        }

                        if (isBedrock) {
                                spawnLoreBuilder.actions(
                                        lang.getMessage(player, "gui.settings.spawn.action.set_both")
                                )
                        } else {
                                spawnLoreBuilder.actions(
                                        listOf(
                                                GuiLoreAction(
                                                        lang.getMessage(player, "gui.settings.click.left"),
                                                        lang.getMessage(
                                                                player,
                                                                "gui.settings.spawn.action.set_guest"
                                                        )
                                                ),
                                                GuiLoreAction(
                                                        lang.getMessage(player, "gui.settings.click.right"),
                                                        lang.getMessage(
                                                                player,
                                                                "gui.settings.spawn.action.set_member"
                                                        )
                                                )
                                        )
                                )
                        }

                        inventory.setItem(
                                spawnSettingSlot,
                                createItemComponent(
                                        plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "spawn",
                                                Material.COMPASS
                                        ),
                                        lang.getMessage(player, "gui.settings.spawn.display"),
                                        spawnLoreBuilder.buildSpec(),
                                        null
                                )
                        )
                        inventory.bindRuntimeOperation(
                                spawnSettingSlot,
                                WorldSettingsRuntimeOperation.SET_SPAWN,
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
                        val expansionLoreBuilder = GuiLoreBuilder(lang, player)
                                .block(
                                        lang.getMessageList(
                                                player,
                                                "gui.settings.expand.blocks.description"
                                        ).map(GuiLoreLine::Text)
                                )
                                .spacer()

                        if (currentLevel == WorldData.EXPANSION_LEVEL_SPECIAL) {
                                expansionLoreBuilder.block(listOf(
                                        GuiLoreLine.StyledText(
                                                lang.getMessage(player, "gui.settings.expand.blocks.no_border"),
                                                "§a",
                                                false
                                        )
                                ))
                        } else {
                                val targetLevel = currentLevel + 1
                                if (currentLevel < maxLevel) {
                                        if (!MyWorldManagerApi.isWorldPointEconomyEnabled()) {
                                                expansionLoreBuilder.block(listOf(
                                                        GuiLoreLine.Data(lang.getMessage(player, "gui.settings.expand.blocks.current_level"), "$currentLevel/$maxLevel", "§e"),
                                                        GuiLoreLine.Data(lang.getMessage(player, "gui.settings.expand.blocks.next_level"), targetLevel, "§e")
                                                ))
                                        } else if (stats.worldPoint < cost) {
                                                // Insufficient points
                                                val insufficient = cost - stats.worldPoint
                                                expansionLoreBuilder.block(listOf(
                                                        GuiLoreLine.Data(lang.getMessage(player, "gui.settings.expand.blocks.current_level"), "$currentLevel/$maxLevel", "§e"),
                                                        GuiLoreLine.Data(lang.getMessage(player, "gui.settings.expand.blocks.cost"), cost, "§c"),
                                                        GuiLoreLine.Data(lang.getMessage(player, "gui.settings.expand.blocks.owned_points"), stats.worldPoint, "§e"),
                                                        GuiLoreLine.Warning(lang.getMessage(player, "gui.settings.expand.blocks.shortage", mapOf("shortage" to insufficient)))
                                                ))
                                        } else {
                                                // Able to upgrade
                                                expansionLoreBuilder.block(listOf(
                                                        GuiLoreLine.Data(lang.getMessage(player, "gui.settings.expand.blocks.current_level"), "$currentLevel/$maxLevel", "§e"),
                                                        GuiLoreLine.Data(lang.getMessage(player, "gui.settings.expand.blocks.next_level"), targetLevel, "§e"),
                                                        GuiLoreLine.Data(lang.getMessage(player, "gui.settings.expand.blocks.cost"), cost, "§e"),
                                                        GuiLoreLine.Data(lang.getMessage(player, "gui.settings.expand.blocks.owned_points"), stats.worldPoint, "§e")
                                                ))
                                        }
                                } else {
                                        // Max level
                                        expansionLoreBuilder.block(listOf(
                                                GuiLoreLine.Data(lang.getMessage(player, "gui.settings.expand.blocks.current_level"), "$currentLevel/$maxLevel", "§e"),
                                                GuiLoreLine.Warning(lang.getMessage(player, "gui.settings.expand.blocks.max_reached"))
                                        ))
                                }

                                val showOpenMenuHint = isInWorld && currentLevel < maxLevel
                                if (showOpenMenuHint) {
                                        expansionLoreBuilder.actions(
                                                lang.getMessage(
                                                        player,
                                                        "gui.settings.expand.action.open_menu"
                                                )
                                        )
                                }

                                val borderInfo = buildCurrentBorderInfo(worldData, currentLevel)
                                expansionLoreBuilder.block(listOf(
                                        GuiLoreLine.Data(lang.getMessage(player, "gui.settings.expand.blocks.border_center"), "X: ${formatDecimal(borderInfo.centerX)} / Z: ${formatDecimal(borderInfo.centerZ)}", "§e"),
                                        GuiLoreLine.Data(lang.getMessage(player, "gui.settings.expand.blocks.border_size"), formatDecimal(borderInfo.size), "§e")
                                ))
                                if (!isBedrock && isInWorld) {
                                        expansionLoreBuilder.actions(
                                                listOf(
                                                        GuiLoreAction(
                                                                lang.getMessage(player, "gui.settings.click.right"),
                                                                lang.getMessage(
                                                                        player,
                                                                        "gui.settings.expand.action.teleport_center"
                                                                )
                                                        )
                                                )
                                        )
                                }
                                if (!isInWorld && warningLore != null) {
                                        expansionLoreBuilder.warning(warningLore)
                                }
                        }

                        inventory.setItem(
                                23,
                                createItemComponent(
                                        plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "expand",
                                                Material.FILLED_MAP
                                        ),
                                        lang.getMessage(player, "gui.settings.expand.display"),
                                        expansionLoreBuilder.buildSpec(),
                                        null
                                )
                        )
                        inventory.bindRuntimeOperation(23, WorldSettingsRuntimeOperation.EXPAND)
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

                        val inactiveColor = lang.getMessage(player, "publish_level.color.inactive")
                        val publishLore = GuiLoreSpec.Rich(buildList {
                                        add(GuiLoreLine.Text(lang.getMessage(
                                                player,
                                                "publish_level.description.${worldData.publishLevel.name.lowercase()}"
                                        )))
                                        add(GuiLoreLine.Spacer)
                                        // 現在値行は持たず、選択肢リスト内のマーカーで現在の公開レベルを示す。
                                        addAll(StructuredLore.selectionLines(
                                                levels.map { (level, name, color) ->
                                                        StructuredLore.SelectionOption(
                                                                label = name,
                                                                selected = level == worldData.publishLevel,
                                                                selectedColor = color,
                                                                inactiveColor = inactiveColor
                                                        )
                                                }
                                        ))
                                        add(GuiLoreLine.Spacer)
                                        add(GuiLoreActions.cycle(lang, player))
                                }, GuiLoreFrame.BOTH)

                        inventory.setItem(
                                24,
                                createItemComponent(
                                        plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "publish",
                                                Material.OAK_DOOR
                                        ),
                                        lang.getMessage(player, "gui.settings.publish.display"),
                                        publishLore,
                                        null
                                )
                        )
                        inventory.bindRuntimeOperation(24, WorldSettingsRuntimeOperation.CYCLE_PUBLISH)
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

                        val memberLore =
                                GuiLoreBuilder(lang, player)
                                        .block(
                                                lang.getMessageList(
                                                        player,
                                                        "gui.settings.member.blocks.description"
                                                ).map(GuiLoreLine::Text)
                                        )
                                        .spacer()
                                        .block(buildList {
                                                add(GuiLoreLine.Data(lang.getMessage(player, "gui.settings.member.blocks.count"), totalCount, "§b"))
                                                add(GuiLoreLine.Data(lang.getMessage(player, "gui.settings.member.blocks.pending_requests"), pendingRequestCount, "§e"))
                                                add(GuiLoreLine.Data(lang.getMessage(player, "gui.settings.member.blocks.pending_invites"), pendingInviteCount, "§e"))
                                                add(GuiLoreLine.Text(lang.getMessage(player, "gui.settings.member.blocks.list_header")))
                                                memberListString.lines().filter(String::isNotBlank).forEach { add(GuiLoreLine.Text(it.trim())) }
                                        })
                                        .actions(
                                                lang.getMessage(
                                                        player,
                                                        "gui.settings.member.action.open_list"
                                                )
                                        )
                                        .buildSpec()

                        inventory.setItem(
                                25,
                                createItemComponent(
                                        plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "members",
                                                Material.PLAYER_HEAD
                                        ),
                                        lang.getMessage(player, "gui.settings.member.display"),
                                        memberLore,
                                        null
                                )
                        )
                        inventory.bindRuntimeOperation(25, WorldSettingsRuntimeOperation.MANAGE_MEMBERS)
                }

                // タグ設定
                if (hasManagePermission) {
                        val tagsList = if (worldData.tags.isEmpty()) {
                                lang.getMessage(player, "gui.settings.tags.lore_empty")
                        } else {
                                worldData.tags.joinToString(", ") { plugin.worldTagManager.getDisplayName(player, it) }
                        }

                        val tagLore =
                                GuiLoreBuilder(lang, player)
                                        .block(
                                                lang.getMessageList(
                                                        player,
                                                        "gui.settings.tags.blocks.description"
                                                ).map(GuiLoreLine::Text)
                                        )
                                        .spacer()
                                        .block(listOf(GuiLoreLine.Data(
                                                lang.getMessage(player, "gui.settings.tags.blocks.current_label"),
                                                tagsList,
                                                "§6"
                                        )))
                                        .actions(
                                                lang.getMessage(player, "gui.settings.tags.action.edit")
                                        )
                                        .buildSpec()

                        inventory.setItem(
                                tagsSettingSlot,
                                createItemComponent(
                                        plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "tags",
                                                Material.BOOK
                                        ),
                                        lang.getMessage(player, "gui.settings.tags.display"),
                                        tagLore,
                                        null
                                )
                        )
                        inventory.bindRuntimeOperation(
                                tagsSettingSlot,
                                WorldSettingsRuntimeOperation.EDIT_TAGS,
                        )
                }

                // 案内設定
                if (hasManagePermission) {
                        val messagePreview = worldData.announcementMessages

                        val announcementLoreBuilder =
                                GuiLoreBuilder(lang, player)
                                        .block(
                                                lang.getMessageList(
                                                        player,
                                                        "gui.settings.announcement.blocks.description"
                                                ).map(GuiLoreLine::Text)
                                        )

                        if (messagePreview.isNotEmpty()) {
                                announcementLoreBuilder
                                        .spacer()
                                        .block(buildList {
                                                add(GuiLoreLine.Text(lang.getMessage(player, "gui.settings.announcement.preview_header")))
                                                messagePreview.forEach { add(GuiLoreLine.UserText(it)) }
                                        })
                        }

                        announcementLoreBuilder.actions(
                                listOf(
                                        GuiLoreAction(
                                                lang.getMessage(player, "gui.settings.click.left"),
                                                lang.getMessage(player, "gui.settings.announcement.action.set_message")
                                        ),
                                        GuiLoreAction(
                                                lang.getMessage(player, "gui.settings.click.right"),
                                                lang.getMessage(player, "gui.settings.announcement.action.reset_message")
                                        )
                                )
                        )

                        inventory.setItem(
                                announcementSettingSlot,
                                createItemComponent(
                                        plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "announcement",
                                                Material.OAK_SIGN
                                        ),
                                        lang.getMessage(
                                                player,
                                                "gui.settings.announcement.display"
                                        ),
                                        announcementLoreBuilder.buildSpec(),
                                        null
                                )
                        )
                        inventory.bindRuntimeOperation(
                                announcementSettingSlot,
                                WorldSettingsRuntimeOperation.EDIT_ANNOUNCEMENT,
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

                        val notificationItem =
                                GuiItemFactory.menuIcon(
                                        GuiMenuIconSpec(
                                                material = Material.BELL,
                                                name = GuiNameSpec.Text(
                                                        lang.getMessage(
                                                                player,
                                                                "gui.settings.notification.display"
                                                        ),
                                                        GuiNameStyle.DEFAULT
                                                ),
                                                role = GuiElementRole.CONTENT,
                                                amount = 1,
                                                description = lang.getMessageList(
                                                        player,
                                                        "gui.settings.notification.blocks.description"
                                                ),
                                                data = listOf(
                                                        GuiMenuIconData(
                                                                lang.getMessage(
                                                                        player,
                                                                        "gui.settings.notification.blocks.current_label"
                                                                ),
                                                                statusText,
                                                                statusColor
                                                        )
                                                ),
                                                options = emptyList(),
                                                warnings = emptyList(),
                                                dangers = emptyList(),
                                                actions = listOf(
                                                        GuiLoreActions.menuSingleClick(
                                                                lang,
                                                                player,
                                                                lang.getMessage(
                                                                        player,
                                                                        "gui.settings.notification.action.toggle"
                                                                )
                                                        )
                                                ),
                                                glint = worldData.notificationEnabled
                                        ),
                                        null
                                )
                        inventory.setItem(notificationSettingSlot, notificationItem)
                        inventory.bindRuntimeOperation(
                                notificationSettingSlot,
                                WorldSettingsRuntimeOperation.TOGGLE_NOTIFICATION,
                        )
                }

                // スロット32: 環境設定 (オーナーのみ)
                if (ownerActionsAllowed && !isBedrock) {
                        val environmentLoreBuilder =
                                GuiLoreBuilder(lang, player)
                                        .block(
                                                lang.getMessageList(
                                                        player,
                                                        "gui.settings.environment.blocks.summary"
                                                ).map(GuiLoreLine::Text)
                                        )
                        if (!isInWorld && warningLore != null) {
                                environmentLoreBuilder.warning(warningLore)
                        }
                        environmentLoreBuilder.actions(
                                lang.getMessage(player, "gui.settings.environment.action.open")
                        )
                        inventory.setItem(
                                32,
                                createItemComponent(
                                        plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "environment",
                                                Material.GRASS_BLOCK
                                        ),
                                        lang.getMessage(player, "gui.settings.environment.display"),
                                        environmentLoreBuilder.buildSpec(),
                                        null
                                )
                        )
                        inventory.bindRuntimeOperation(
                                32,
                                WorldSettingsRuntimeOperation.OPEN_ENVIRONMENT,
                        )
                }

                // スロット33: 重大な設定 (オーナーのみ)
                // スロット33: 重大な設定 (オーナーのみ)
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                if (ownerActionsAllowed && stats.criticalSettingsEnabled) {
                        val criticalLoreBuilder =
                                GuiLoreBuilder(lang, player)
                                        .block(
                                                lang.getMessageList(
                                                        player,
                                                        "gui.settings.critical.blocks.summary"
                                                ).map(GuiLoreLine::Text)
                                        )
                        if (!isInWorld && warningLore != null) {
                                criticalLoreBuilder.warning(warningLore)
                        }
                        criticalLoreBuilder.actions(
                                lang.getMessage(player, "gui.settings.critical.action.open")
                        )
                        inventory.setItem(
                                33,
                                createItemComponent(
                                        plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "critical",
                                                Material.TNT
                                        ),
                                        lang.getMessage(player, "gui.settings.critical.display"),
                                        criticalLoreBuilder.buildSpec(),
                                        null
                                )
                        )
                        inventory.bindRuntimeOperation(
                                33,
                                WorldSettingsRuntimeOperation.OPEN_CRITICAL,
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
                val infoLines = buildList<GuiLoreLine> {
                        if (worldData.description.isNotEmpty()) {
                                add(GuiLoreLine.UserText(worldData.description))
                                add(GuiLoreLine.Spacer)
                        }
                        add(GuiLoreLine.Data(
                                lang.getMessage(player, "gui.settings.main_info.owner_label"),
                                PlayerNameUtil.getNameOrDefault(worldData.owner, lang.getMessage(player, "general.unknown")),
                                "§b"
                        ))
                        if (!isSpecialExpansion) {
                                add(GuiLoreLine.Data(lang.getMessage(player, "gui.settings.main_info.expansion_label"), "$currentLevel/$maxLevel", "§e"))
                        }
                        add(GuiLoreLine.Data(
                                lang.getMessage(player, "gui.settings.main_info.created_label"),
                                "${displayFormatter.format(createdAtDate)} ($createdInfo)",
                                "§e"
                        ))
                        if (!isSpecialExpansion) {
                                add(GuiLoreLine.Data(
                                        lang.getMessage(player, "gui.settings.main_info.archive_label"),
                                        lang.getMessage(player, "gui.settings.main_info.archive_value", mapOf("days" to daysRemaining, "date" to dateStr)),
                                        "§6"
                                ))
                        }
                        add(GuiLoreLine.Data(
                                lang.getMessage(player, "gui.settings.main_info.members_label"),
                                lang.getMessage(player, "gui.settings.main_info.members_value", mapOf("members" to totalCount, "online" to onlineCount)),
                                "§f"
                        ))
                        add(GuiLoreLine.Data(lang.getMessage(player, "gui.settings.main_info.publish_label"), publishLevelName, publishLevelColor))
                        add(GuiLoreLine.Data(lang.getMessage(player, "gui.settings.main_info.favorites_label"), worldData.favorite, "§c"))
                        add(GuiLoreLine.Data(lang.getMessage(player, "gui.settings.main_info.visitors_label"), worldData.recentVisitors.sum(), "§b"))
                        add(GuiLoreLine.Metadata("UUID", worldData.uuid))
                        if (plugin.worldConfigRepository.findByWorldName(player.world.name)?.uuid != worldData.uuid) {
                                add(GuiLoreActions.singleClick(lang, player, lang.getMessage(player, "gui.player_world.world_item.warp")))
                        }
                }

                val infoItem =
                        createItemComponent(
                                worldData.icon,
                                lang.getMessage(
                                        player,
                                        "gui.settings.main_info.name",
                                        mapOf("world" to worldData.name)
                                ),
                                GuiLoreSpec.Rich(infoLines, GuiLoreFrame.BOTH),
                                null
                        )
                inventory.setItem(worldInfoSlot, infoItem)
                if (plugin.worldConfigRepository.findByWorldName(player.world.name)?.uuid != worldData.uuid) {
                        inventory.bindRuntimeOperation(
                                worldInfoSlot,
                                WorldSettingsRuntimeOperation.WARP,
                        )
                }

                // スロット51: 訪問中のプレイヤー管理
                val visitors =
                        Bukkit.getWorld(targetWorldName)?.players?.filter {
                                it.uniqueId != worldData.owner &&
                                        !worldData.moderators.contains(it.uniqueId) &&
                                        !worldData.members.contains(it.uniqueId)
                        }
                                ?: emptyList()
                if (hasManagePermission && visitors.isNotEmpty()) {
                        val visitorLore =
                                GuiLoreBuilder(lang, player)
                                        .block(listOf(GuiLoreLine.Data(
                                                lang.getMessage(player, "gui.settings.visitors.blocks.count_label"),
                                                visitors.size,
                                                "§e"
                                        )))
                                        .actions(
                                                lang.getMessage(player, "gui.settings.visitors.action.open")
                                        )
                                        .buildSpec()

                        inventory.setItem(
                                51,
                                createItemComponent(
                                        plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "visitor",
                                                Material.SPYGLASS
                                        ),
                                        lang.getMessage(player, "gui.settings.visitors.display"),
                                        visitorLore,
                                        null
                                )
                        )
                        inventory.bindRuntimeOperation(
                                51,
                                WorldSettingsRuntimeOperation.MANAGE_VISITORS,
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
                        val portalLore =
                                GuiLoreBuilder(lang, player)
                                        .block(
                                                lang.getMessageList(
                                                        player,
                                                        "gui.settings.portals.blocks.summary"
                                                ).map(GuiLoreLine::Text)
                                        )
                                        .actions(
                                                lang.getMessage(player, "gui.settings.portals.action.open")
                                        )
                                        .buildSpec()
                        inventory.setItem(
                                52,
                                createItemComponent(
                                        plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "portals",
                                                Material.END_PORTAL_FRAME
                                        ),
                                        lang.getMessage(player, "gui.settings.portals.display"),
                                        portalLore,
                                        null
                                )
                        )
                        inventory.bindRuntimeOperation(
                                52,
                                WorldSettingsRuntimeOperation.MANAGE_PORTALS,
                        )
                }

                // 空きスロットを灰色板ガラスで埋める
                val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
                for (i in 0 until inventory.size) {
                        val item = inventory.getItem(i)
                        if (item == null || item.type == Material.AIR) {
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
                                if (presentationMode == WorldSettingsLayoutMode.COMPACT_LIMITED_OWNER) {
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
                                if (presentationMode == WorldSettingsLayoutMode.COMPACT_LIMITED_OWNER) {
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
                                if (presentationMode == WorldSettingsLayoutMode.COMPACT_LIMITED_OWNER) {
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
                        }
                        .zip(validSlots)
                        .forEach { (resolved, slot) ->
                                val presentation = resolved.presentation
                                val item = CCSystem.getAPI().getGuiElementService()
                                        .menuCapability(presentation)
                                val interaction = if (resolved.actionable) {
                                        MenuInteraction.Action(
                                                actionId = ACTION_CAPABILITY,
                                                acceptedClicks = resolved.acceptedClicks,
                                                payload = arguments +
                                                        (CAPABILITY_ID_PAYLOAD to resolved.capabilityId),
                                        )
                                } else {
                                        MenuInteraction.DisplayOnly
                                }
                                inventory.setSemanticItem(slot, item, interaction)
                        }
        }

        private fun handleCapability(context: MenuActionContext): MenuActionResult {
                val capabilityId = context.payload[CAPABILITY_ID_PAYLOAD]
                        ?: return MenuActionResult.Ignored
                val arguments = context.payload - CAPABILITY_ID_PAYLOAD
                return CCSystem.getAPI().getMenuCapabilityService()
                        .execute(capabilityId, context.player, context.click, arguments)
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
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size)
                inventory.applyStandardFrame()

                val infoItem =
                        createItem(
                                Material.PAPER,
                                lang.getMessage(player, "gui.archive.question"),
                                GuiLoreSpec.Rich(lang.getMessageList(player, "gui.archive.warning").map(GuiLoreLine::Warning), GuiLoreFrame.BOTH),
                                ItemTag.TYPE_GUI_INFO
                        )
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(player, "gui.archive.confirm"),
                                GuiLoreSpec.Rich(listOf(GuiLoreLine.Text(lang.getMessage(player, "gui.archive.confirm_desc"))), GuiLoreFrame.NONE),
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(player, "gui.archive.cancel"),
                                GuiLoreSpec.Rich(listOf(GuiLoreLine.Text(lang.getMessage(player, "gui.archive.cancel_desc"))), GuiLoreFrame.NONE),
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
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size)
                inventory.applyStandardFrame()

                val infoItem =
                        createItem(
                                Material.PAPER,
                                lang.getMessage(player, "gui.unarchive_confirm.title"),
                                GuiLoreSpec.Blocks(listOf(
                                        GuiLoreBlock(lang.getMessageList(player, "gui.unarchive_confirm.description").map(GuiLoreLine::Text)),
                                        GuiLoreBlock(listOf(GuiLoreActions.singleClick(lang, player, lang.getMessage(player, "gui.unarchive_confirm.action"))))
                                )),
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
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size)
                // ヘッダー・フッター
                val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
                inventory.applyStandardFrame()

                inventory.setItem(
                        20,
                        createItem(
                                Material.MAP,
                                lang.getMessage(player, "gui.expansion.center_expand.name"),
                                GuiLoreSpec.Rich(lang.getMessageList(player, "gui.expansion.center_expand.lore").map(GuiLoreLine::Text), GuiLoreFrame.BOTH),
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
                                GuiLoreSpec.Rich(lang.getMessageList(player, "gui.expansion.direction_expand.lore").map(GuiLoreLine::Text), GuiLoreFrame.BOTH),
                                null
                        )
                )
                inventory.bindRuntimeOperation(
                        24,
                        WorldSettingsRuntimeOperation.EXPAND_DIRECTION,
                )

                // 戻るボタン
                inventory.setItem(
                        40,
                        GuiHelper.createReturnItem(
                                plugin,
                                player,
                                "world_settings"
                        )
                )
                inventory.bindRuntimeOperation(40, WorldSettingsRuntimeOperation.BACK)

                val canStepBack = worldData.latestBorderExpansionRecord() != null
                if (canStepBack) {
                        inventory.setItem(
                                42,
                                createItem(
                                        Material.RECOVERY_COMPASS,
                                        lang.getMessage(player, "gui.expansion.step_back.name"),
                                        GuiLoreSpec.Rich(lang.getMessageList(player, "gui.expansion.step_back.lore").map(GuiLoreLine::Text), GuiLoreFrame.BOTH),
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
                        if (inventory.getItem(i) == null) inventory.setItem(i, grayPane)
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
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size)
                inventory.applyStandardFrame()

                val loreLines = mutableListOf<GuiLoreLine>(
                        GuiLoreLine.Warning(lang.getMessage(player, "gui.confirm.step_back_expansion.question"))
                )
                loreLines += lang.getMessageList(player, "gui.confirm.step_back_expansion.description").map(GuiLoreLine::Text)
                if (worldData.latestBorderExpansionRecord()?.modified == true) {
                        loreLines += lang.getMessageList(player, "gui.confirm.step_back_expansion.modified_warning").map(GuiLoreLine::Warning)
                }
                loreLines += getSpawnAdjustmentWarning(player, worldData, borderResetTargetForStepBack(worldData))
                val infoItem =
                        createItem(
                                Material.RECOVERY_COMPASS,
                                lang.getMessage(player, "gui.confirm.step_back_expansion.display"),
                                GuiLoreSpec.Rich(loreLines, GuiLoreFrame.BOTH),
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
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size)
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
                                GuiLoreSpec.Rich(listOf(
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
                                GuiLoreSpec.Rich(listOf(GuiLoreLine.Text(lang.getMessage(player, "gui.expansion.execute_desc"))), GuiLoreFrame.NONE),
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(player, "gui.expansion.cancel"),
                                GuiLoreSpec.Rich(listOf(GuiLoreLine.Text(lang.getMessage(player, "gui.expansion.cancel_desc"))), GuiLoreFrame.NONE),
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )

                val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
                for (i in 0 until inventory.size) {
                        if (inventory.getItem(i) == null) inventory.setItem(i, grayPane)
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
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.MANAGE_MEMBERS,
                        isGui = true
                )

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

                val inventory = RuntimeItemBuffer(layout.size)
                inventory.clear()

                val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
                inventory.applyStandardFrame()

                // メンバーリストの描画
                val isAdminFlow = plugin.settingsSessionManager.getSession(player)?.isAdminFlow == true
                val canManageRoles = worldData.owner == player.uniqueId || isAdminFlow
                currentPageMembers.forEachIndexed { index, entry ->
                        val slot = layout.itemSlots.getOrNull(index) ?: return@forEachIndexed
                        var capabilityId: String? = null
                                val memberItem =
                                        if (entry.pendingDecisionId != null) {
                                        createPendingItem(
                                                viewer = player,
                                                targetUuid = entry.playerUuid,
                                                decisionId = entry.pendingDecisionId,
                                                createdAt = entry.pendingCreatedAt ?: 0L,
                                                pendingType = entry.pendingType ?: PendingInteractionType.MEMBER_INVITE
                                        )
                                } else {
                                        val subject = MemberManagementCapabilitySubject(
                                                player,
                                                worldData,
                                                entry.playerUuid,
                                        )
                                        val service = CCSystem.getAPI().getMenuCapabilityService()
                                        val capabilityView = service
                                                .definitions(MemberManagementCapabilityContract.PLACEMENT)
                                                .firstNotNullOfOrNull { definition ->
                                                        service.resolve(
                                                                definition.capabilityId,
                                                                player,
                                                                attributes = mapOf(
                                                                        MemberManagementCapabilityContract.SUBJECT_ATTRIBUTE to subject,
                                                                ),
                                                        )
                                                }
                                        capabilityId = capabilityView?.capabilityId
                                        createMemberItem(
                                                player,
                                                entry.playerUuid,
                                                entry.role ?: lang.getMessage(player, "role.member"),
                                                canManageRoles,
                                                capabilityView,
                                        )
                                }
                        inventory.setItem(slot, memberItem)
                        val operation = when (entry.pendingType) {
                                PendingInteractionType.MEMBER_INVITE ->
                                        WorldSettingsRuntimeOperation.PENDING_INVITE
                                PendingInteractionType.MEMBER_REQUEST ->
                                        WorldSettingsRuntimeOperation.PENDING_REQUEST
                                else -> WorldSettingsRuntimeOperation.MEMBER
                        }
                        inventory.bindRuntimeOperation(
                                slot,
                                operation,
                                acceptedClicks = if (entry.pendingDecisionId == null) {
                                        MenuAcceptedClicks.LEFT_RIGHT
                                } else {
                                        MenuAcceptedClicks.LEFT
                                },
                                payload = buildMap {
                                        put(ROUTE_TARGET_UUID, entry.playerUuid.toString())
                                        entry.pendingDecisionId?.let {
                                                put(ROUTE_DECISION_ID, it.toString())
                                        }
                                        capabilityId?.let {
                                                put(ROUTE_CAPABILITY_ID, it)
                                        }
                                },
                        )
                }

                // ナビゲーション
                if (currentPage > 0) {
                        inventory.setItem(
                                layout.previousPageSlot,
                                GuiHelper.createPrevPageItem(
                                        plugin,
                                        player,
                                        "world_settings",
                                        currentPage - 1
                                )
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
                                GuiHelper.createNextPageItem(
                                        plugin,
                                        player,
                                        "world_settings",
                                        currentPage + 1
                                )
                        )
                        inventory.bindRuntimeOperation(
                                layout.nextPageSlot,
                                WorldSettingsRuntimeOperation.PAGE,
                                payload = mapOf(ROUTE_PAGE to (currentPage + 1).toString()),
                        )
                }

                // 戻ると招待の位置は共通レイアウトから取得し、一覧本文の行数変更に追従させる。
                inventory.setItem(
                        footerStart + 4,
                        GuiHelper.createReturnItem(
                                plugin,
                                player,
                                "world_settings"
                        )
                )

                // メンバー招待ボタン
                val canForceAddMember = PermissionManager.canForceAddMember(player)
                val inviteLore =
                        if (canForceAddMember) {
                                val desc = lang.getMessage(player, "gui.member_management.invite.desc")
                                GuiLoreSpec.Blocks(listOf(
                                    com.awabi2048.ccsystem.api.gui.GuiLoreBlock(
                                        listOf(
                                                GuiLoreLine.Text(desc),
                                        )
                                    ),
                                    com.awabi2048.ccsystem.api.gui.GuiLoreBlock(
                                        listOf(
                                                GuiLoreLine.Action(
                                                        lang.getMessage(player, "lore.click.any"),
                                                        lang.getMessage(player, "gui.member_management.invite.action.normal")
                                                ),
                                                GuiLoreLine.Action(
                                                        lang.getMessage(player, "lore.click.shift_any"),
                                                        lang.getMessage(player, "gui.member_management.invite.action.force")
                                                )
                                        )
                                    )
                                ))
                        } else {
                                GuiLoreSpec.Blocks(listOf(
                                    com.awabi2048.ccsystem.api.gui.GuiLoreBlock(listOf(
                                        GuiLoreLine.Text(lang.getMessage(player, "gui.member_management.invite.desc"))
                                    )),
                                    com.awabi2048.ccsystem.api.gui.GuiLoreBlock(listOf(
                                        GuiLoreActions.singleClick(
                                                lang,
                                                player,
                                                lang.getMessage(player, "gui.member_management.invite.action.normal")
                                        )
                                    ))
                                ))
                }
                inventory.setItem(
                        footerStart + 6,
                        createItem(
                                Material.PAPER,
                                lang.getMessage(player, "gui.member_management.invite.name"),
                                inviteLore,
                                null
                        )
                )
                inventory.bindRuntimeOperation(
                        footerStart + 4,
                        WorldSettingsRuntimeOperation.BACK,
                )
                inventory.bindRuntimeOperation(
                        footerStart + 6,
                        WorldSettingsRuntimeOperation.INVITE_MEMBER,
                )

                if (isAdminFlow) {
                        val ownerName =
                                PlayerNameUtil.getNameOrDefault(
                                        worldData.owner,
                                        lang.getMessage(player, "general.unknown")
                                )
                        inventory.setItem(
                                footerStart + 2,
                                createItem(
                                        Material.NAME_TAG,
                                        lang.getMessage(
                                                player,
                                                "gui.member_management.admin_owner_reset.name"
                                        ),
                                        GuiLoreSpec.Blocks(
                                                listOf(
                                                        com.awabi2048.ccsystem.api.gui.GuiLoreBlock(
                                                                listOf(
                                                                        GuiLoreLine.Data(
                                                                                lang.getMessage(
                                                                                        player,
                                                                                        "gui.member_management.admin_owner_reset.current_owner"
                                                                                ),
                                                                                ownerName,
                                                                                "§e"
                                                                        )
                                                                )
                                                        ),
                                                        com.awabi2048.ccsystem.api.gui.GuiLoreBlock(
                                                                listOf(
                                                                        GuiLoreActions.singleClick(
                                                                                lang,
                                                                                player,
                                                                                lang.getMessage(
                                                                                        player,
                                                                                        "gui.member_management.admin_owner_reset.action"
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        ),
                                        null
                                )
                        )
                        inventory.bindRuntimeOperation(
                                footerStart + 2,
                                WorldSettingsRuntimeOperation.MEMBER_OWNER_RESET,
                        )
                }

                // 背景埋め
                val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
                for (i in 9 until footerStart) {
                        if (inventory.getItem(i) == null) inventory.setItem(i, grayPane)
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
                val lang = plugin.languageManager
                val targetName = PlayerNameUtil.getNameOrDefault(targetUuid, lang.getMessage(player, "general.unknown"))
                val title = lang.getMessage(player, "gui.member_management.pending_cancel_confirm.title")
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.MEMBER_PENDING_INVITE_CANCEL_CONFIRM,
                        isGui = true
                )

                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size)
                inventory.applyStandardFrame()

                val item = ItemStack(Material.PLAYER_HEAD)
                val meta = item.itemMeta as? SkullMeta ?: return
                meta.owningPlayer = Bukkit.getOfflinePlayer(targetUuid)
                meta.displayName(
                        LegacyComponentSerializer.legacySection()
                                .deserialize(
                                        lang.getMessage(
                                                player,
                                                "gui.member_management.pending_item.name",
                                                mapOf("player" to targetName)
                                        )
                                )
                                .decoration(TextDecoration.ITALIC, false)
                )
                meta.lore(GuiItemFactory.menuLore(listOf(
                        GuiLoreLine.Warning(lang.getMessage(
                                player,
                                "gui.member_management.pending_cancel_confirm.body",
                                mapOf("player" to targetName)
                        ))
                )))
                item.itemMeta = meta
                inventory.setItem(22, item)

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
                presentRuntime(
                        player,
                        title,
                        inventory,
                        WorldSettingsRuntimeScreen.MEMBER_PENDING_INVITE_CANCEL_CONFIRM,
                        worldData.uuid,
                        targetUuid = targetUuid,
                        decisionId = decisionId,
                )
        }

        fun openMemberRemoveConfirmation(
                player: Player,
                worldData: WorldData,
                targetUuid: java.util.UUID
        ) {
                val lang = plugin.languageManager
                val targetName = PlayerNameUtil.getNameOrDefault(targetUuid, lang.getMessage(player, "general.unknown"))

                val title =
                        lang.getMessage(
                                player,
                                "gui.member_management.remove_confirm.title",
                                mapOf("player" to targetName)
                        )
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.MEMBER_REMOVE_CONFIRM,
                        isGui = true
                )
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size)
                inventory.applyStandardFrame()

                val lore = GuiLoreSpec.Rich(listOf(
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
                                GuiLoreSpec.Rich(listOf(GuiLoreLine.Text(
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
                                GuiLoreSpec.Rich(listOf(GuiLoreLine.Warning(
                                        lang.getMessage(
                                                player,
                                                "gui.member_management.remove_confirm.confirm_desc"
                                        )
                                )), GuiLoreFrame.NONE),
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )


                inventory.bindConfirmation(confirmSlot = 24, cancelSlot = 20)
                presentRuntime(
                        player,
                        title,
                        inventory,
                        WorldSettingsRuntimeScreen.MEMBER_REMOVE_CONFIRM,
                        worldData.uuid,
                        targetUuid = targetUuid,
                )
        }

        fun openMemberTransferConfirmation(
                player: Player,
                worldData: WorldData,
                targetUuid: java.util.UUID
        ) {
                val lang = plugin.languageManager
                val targetName = PlayerNameUtil.getNameOrDefault(targetUuid, lang.getMessage(player, "general.unknown"))

                val title =
                        lang.getMessage(
                                player,
                                "gui.member_management.transfer_confirm.title",
                                mapOf("player" to targetName)
                        )
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.MEMBER_TRANSFER_CONFIRM,
                        isGui = true
                )
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size)
                inventory.applyStandardFrame()

                val lore = GuiLoreSpec.Rich(listOf(
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
                                GuiLoreSpec.Rich(listOf(GuiLoreLine.Text(
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
                                GuiLoreSpec.Rich(listOf(GuiLoreLine.Warning(
                                        lang.getMessage(
                                                player,
                                                "gui.member_management.transfer_confirm.confirm_desc"
                                        )
                                )), GuiLoreFrame.NONE),
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )

                inventory.bindConfirmation(confirmSlot = 24, cancelSlot = 20)
                presentRuntime(
                        player,
                        title,
                        inventory,
                        WorldSettingsRuntimeScreen.MEMBER_TRANSFER_CONFIRM,
                        worldData.uuid,
                        targetUuid = targetUuid,
                )
        }

        private fun createMemberItem(
                viewer: Player,
                uuid: java.util.UUID,
                role: String,
                isOwner: Boolean,
                capabilityView: com.awabi2048.ccsystem.api.gui.ResolvedMenuCapability? = null,
        ): ItemStack {
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

                val item = ItemStack(Material.PLAYER_HEAD)
                val meta = item.itemMeta as? org.bukkit.inventory.meta.SkullMeta ?: return item
                meta.owningPlayer = player

                meta.displayName(
                        Component.text("$color$displayName")
                                .decoration(TextDecoration.ITALIC, false)
                )

                val itemLore = mutableListOf<GuiLoreLine>()

                // Info section
                if (capabilityView != null) {
                        itemLore += capabilityView.presentation.embeddedLoreBlocks
                                .flatMap(GuiLoreBlock::lines)
                } else if (isOnline) {
                        itemLore += GuiLoreLine.StyledText(
                                lang.getMessage(viewer, "gui.member_management.item.online_label"),
                                "§a",
                                false
                        )
                } else {
                        val onlineLabel = lang.getMessage(viewer, "gui.member_management.item.last_online_label")
                        val lastOnline = stats.lastOnline
                                ?.let { formatStoredDateTimeForPlayer(viewer, it) }
                                ?: lang.getMessage(viewer, "general.unknown")
                        itemLore += GuiLoreLine.Data(onlineLabel, lastOnline, "§f")
                }
                if (capabilityView == null) {
                        itemLore += GuiLoreLine.Data(
                                lang.getMessage(viewer, "gui.member_management.item.role_label"),
                                role,
                                "§f",
                        )
                }

                if (capabilityView == null && isOwner && role != lang.getMessage(viewer, "role.owner")) {
                        val nextRole = if (role == lang.getMessage(null as Player?, "role.member")) {
                                lang.getMessage(null as Player?, "role.moderator")
                        } else {
                                lang.getMessage(null as Player?, "role.member")
                        }
                        itemLore.addAll(
                                                listOf(
                                                        GuiLoreLine.Action(
                                                                lang.getMessage(viewer, "lore.click.left"),
                                                                lang.getMessage(viewer, "gui.member_management.item.action.change_role", mapOf("next_role" to nextRole))
                                                        ),
                                                        GuiLoreLine.Action(
                                                                lang.getMessage(viewer, "lore.click.shift_left"),
                                                                lang.getMessage(viewer, "gui.member_management.item.action.transfer_owner")
                                                        ),
                                                        GuiLoreLine.Action(
                                                                lang.getMessage(viewer, "lore.click.shift_right"),
                                                                lang.getMessage(viewer, "gui.member_management.item.action.remove_member")
                                                        )
                                                )
                        )
                }

                meta.lore(
                        if (capabilityView == null) {
                                GuiItemFactory.menuLore(itemLore)
                        } else {
                                CCSystem.getAPI().getLoreService().render(
                                        GuiLoreSpec.Blocks(
                                                capabilityView.presentation.embeddedLoreBlocks,
                                        ),
                                )
                        },
                )
                item.itemMeta = meta

                return item
        }

        private fun createPendingItem(
                viewer: Player,
                targetUuid: UUID,
                decisionId: UUID,
                createdAt: Long,
                pendingType: PendingInteractionType
        ): ItemStack {
                val worldName =
                        plugin.worldConfigRepository
                                .findByUuid(
                                        plugin.settingsSessionManager.getSession(viewer)?.worldUuid
                                                ?: return ItemStack(Material.BARRIER)
                                )
                                ?.name
                                ?: plugin.languageManager.getMessage(viewer, "general.unknown")
                val item =
                        PendingInteractionItemFactory.createItem(
                                plugin = plugin,
                                viewer = viewer,
                                subjectUuid = targetUuid,
                                type =
                                        when (pendingType) {
                                                PendingInteractionType.MEMBER_INVITE -> me.awabi2048.myworldmanager.service.PendingDecisionManager.PendingType.MEMBER_INVITE
                                                PendingInteractionType.MEMBER_REQUEST -> me.awabi2048.myworldmanager.service.PendingDecisionManager.PendingType.MEMBER_REQUEST
                                        },
                                worldName = worldName,
                                createdAt = createdAt,
                                decisionId = decisionId,
                                actionMode = PendingInteractionActionMode.CANCEL,
                                itemTagType = null,
                        )
                return item
        }

        private fun formatPendingInviteDateTimeForPlayer(player: Player, timestamp: Long): String {
                val dateTime = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()
                return dateTime.format(pendingInviteDateTimeFormatterFor(player))
        }

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
                val lang = plugin.languageManager
                val title = lang.getMessage(player, "gui.visitor_management.title")
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.MANAGE_VISITORS,
                        isGui = true
                )
                val world = Bukkit.getWorld("my_world.${worldData.uuid}") ?: return
                val visitorPlayers =
                        world.players.filter {
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
                val inventory = RuntimeItemBuffer(layout.size)
                inventory.applyStandardFrame()

                // プレイヤーリストの描画
                val isAdminFlow = plugin.settingsSessionManager.getSession(player)?.isAdminFlow == true
                val canKick =
                        worldData.owner == player.uniqueId ||
                                worldData.moderators.contains(player.uniqueId) ||
                                isAdminFlow

                currentPageVisitors.forEachIndexed { index, visitor ->
                        val slot = layout.itemSlots[index]
                        inventory.setItem(
                                slot,
                                createVisitorItem(player, visitor.uniqueId, canKick)
                        )
                        inventory.bindRuntimeOperation(
                                slot,
                                WorldSettingsRuntimeOperation.VISITOR,
                                payload = mapOf(ROUTE_TARGET_UUID to visitor.uniqueId.toString()),
                        )
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
                inventory.setItem(
                        layout.actionSlot,
                        createItem(
                                Material.REDSTONE,
                                lang.getMessage(player, "gui.common.back"),
                                GuiLoreSpec.None,
                                null
                        )
                )
                inventory.bindRuntimeOperation(
                        layout.actionSlot,
                        WorldSettingsRuntimeOperation.BACK,
                )

                // 背景埋め
                val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
                for (i in 9 until layout.size - 9) {
                        if (inventory.getItem(i) == null) inventory.setItem(i, grayPane)
                }

                presentRuntime(
                        player,
                        title,
                        inventory,
                        WorldSettingsRuntimeScreen.VISITOR_MANAGEMENT,
                        worldData.uuid,
                        replaceCurrent,
                        routeArguments = mapOf(ROUTE_PAGE to visitorPage.page.toString()),
                )
        }

        fun openVisitorKickConfirmation(
                player: Player,
                worldData: WorldData,
                targetUuid: java.util.UUID
        ) {
                val lang = plugin.languageManager
                val targetName = PlayerNameUtil.getNameOrDefault(targetUuid, lang.getMessage(player, "general.unknown"))

                val title =
                        lang.getMessage(
                                player,
                                "gui.visitor_management.kick_confirm.title",
                                mapOf("player" to targetName)
                        )
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.VISITOR_KICK_CONFIRM,
                        isGui = true
                )
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size)
                inventory.applyStandardFrame()

                inventory.setItem(
                        22,
                        createItem(
                                Material.PAPER,
                                lang.getMessage(
                                        player,
                                        "gui.visitor_management.kick_confirm.question"
                                ),
                                GuiLoreSpec.Rich(listOf(
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
                                GuiLoreSpec.Rich(listOf(GuiLoreLine.Text(
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
                                GuiLoreSpec.Rich(listOf(GuiLoreLine.Warning(
                                        lang.getMessage(
                                                player,
                                                "gui.visitor_management.kick_confirm.confirm_desc"
                                        )
                                )), GuiLoreFrame.NONE),
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )

                inventory.bindConfirmation(confirmSlot = 24, cancelSlot = 20)
                presentRuntime(
                        player,
                        title,
                        inventory,
                        WorldSettingsRuntimeScreen.VISITOR_KICK_CONFIRM,
                        worldData.uuid,
                        targetUuid = targetUuid,
                )
        }

        private fun createVisitorItem(
                viewer: Player,
                uuid: java.util.UUID,
                canKick: Boolean
        ): ItemStack {
                val lang = plugin.languageManager
                val player = Bukkit.getOfflinePlayer(uuid)
                val isOnline = player.isOnline
                val onlineColor = lang.getMessage(viewer, "publish_level.color.online")
                val offlineColor = lang.getMessage(viewer, "publish_level.color.offline")
                val color = if (isOnline) onlineColor else offlineColor

                val item = ItemStack(Material.PLAYER_HEAD)
                val meta = item.itemMeta as? org.bukkit.inventory.meta.SkullMeta ?: return item
                meta.owningPlayer = player

                meta.displayName(
                        LegacyComponentSerializer.legacySection()
                                .deserialize(
                                        "$color${player.name ?: lang.getMessage(viewer, "general.unknown")}"
                                )
                                .decoration(TextDecoration.ITALIC, false)
                )

                val lines = mutableListOf<String>()

                val statusText =
                        if (isOnline) lang.getMessage(viewer, "status.online")
                        else lang.getMessage(viewer, "status.offline")
                val statusColor = if (isOnline) onlineColor else offlineColor
                lines.add(
                        lang.getMessage(
                                viewer,
                                "gui.common.status_display",
                                mapOf("color" to statusColor, "status" to statusText)
                        )
                )

                if (canKick) {
                        lines.add(
                                CCSystem.getAPI().getLoreService().render(
                                        GuiLoreSpec.Rich(
                                                listOf(
                                                        GuiLoreActions.singleClick(
                                                                lang,
                                                                viewer,
                                                                lang.getMessage(viewer, "gui.visitor_management.item.kick")
                                                        )
                                                ),
                                                GuiLoreFrame.NONE
                                        )
                                ).joinToString("") { net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(it) }
                        )
                }

                val lore = GuiItemFactory.menuLore(lines.map(GuiLoreLine::Text))
                meta.lore(lore)
                item.itemMeta = meta

                return item
        }

        private fun createItem(
                material: Material,
                name: String,
                lore: GuiLoreSpec,
                tag: String?
        ): ItemStack {
                return GuiItemFactory.item(material, name, lore)
        }

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
        ): ItemStack {
                return GuiItemFactory.item(material, name, lore)
        }

        fun openCriticalSettings(player: Player, worldData: WorldData) {
                if (!plugin.playerStatsRepository.findByUuid(player.uniqueId).criticalSettingsEnabled) {
                        return
                }
                val lang = plugin.languageManager
                val title = lang.getMessage(player, "gui.critical.title")
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())

                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.CRITICAL_SETTINGS,
                        isGui = true
                )

                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size)
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

                val archiveLoreBuilder = GuiLoreBuilder(lang, player)
                    .block(lang.getMessageList(player, "gui.critical.archive_world.description").map(GuiLoreLine::Text))
                if (isOnCooldown) {
                    archiveLoreBuilder
                        .warning(lang.getMessage(player, "gui.critical.archive_world.cooldown_warning", mapOf("cooldown_hours" to cooldownHours)))
                        .block(listOf(GuiLoreLine.StyledText(lang.getMessage(player, "gui.critical.archive_world.remaining", mapOf("hours_remaining" to hoursRemaining)), "§8", false)))
                } else {
                    archiveLoreBuilder.actions(lang.getMessage(player, "gui.critical.archive_world.action"))
                }
                val archiveLore = archiveLoreBuilder.buildSpec()

                // 動的スロット配置: ボーダー拡張の有無に応じて決定
                // - 拡張あり (level > 0): スロット20=リセット, 22=アーカイブ, 24=削除
                // - 拡張なし (level == 0): スロット20=削除, 24=アーカイブ
                val isExpansionEnabled = worldData.borderExpansionLevel > 0
                val hasSpecialExpansion = worldData.borderExpansionLevel == WorldData.EXPANSION_LEVEL_SPECIAL

                if (isExpansionEnabled || hasSpecialExpansion) {
                    // 拡張リセットボタン (スロット20)
                    val currentLevel = worldData.borderExpansionLevel
                    val resetLore: GuiLoreSpec

                    if (currentLevel > 0) {
                        val resetRefund = (calculateTotalExpansionCost(currentLevel) * refundRate).toInt()
                        resetLore = GuiLoreBuilder(lang, player)
                            .block(listOf(
                                GuiLoreLine.Data(lang.getMessage(player, "gui.critical.reset_expansion.level_label"), currentLevel.toString(), "§e"),
                                GuiLoreLine.Data(lang.getMessage(player, "gui.critical.reset_expansion.refund_label"), resetRefund.toString(), "§e")
                            ))
                            .warning(lang.getMessage(player, "gui.critical.reset_expansion.warning"))
                            .actions(lang.getMessage(player, "gui.critical.reset_expansion.action"))
                            .buildSpec()
                    } else {
                        resetLore = GuiLoreSpec.Rich(
                            listOf(GuiLoreLine.Warning(lang.getMessage(player, "gui.critical.reset_expansion.unavailable"))),
                            GuiLoreFrame.BOTH
                        )
                    }

                    inventory.setItem(
                        20,
                        createItem(
                            Material.BARRIER,
                            lang.getMessage(player, "gui.critical.reset_expansion.display"),
                            resetLore,
                            null
                        )
                    )
                    if (currentLevel > 0) {
                            inventory.bindRuntimeOperation(
                                    20,
                                    WorldSettingsRuntimeOperation.RESET_EXPANSION,
                            )
                    }

                    // アーカイブボタン (スロット22)
                    inventory.setItem(
                        22,
                            createItem(
                            plugin.menuConfigManager.getIconMaterial("world_settings", "critical", Material.CHEST),
                            lang.getMessage(player, "gui.critical.archive_world.display"),
                            archiveLore,
                            null
                        )
                    )
                    if (!isOnCooldown) {
                            inventory.bindRuntimeOperation(22, WorldSettingsRuntimeOperation.ARCHIVE)
                    }
                } else {
                    // 拡張なし: アーカイブは slot 24
                    inventory.setItem(
                        24,
                            createItem(
                            plugin.menuConfigManager.getIconMaterial("world_settings", "critical", Material.CHEST),
                            lang.getMessage(player, "gui.critical.archive_world.display"),
                            archiveLore,
                            null
                        )
                    )
                    if (!isOnCooldown) {
                            inventory.bindRuntimeOperation(24, WorldSettingsRuntimeOperation.ARCHIVE)
                    }
                }

                // 削除ボタン
                val ownerStats = plugin.playerStatsRepository.findByUuid(worldData.owner)
                val canDeleteWorld = !MyWorldManagerApi.isWorldSlotSystemEnabled() ||
                        ownerStats.unlockedWorldSlot > 0
                val deletePlaceholders = mapOf(
                        "points" to refund,
                        "percent" to percent,
                        "slots" to ownerStats.unlockedWorldSlot
                    )
                val deleteLore = if (canDeleteWorld) {
                    GuiLoreBuilder(lang, player)
                        .block(listOf(
                            GuiLoreLine.Text(lang.getMessage(player, "gui.critical.delete_world.description")),
                            GuiLoreLine.Data(lang.getMessage(player, "gui.critical.delete_world.refund_label"), refund.toString(), "§e"),
                            GuiLoreLine.StyledText(lang.getMessage(player, "gui.critical.delete_world.refund_note", mapOf("percent" to percent)), "§8", false)
                        ))
                        .warning(lang.getMessage(player, "gui.critical.delete_world.warning"))
                        .actions(lang.getMessage(player, "gui.critical.delete_world.action"))
                        .buildSpec()
                } else {
                    GuiLoreBuilder(lang, player)
                        .block(listOf(GuiLoreLine.Text(lang.getMessage(player, "gui.critical.delete_world.description"))))
                        .warning(lang.getMessage(player, "gui.critical.delete_world.unavailable_slot"))
                        .block(listOf(GuiLoreLine.Data(lang.getMessage(player, "gui.critical.delete_world.owner_slots_label"), ownerStats.unlockedWorldSlot.toString(), "§e")))
                        .buildSpec()
                }
                val deleteDisplayName = if (canDeleteWorld) {
                        lang.getMessage(player, "gui.critical.delete_world.display")
                } else {
                        "§8§m${lang.getMessage(player, "gui.critical.delete_world.display")}"
                }

                val deleteSlot = if (isExpansionEnabled || hasSpecialExpansion) 24 else 20
                inventory.setItem(
                        deleteSlot,
                        createItem(
                                Material.LAVA_BUCKET,
                                deleteDisplayName,
                                deleteLore,
                                null
                        )
                )
                if (canDeleteWorld) {
                        inventory.bindRuntimeOperation(
                                deleteSlot,
                                WorldSettingsRuntimeOperation.DELETE_WORLD,
                        )
                }

                // 戻るボタン (スロット36から40へ移動)
                inventory.setItem(
                        40,
                        createItem(
                                Material.REDSTONE,
                                lang.getMessage(player, "gui.common.back"),
                                GuiLoreSpec.None,
                                null
                        )
                )
                inventory.bindRuntimeOperation(40, WorldSettingsRuntimeOperation.BACK)

                presentRuntime(player, title, inventory, WorldSettingsRuntimeScreen.CRITICAL_SETTINGS, worldData.uuid)
        }

        private fun calculateTotalExpansionCost(level: Int): Int {
                return WorldRuntimePolicies.totalExpansionCost(plugin.config, level)
        }

        fun openResetExpansionConfirmation(player: Player, worldData: WorldData) {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, "gui.confirm.reset_expansion.title")
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.RESET_EXPANSION_CONFIRM,
                        isGui = true
                )
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size)
                inventory.applyStandardFrame()

                val loreLines = mutableListOf<GuiLoreLine>(
                        GuiLoreLine.Warning(lang.getMessage(player, "gui.confirm.reset_expansion.question"))
                )
                loreLines += lang.getMessageList(player, "gui.confirm.reset_expansion.description").map(GuiLoreLine::Text)
                if (worldData.hasModifiedBorderExpansion()) {
                        loreLines += lang.getMessageList(player, "gui.confirm.reset_expansion.modified_warning").map(GuiLoreLine::Warning)
                }
                loreLines += getSpawnAdjustmentWarning(player, worldData, borderResetTargetForReset(worldData))
                val infoItem =
                        createItem(
                                Material.PAPER,
                                lang.getMessage(player, "gui.confirm.reset_expansion.display"),
                                GuiLoreSpec.Rich(loreLines, GuiLoreFrame.BOTH),
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
                presentRuntime(player, title, inventory, WorldSettingsRuntimeScreen.RESET_EXPANSION_CONFIRM, worldData.uuid)
        }

        fun openResetExpansionSpawnUnsafeConfirmation(player: Player, worldData: WorldData) {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, "gui.confirm.reset_expansion_spawn_unsafe.title")
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.RESET_EXPANSION_CONFIRM_SPAWN_UNSAFE,
                        isGui = true
                )
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size)
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
                                GuiLoreSpec.Rich(loreLines, GuiLoreFrame.BOTH),
                                ItemTag.TYPE_GUI_INFO
                        )
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(player, "gui.common.cancel"),
                                GuiLoreSpec.Rich(listOf(GuiLoreLine.Text(lang.getMessage(player, "gui.common.back"))), GuiLoreFrame.NONE),
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
                presentRuntime(player, title, inventory, WorldSettingsRuntimeScreen.RESET_EXPANSION_SPAWN_UNSAFE_CONFIRM, worldData.uuid)
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
                val lang = plugin.languageManager
                val title = lang.getMessage(player, "gui.confirm.delete_1.title")
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.DELETE_WORLD_CONFIRM,
                        isGui = true
                )
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size)
                inventory.applyStandardFrame()

                val lore = GuiLoreSpec.Rich(
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
                presentRuntime(player, title, inventory, WorldSettingsRuntimeScreen.DELETE_WORLD_CONFIRM, worldData.uuid)
        }

        fun openDeleteWorldConfirmation2(player: Player, worldData: WorldData) {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, "gui.confirm.delete_2.title")
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.DELETE_WORLD_CONFIRM_FINAL,
                        isGui = true
                )
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())
                val inventory = RuntimeItemBuffer(GuiHelper.confirmationLayout().size)
                inventory.applyStandardFrame()

                val lore = com.awabi2048.ccsystem.api.gui.GuiLoreSpec.Rich(
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
                presentRuntime(player, title, inventory, WorldSettingsRuntimeScreen.DELETE_WORLD_FINAL_CONFIRM, worldData.uuid)
        }

        fun openPortalManagement(
                player: Player,
                worldData: WorldData,
                page: Int = 0,
                replaceCurrent: Boolean = false,
        ) {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, "gui.settings.portals.display")
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())

                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.MANAGE_PORTALS,
                        isGui = true
                )

                val allPortals =
                        plugin.portalRepository.findAll().filter { it.worldKey == worldData.worldKey }

                val pageLayout =
                        CCSystem.getAPI().getGuiLayoutService().sevenColumnPage(allPortals.size, page)
                val currentPage = pageLayout.page
                val layout = pageLayout.layout
                val currentPagePortals =
                        allPortals.drop(pageLayout.startIndex).take(pageLayout.itemCount)

                val inventory = RuntimeItemBuffer(layout.size)
                // 背景
                val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
                inventory.applyStandardFrame()

                currentPagePortals.forEachIndexed { index, portal ->
                        val slot = layout.itemSlots[index]
                        inventory.setItem(slot, createPortalManagementItem(player, portal))
                        inventory.bindRuntimeOperation(
                                slot,
                                WorldSettingsRuntimeOperation.PORTAL,
                                payload = mapOf(ROUTE_TARGET_UUID to portal.id.toString()),
                        )
                }

                // ナビゲーション
                if (currentPage > 0) {
                        inventory.setItem(
                                layout.previousPageSlot,
                                GuiHelper.createPrevPageItem(
                                        plugin,
                                        player,
                                        "world_settings",
                                        currentPage - 1
                                )
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
                                GuiHelper.createNextPageItem(
                                        plugin,
                                        player,
                                        "world_settings",
                                        currentPage + 1
                                )
                        )
                        inventory.bindRuntimeOperation(
                                layout.nextPageSlot,
                                WorldSettingsRuntimeOperation.PAGE,
                                payload = mapOf(ROUTE_PAGE to (currentPage + 1).toString()),
                        )
                }

                // 戻るボタン
                inventory.setItem(
                        layout.backSlot,
                        createItem(
                                Material.REDSTONE,
                                lang.getMessage(player, "gui.common.back"),
                                GuiLoreSpec.None,
                                null
                        )
                )
                inventory.bindRuntimeOperation(
                        layout.backSlot,
                        WorldSettingsRuntimeOperation.BACK,
                )

                presentRuntime(
                        player,
                        title,
                        inventory,
                        WorldSettingsRuntimeScreen.PORTAL_MANAGEMENT,
                        worldData.uuid,
                        replaceCurrent,
                        routeArguments = mapOf(ROUTE_PAGE to currentPage.toString()),
                )
        }

        private fun createPortalManagementItem(player: Player, portal: PortalData): ItemStack {
                val lang = plugin.languageManager
                val item = ItemStack(Material.END_PORTAL_FRAME)
                val meta = item.itemMeta ?: return item

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
                meta.displayName(
                        LegacyComponentSerializer.legacySection()
                                .deserialize(displayTitle)
                                .decoration(TextDecoration.ITALIC, false)
                )

                meta.lore(CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(listOf(
                        com.awabi2048.ccsystem.api.gui.GuiLoreBlock(listOf(
                                GuiLoreLine.Data(
                                        lang.getMessage(player, "gui.admin_portals.portal_item.coordinates"),
                                        "${portal.x}, ${portal.y}, ${portal.z}",
                                        "§f"
                                )
                        )),
                        com.awabi2048.ccsystem.api.gui.GuiLoreBlock(listOf(
                                GuiLoreLine.Action(lang.getMessage(player, "gui.settings.click.left"), lang.getMessage(player, "gui.admin_portals.portal_item.action.teleport")),
                                GuiLoreLine.Action(lang.getMessage(player, "gui.settings.click.right"), lang.getMessage(player, "gui.admin_portals.portal_item.action.remove"))
                        ))
                ))))
                item.itemMeta = meta
                return item
        }

        private fun renderRuntimeRoute(player: Player, route: MenuRoute): InventoryMenuView {
                val screen = runtimeScreen(route)
                        ?: error("ワールド設定Runtimeの画面種別がありません")
                val worldUuid = runtimeWorldUuid(route)
                        ?: error("ワールド設定RuntimeのワールドUUIDがありません")
                val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
                        ?: error("ワールド設定Runtimeの対象ワールドが見つかりません: $worldUuid")

                runtimeRenderCapture.remove()
                runtimeRenderCaptureActive.set(true)
                try {
                        when (screen) {
                                WorldSettingsRuntimeScreen.WORLD_SETTINGS,
                                WorldSettingsRuntimeScreen.ICON_SELECTION ->
                                        runtimeRenderCapture.set(renderWorldSettings(player, worldData))
                                WorldSettingsRuntimeScreen.MEMBER_MANAGEMENT ->
                                        runtimeRenderCapture.set(
                                                renderMemberManagement(
                                                        player,
                                                        worldData,
                                                        runtimePage(route) ?: 0,
                                                ),
                                        )
                                WorldSettingsRuntimeScreen.MEMBER_PENDING_INVITE_CANCEL_CONFIRM ->
                                        openMemberPendingInviteCancelConfirmation(
                                                player,
                                                worldData,
                                                requireNotNull(runtimeTargetUuid(route)),
                                                requireNotNull(runtimeDecisionId(route)),
                                        )
                                WorldSettingsRuntimeScreen.MEMBER_REMOVE_CONFIRM ->
                                        openMemberRemoveConfirmation(
                                                player,
                                                worldData,
                                                requireNotNull(runtimeTargetUuid(route)),
                                        )
                                WorldSettingsRuntimeScreen.MEMBER_TRANSFER_CONFIRM ->
                                        openMemberTransferConfirmation(
                                                player,
                                                worldData,
                                                requireNotNull(runtimeTargetUuid(route)),
                                        )
                                WorldSettingsRuntimeScreen.VISITOR_MANAGEMENT ->
                                        openVisitorManagement(
                                                player,
                                                worldData,
                                                runtimePage(route) ?: 0,
                                        )
                                WorldSettingsRuntimeScreen.VISITOR_KICK_CONFIRM ->
                                        openVisitorKickConfirmation(
                                                player,
                                                worldData,
                                                requireNotNull(runtimeTargetUuid(route)),
                                        )
                                WorldSettingsRuntimeScreen.EXPANSION_METHOD_SELECTION ->
                                        runtimeRenderCapture.set(
                                                renderExpansionMethodSelection(player, worldData),
                                        )
                                WorldSettingsRuntimeScreen.EXPANSION_CONFIRM ->
                                        runtimeRenderCapture.set(
                                                renderExpansionConfirmation(
                                                        player,
                                                route.payload[ROUTE_EXPANSION_DIRECTION]
                                                        ?.takeUnless { it == ROUTE_NULL_VALUE }
                                                        ?.let(org.bukkit.block.BlockFace::valueOf),
                                                route.payload[ROUTE_EXPANSION_COST]?.toIntOrNull()
                                                        ?: error("ワールド拡張コストがありません"),
                                                ),
                                        )
                                WorldSettingsRuntimeScreen.EXPANSION_STEP_BACK_CONFIRM ->
                                        runtimeRenderCapture.set(
                                                renderExpansionStepBackConfirmation(player, worldData),
                                        )
                                WorldSettingsRuntimeScreen.CRITICAL_SETTINGS ->
                                        openCriticalSettings(player, worldData)
                                WorldSettingsRuntimeScreen.RESET_EXPANSION_CONFIRM ->
                                        openResetExpansionConfirmation(player, worldData)
                                WorldSettingsRuntimeScreen.RESET_EXPANSION_SPAWN_UNSAFE_CONFIRM ->
                                        openResetExpansionSpawnUnsafeConfirmation(player, worldData)
                                WorldSettingsRuntimeScreen.DELETE_WORLD_CONFIRM ->
                                        openDeleteWorldConfirmation1(player, worldData)
                                WorldSettingsRuntimeScreen.DELETE_WORLD_FINAL_CONFIRM ->
                                        openDeleteWorldConfirmation2(player, worldData)
                                WorldSettingsRuntimeScreen.ARCHIVE_CONFIRM ->
                                        runtimeRenderCapture.set(renderArchiveConfirmation(player, worldData))
                                WorldSettingsRuntimeScreen.ARCHIVE_FROM_CRITICAL_CONFIRM ->
                                        runtimeRenderCapture.set(renderArchiveConfirmation(player, worldData))
                                WorldSettingsRuntimeScreen.UNARCHIVE_CONFIRM ->
                                        runtimeRenderCapture.set(renderUnarchiveConfirmation(player, worldData))
                                WorldSettingsRuntimeScreen.PORTAL_MANAGEMENT ->
                                        openPortalManagement(
                                                player,
                                                worldData,
                                                runtimePage(route) ?: 0,
                                        )
                        }
                        return runtimeRenderCapture.get()
                                ?: error("ワールド設定Runtimeの再生成結果がありません: $screen")
                } finally {
                        runtimeRenderCapture.remove()
                        runtimeRenderCaptureActive.remove()
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

        private fun presentRuntime(
                player: Player,
                title: Component,
                inventory: RuntimeItemBuffer,
                screen: WorldSettingsRuntimeScreen,
                worldUuid: UUID,
                replaceCurrent: Boolean = false,
                targetUuid: UUID? = null,
                decisionId: UUID? = null,
                routeArguments: Map<String, String> = emptyMap(),
        ) {
                val view = InventoryMenuView(
                        size = inventory.size,
                        title = title,
                        elements = inventory.elements(),
                        standardFrame = false,
                        playerInventoryInteraction = PlayerInventoryInteraction.INTERACTIVE,
                )
                if (runtimeRenderCaptureActive.get()) {
                        runtimeRenderCapture.set(view)
                        return
                }
                val payload = mutableMapOf(
                        ROUTE_SCREEN to screen.name,
                        ROUTE_WORLD_UUID to worldUuid.toString(),
                )
                targetUuid?.let { payload[ROUTE_TARGET_UUID] = it.toString() }
                decisionId?.let { payload[ROUTE_DECISION_ID] = it.toString() }
                payload.putAll(routeArguments)
                val route = MenuRoute(
                        RUNTIME_OWNER,
                        RUNTIME_ROUTE,
                        payload,
                )
                if (replaceCurrent) {
                        runtime.replace(player, route)
                } else {
                        runtime.navigate(player, route)
                }
        }

        private fun presentRuntime(
                player: Player,
                title: String,
                inventory: RuntimeItemBuffer,
                screen: WorldSettingsRuntimeScreen,
                worldUuid: UUID,
                replaceCurrent: Boolean = false,
                targetUuid: UUID? = null,
                decisionId: UUID? = null,
                routeArguments: Map<String, String> = emptyMap(),
        ) = presentRuntime(
                player,
                GuiHelper.inventoryTitle(title),
                inventory,
                screen,
                worldUuid,
                replaceCurrent,
                targetUuid,
                decisionId,
                routeArguments,
        )

        private fun createDecorationItem(material: Material): ItemStack {
                return GuiItemFactory.decoration(material)
        }

        fun editWorldInfo(player: Player, worldData: WorldData) {
                plugin.worldSettingsListener.editWorldInfo(player, worldData)
        }

        fun enterIconSelection(player: Player, worldData: WorldData) {
                plugin.worldSettingsListener.startIconSelection(player, worldData)
        }

        fun handleIconSelection(player: Player, clickedItem: ItemStack): MenuActionResult {
                return plugin.worldSettingsListener.handleRuntimeIconSelection(player, clickedItem)
        }

        fun handleManagedMenuClose(player: Player, reason: MenuCloseReason) {
                plugin.worldSettingsListener.onRuntimeInventoryClose(player, reason)
        }

        fun enterSpawnSetting(player: Player, worldData: WorldData, isGuest: Boolean) {
                val action = if (isGuest) SettingsAction.SET_SPAWN_GUEST else SettingsAction.SET_SPAWN_MEMBER
                plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, action, isGui = true)
                runtime.suspendForExternal(player)

                plugin.worldSettingsListener.startSpawnPreview(player)

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
                const val ACTION_CAPABILITY = "capability"
                private const val CAPABILITY_ID_PAYLOAD = "capability"
                private const val WORLD_UUID_ARGUMENT = "world_uuid"
                private val WORLD_SETTINGS_CAPABILITY_SLOTS = listOf(51)
                private const val ROUTE_SCREEN = "screen"
                const val ROUTE_WORLD_UUID = "world_uuid"
                const val ROUTE_PAGE = "page"
                const val ROUTE_TARGET_UUID = "target_uuid"
                const val ROUTE_DECISION_ID = "decision_id"
                const val ROUTE_CAPABILITY_ID = "capability_id"
                private const val ROUTE_OPERATION = "operation"
                private const val ROUTE_EXPANSION_COST = "expansion_cost"
                private const val ROUTE_EXPANSION_DIRECTION = "expansion_direction"
                private const val ROUTE_NULL_VALUE = "none"
        }
}
