package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiAdminKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiBedrockKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiCommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiSettingsKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldPublishLevelKeys
import com.awabi2048.ccsystem.api.localization.LocalizationKey

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
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.extension.PlayerWorldCapabilityContract
import me.awabi2048.myworldmanager.api.extension.PlayerWorldCapabilitySubject
import me.awabi2048.myworldmanager.api.extension.WorldSettingsNavigationRequest
import me.awabi2048.myworldmanager.model.PlayerStats
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PermissionManager
import me.awabi2048.myworldmanager.util.WorldCreationChecks
import me.awabi2048.myworldmanager.util.WorldRuntimePolicies
import org.bukkit.Bukkit
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiInteractionGuidance
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import com.awabi2048.ccsystem.api.gui.GuiMenuActionIntent
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiMenuDisplaySpec
import com.awabi2048.ccsystem.api.gui.GuiMenuCapabilityInvocationSpec
import com.awabi2048.ccsystem.api.gui.GuiValueTone
import com.awabi2048.ccsystem.api.gui.menuUnavailable

class PlayerWorldGui(private val plugin: MyWorldManager) {

        private val repository = plugin.worldConfigRepository
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
                                        ACTION_CREATE to MenuActionHandler(::create),
                                        ACTION_SETTINGS to MenuActionHandler(::userSettings),
                                        ACTION_PENDING to MenuActionHandler(::pending),
                                        ACTION_WORLD to MenuActionHandler(::world),
                                ),
                        ),
                )
        }

        fun getPlayerWorlds(player: Player): List<WorldData> {
                return getPlayerWorlds(player.uniqueId)
        }

        fun getPlayerWorlds(targetPlayerUuid: UUID): List<WorldData> {
                val stats = plugin.playerStatsRepository.findByUuid(targetPlayerUuid)
                val allWorlds = repository.findAll()

                // プレイヤーがアクセス可能なワールドをフィルタリング
                val accessibleWorlds = allWorlds
                        .filter {
                                it.owner == targetPlayerUuid ||
                                        it.moderators.contains(targetPlayerUuid) ||
                                        it.members.contains(targetPlayerUuid) ||
                                        it.isArchived // アーカイブ済みも自分のなら表示
                        }
                        .filter {
                                it.owner == targetPlayerUuid || !it.isArchived
                        } // メンバーとして参加しているアーカイブ済みは非表示

                // worldDisplayOrder に含まれるワールド（順序リスト順）
                val orderedWorlds = stats.worldDisplayOrder
                        .mapNotNull { uuid -> accessibleWorlds.find { it.uuid == uuid } }

                // worldDisplayOrder に含まれていないワールド（作成日時降順）
                val unorderedWorlds = accessibleWorlds
                        .filter { !stats.worldDisplayOrder.contains(it.uuid) }
                        .sortedWith(compareBy<WorldData> { it.isArchived }.thenByDescending { it.createdAt })

                // 完全な順序リスト（orderedWorlds + unorderedWorlds）
                return orderedWorlds + unorderedWorlds
        }

        fun open(
                player: Player,
                page: Int = 0,
                showBackButton: Boolean? = null,
                targetPlayerUuid: UUID = player.uniqueId,
                targetPlayerName: String? = player.name
        ) {
                val session = plugin.playerWorldSessionManager.getSession(player.uniqueId)
                if (showBackButton != null) {
                        session.showBackButton = showBackButton
                }

                // worldDisplayOrder のバリデーション
                val isOwnMenu = targetPlayerUuid == player.uniqueId
                val stats = plugin.playerStatsRepository.findByUuid(targetPlayerUuid)
                val beforeCount = stats.worldDisplayOrder.size
                stats.worldDisplayOrder.removeAll { uuid ->
                        plugin.worldConfigRepository.findByUuid(uuid) == null
                }
                val afterCount = stats.worldDisplayOrder.size

                // 変更があれば保存＆ログ出力。移行待ちの場合は保存を諦めて表示を優先する。
                if (beforeCount != afterCount) {
                        try {
                                plugin.playerStatsRepository.save(stats)
                                plugin.logger.info("[PlayerWorldGui] ${player.name} の worldDisplayOrder から削除されたワールド ${beforeCount - afterCount} 件を削除しました。")
                        } catch (failure: IllegalStateException) {
                                if (me.awabi2048.myworldmanager.util.MigrationFeedback.isMigrationRequired(failure)) {
                                        plugin.logger.warning("[PlayerWorldGui] ${player.name} の worldDisplayOrder 補正をスキップしました（移行待ち）: ${failure.message}")
                                        // メモリ上の変更は 유지하되、保存は見送る
                                } else {
                                        throw failure
                                }
                        }
                }

                repository.loadAll()
                val playerWorlds = getPlayerWorlds(targetPlayerUuid)

                // worldDisplayOrder に含まれていないワールドを自動追加
                val currentUuids = playerWorlds.map { it.uuid }
                val missingUuids = currentUuids.filter { !stats.worldDisplayOrder.contains(it) }
                if (missingUuids.isNotEmpty()) {
                        stats.worldDisplayOrder.addAll(missingUuids)
                        try {
                                plugin.playerStatsRepository.save(stats)
                                plugin.logger.info("[PlayerWorldGui] ${player.name} の worldDisplayOrder に新規ワールド ${missingUuids.size} 件を追加しました。")
                        } catch (failure: IllegalStateException) {
                                if (me.awabi2048.myworldmanager.util.MigrationFeedback.isMigrationRequired(failure)) {
                                        plugin.logger.warning("[PlayerWorldGui] ${player.name} の worldDisplayOrder 補正をスキップしました（移行待ち）: ${failure.message}")
                                } else {
                                        throw failure
                                }
                        }
                }

                // 現在のページ番号を保存
                session.currentPage = page

                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        player.uniqueId,
                        me.awabi2048.myworldmanager.session.SettingsAction.PLAYER_WORLD_GUI,
                        isGui = true
                )
                runtime.navigate(
                        player,
                        route(
                                page,
                                targetPlayerUuid,
                                targetPlayerName,
                                session.showBackButton,
                        ),
                )
        }

        private fun render(player: Player, route: MenuRoute): InventoryMenuView {
                val targetUuid = targetUuid(route)
                val targetName = route.payload[TARGET_NAME]
                val worlds = getPlayerWorlds(targetUuid)
                val pageLayout = CCSystem.getAPI().getGuiLayoutService().sevenColumnPage(
                        worlds.size,
                        route.payload[PAGE]?.toIntOrNull() ?: 0,
                )
                val session = plugin.playerWorldSessionManager.getSession(player.uniqueId)
                session.currentPage = pageLayout.page
                val layout = pageLayout.layout
                val stats = plugin.playerStatsRepository.findByUuid(targetUuid)
                val isOwnMenu = targetUuid == player.uniqueId
                val capabilitySubject =
                        PlayerWorldCapabilitySubject(
                                player,
                                targetUuid,
                                targetName,
                                worlds,
                                showBackButton(route),
                                route,
                        )
                val capabilityService = CCSystem.getAPI().getMenuCapabilityService()
                val elements = mutableListOf<MenuElement>()
                worlds.drop(pageLayout.startIndex).take(pageLayout.itemCount).forEachIndexed { index, world ->
                        val attributes = playerWorldCapabilityAttributes(capabilitySubject, world)
                        val capability = capabilityService
                                .definitions(PlayerWorldCapabilityContract.WORLD_ITEM_PLACEMENT)
                                .firstNotNullOfOrNull { definition ->
                                        capabilityService.resolve(
                                                definition.capabilityId,
                                                player,
                                                attributes = attributes,
                                        )
                                }
                        elements += capability?.let {
                                CCSystem.getAPI().getGuiElementService().menuCapabilityEntry(
                                        player,
                                        GuiMenuCapabilityInvocationSpec(
                                                slot = layout.itemSlots[index],
                                                capability = it.requireExplicitActionSafety(),
                                                attributes = attributes,
                                        ),
                                )
                        } ?: createWorldEntry(player, world, targetUuid, layout.itemSlots[index])
                }
                val createCount = plugin.worldConfigRepository.ownerCountIncludingQuarantine(targetUuid)
                val maxSlot = WorldRuntimePolicies.maxCreateCountDefault(plugin.config) + stats.unlockedWorldSlot
                if (isOwnMenu) {
                        val creationAttributes = playerWorldCapabilityAttributes(capabilitySubject)
                        val capabilityView = capabilityService
                                .definitions(PlayerWorldCapabilityContract.CREATION_PLACEMENT)
                                .firstNotNullOfOrNull { definition ->
                                        capabilityService.resolve(
                                                definition.capabilityId,
                                                player,
                                                attributes = creationAttributes,
                                        )
                                }
                        if (capabilityView != null) {
                                elements += CCSystem.getAPI().getGuiElementService().menuCapabilityEntry(
                                        player,
                                        GuiMenuCapabilityInvocationSpec(
                                                slot = layout.actionSlot - 2,
                                                capability = capabilityView.requireExplicitActionSafety(),
                                                attributes = creationAttributes,
                                        ),
                                )
                        } else {
                                val reason = creationBlockReason(
                                        player,
                                        createCount,
                                        maxSlot,
                                        PermissionManager.canBypassWorldLimits(player),
                                )
                                elements += if (reason == null) {
                                        createCreationEntry(player, layout.actionSlot - 2)
                                } else {
                                        createCreationUnavailableEntry(player, layout.actionSlot - 2, reason)
                                }
                        }
                }
                val summaryAttributes = playerWorldCapabilityAttributes(capabilitySubject)
                val summaryCapability = capabilityService
                        .definitions(PlayerWorldCapabilityContract.SUMMARY_PLACEMENT)
                        .firstNotNullOfOrNull { definition ->
                                capabilityService.resolve(
                                        definition.capabilityId,
                                        player,
                                        attributes = summaryAttributes,
                                )
                        }
                elements += if (summaryCapability != null) {
                        CCSystem.getAPI().getGuiElementService().menuCapabilityEntry(
                                player,
                                GuiMenuCapabilityInvocationSpec(
                                        slot = PlayerWorldCapabilityContract.HEADER_CENTER_SLOT,
                                        capability = summaryCapability.requireExplicitActionSafety(),
                                        attributes = summaryAttributes,
                                ),
                        )
                } else {
                        createStatsEntry(
                                player,
                                PlayerWorldCapabilityContract.HEADER_CENTER_SLOT,
                                targetUuid,
                                targetName,
                                createCount,
                                maxSlot,
                                stats,
                        )
                }
                // フッター中央は閲覧対象ではなく、操作しているプレイヤーの現在地を常に示します。
                elements += plugin.currentWorldMenuElementFactory.create(player, layout.actionSlot)
                if (isOwnMenu) {
                        elements += createUserSettingsEntry(player, layout.actionSlot + 2)
                        elements += createPendingEntry(player, layout.size - 2)
                }
                if (pageLayout.page > 0) {
                        elements += navigationEntry(player, layout.previousPageSlot, false, pageLayout.page - 1)
                }
                if (GuiHelper.canGoBack(player)) {
                        elements += backEntry(player, layout.backSlot)
                }
                if (pageLayout.page < pageLayout.totalPages - 1) {
                        // 旧来の個人設定・保留通知位置を維持するため、次ページだけを右端へ分離します。
                        elements += navigationEntry(player, layout.size - 1, true, pageLayout.page + 1)
                }
                return InventoryMenuView(
                        layout.size,
                        GuiHelper.inventoryTitle(
                                plugin.languageManager.getMessage(player, MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_TITLE),
                        ),
                        elements,
                )
        }

        private fun page(context: MenuActionContext): MenuActionResult {
                val targetPage = context.payload[PAGE]?.toIntOrNull() ?: return MenuActionResult.Rejected()
                return MenuActionResult.Success(
                        MenuUpdate.Replace(
                                route(
                                        targetPage,
                                        targetUuid(context.route),
                                        context.route.payload[TARGET_NAME],
                                        showBackButton(context.route),
                                ),
                        ),
                )
        }

        private fun back(context: MenuActionContext): MenuActionResult {
                return MenuActionResult.Success(MenuUpdate.Back)
        }

        private fun create(context: MenuActionContext): MenuActionResult {
                if (targetUuid(context.route) != context.player.uniqueId) return MenuActionResult.Ignored
                if (!WorldCreationChecks.checkSelfCreatePermission(context.player)) {
                        return MenuActionResult.Rejected()
                }
                val session = plugin.creationSessionManager.startSession(context.player.uniqueId)
                session.isDialogMode = !plugin.playerPlatformResolver.isBedrock(context.player)
                return MenuActionResult.Success(
                        MenuUpdate.Navigate(plugin.creationGui.typeSelectionRoute()),
                )
        }

        private fun userSettings(context: MenuActionContext): MenuActionResult {
                if (targetUuid(context.route) != context.player.uniqueId) return MenuActionResult.Ignored
                val route = plugin.userSettingsGui.prepareOpen(context.player, showBackButton = true)
                        ?: return MenuActionResult.Rejected()
                return MenuActionResult.Success(MenuUpdate.Navigate(route))
        }

        private fun pending(context: MenuActionContext): MenuActionResult {
                if (targetUuid(context.route) != context.player.uniqueId) return MenuActionResult.Ignored
                val session = plugin.playerWorldSessionManager.getSession(context.player.uniqueId)
                return MenuActionResult.Success(
                        MenuUpdate.Navigate(
                                plugin.pendingInteractionGui.prepareOpen(
                                        page = 0,
                                        returnPage = session.currentPage,
                                        showBackButton = session.showBackButton,
                                        fromBedrockMenu = false,
                                ),
                        ),
                )
        }

        private fun world(context: MenuActionContext): MenuActionResult {
                val uuid = context.payload[WORLD_UUID]
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return MenuActionResult.Rejected()
                val worldData = plugin.worldConfigRepository.findByUuid(uuid)
                        ?: return MenuActionResult.Rejected()
                val ownMenu = targetUuid(context.route) == context.player.uniqueId
                return when {
                        ownMenu && context.click.isShiftClick && context.click.isLeftClick ->
                                moveToTop(context, worldData)
                        context.click.isLeftClick && worldData.isArchived ->
                                unarchive(context.player, worldData)
                        context.click.isLeftClick && isCurrentWorld(context.player, worldData) &&
                                canOpenWorldSettings(context.player, worldData) ->
                                openSettings(context.player, worldData)
                        context.click.isLeftClick && !isCurrentWorld(context.player, worldData) ->
                                warp(context.player, worldData)
                        context.click.isRightClick && canOpenWorldSettings(context.player, worldData) ->
                                openSettings(context.player, worldData)
                        else -> MenuActionResult.Ignored
                }
        }

        private fun moveToTop(context: MenuActionContext, worldData: WorldData): MenuActionResult {
                val player = context.player
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                val originalOrder = stats.worldDisplayOrder.toList()
                stats.worldDisplayOrder.remove(worldData.uuid)
                stats.worldDisplayOrder.add(0, worldData.uuid)
                try {
                        plugin.playerStatsRepository.save(stats)
                } catch (failure: IllegalStateException) {
                        stats.worldDisplayOrder.clear()
                        stats.worldDisplayOrder.addAll(originalOrder)
                        me.awabi2048.myworldmanager.util.MigrationFeedback.handleSaveException(
                                player,
                                plugin.languageManager,
                                failure,
                                worldScoped = false,
                        )?.let { return it }
                        throw failure
                } catch (failure: Exception) {
                        stats.worldDisplayOrder.clear()
                        stats.worldDisplayOrder.addAll(originalOrder)
                        throw failure
                }
                player.sendMessage("§a「${worldData.name}」を一番上に移動しました。")
                return MenuActionResult.Success(MenuUpdate.Refresh)
        }

        private fun unarchive(player: Player, worldData: WorldData): MenuActionResult {
                if (worldData.owner != player.uniqueId) {
                        player.sendMessage(
                                plugin.languageManager.getMessage(player, MyworldMessagesKeys.MESSAGES_ARCHIVE_ACCESS_DENIED),
                        )
                        return MenuActionResult.Rejected()
                }
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        me.awabi2048.myworldmanager.session.SettingsAction.UNARCHIVE_CONFIRM,
                        isGui = true,
                )
                return MenuActionResult.Success(
                        MenuUpdate.Navigate(
                                plugin.worldSettingsGui.runtimeRoute(
                                        WorldSettingsRuntimeScreen.UNARCHIVE_CONFIRM,
                                        worldData.uuid,
                                ),
                        ),
                )
        }

        private fun warp(player: Player, worldData: WorldData): MenuActionResult {
                if (Bukkit.getWorld(worldData.customWorldName ?: "my_world.${worldData.uuid}") == null) {
                        player.sendMessage(plugin.languageManager.getMessage(player, MyworldMessagesKeys.MESSAGES_WORLD_LOADING))
                }
                plugin.worldService.teleportToWorld(player, worldData.uuid) {
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        MyworldMessagesKeys.MESSAGES_WARP_SUCCESS,
                                        mapOf("world" to worldData.name),
                                ),
                        )
                }
                return MenuActionResult.Success(MenuUpdate.Close)
        }

        private fun openSettings(player: Player, worldData: WorldData): MenuActionResult {
                val session = plugin.playerWorldSessionManager.getSession(player.uniqueId)
                val route = MyWorldManagerApi.prepareWorldSettingsRoute(
                        player,
                        worldData.uuid,
                        WorldSettingsNavigationRequest(
                                showBackButton = true,
                                isPlayerWorldFlow = true,
                                parentShowBackButton = session.showBackButton,
                        ),
                ) ?: return MenuActionResult.Rejected()
                return MenuActionResult.Success(MenuUpdate.Navigate(route))
        }

        private fun canOpenWorldSettings(player: Player, worldData: WorldData): Boolean =
                player.hasPermission("myworldmanager.admin") ||
                        worldData.owner == player.uniqueId ||
                        player.uniqueId in worldData.moderators ||
                        player.uniqueId in worldData.members

        internal fun route(
                page: Int,
                targetUuid: UUID,
                targetName: String?,
                showBackButton: Boolean,
        ): MenuRoute =
                MenuRoute(
                        OWNER,
                        ROUTE_ID,
                        buildMap {
                                put(PAGE, page.toString())
                                put(TARGET_UUID, targetUuid.toString())
                                put(SHOW_BACK, showBackButton.toString())
                                targetName?.let { put(TARGET_NAME, it) }
                        },
                )

        private fun targetUuid(route: MenuRoute): UUID =
                route.payload[TARGET_UUID]?.let(UUID::fromString)
                        ?: error("プレイヤーワールド一覧の対象UUIDがありません")

        private fun showBackButton(route: MenuRoute): Boolean =
                route.payload[SHOW_BACK]?.toBooleanStrictOrNull() ?: false

        private fun playerWorldCapabilitySubject(
                player: Player,
                route: MenuRoute,
        ): PlayerWorldCapabilitySubject = PlayerWorldCapabilitySubject(
                player,
                targetUuid(route),
                route.payload[TARGET_NAME],
                getPlayerWorlds(targetUuid(route)),
                showBackButton(route),
                route,
        )

        private fun playerWorldCapabilityAttributes(
                subject: PlayerWorldCapabilitySubject,
                worldData: WorldData? = null,
        ): Map<String, Any> = buildMap {
                put(PlayerWorldCapabilityContract.SUBJECT_ATTRIBUTE, subject)
                worldData?.let {
                        put(PlayerWorldCapabilityContract.WORLD_ATTRIBUTE, it)
                }
        }

        private fun createWorldEntry(
                player: Player,
                world: WorldData,
                playerUuid: UUID,
                slot: Int,
        ): MenuElement {
                val lang = plugin.languageManager
                val ownerName = PlayerNameUtil.getNameOrDefault(world.owner, lang.getMessage(player, CommonKeys.GENERAL_UNKNOWN))
                val publishLevelName = lang.getMessage(player, when (world.publishLevel) {
                        PublishLevel.PUBLIC -> MyworldPublishLevelKeys.PUBLISH_LEVEL_PUBLIC
                        PublishLevel.FRIEND -> MyworldPublishLevelKeys.PUBLISH_LEVEL_FRIEND
                        PublishLevel.PRIVATE -> MyworldPublishLevelKeys.PUBLISH_LEVEL_PRIVATE
                        PublishLevel.LOCKED -> MyworldPublishLevelKeys.PUBLISH_LEVEL_LOCKED
                })
                val favorites = world.favorite
                val visitors = world.recentVisitors.sum()
                val tagNames = if (world.tags.isNotEmpty()) {
                        world.tags.joinToString(", ") {
                                plugin.worldTagManager.getDisplayName(player, it)
                        }
                } else null

                val now = LocalDate.now()
                val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val displayFormatter = dateFormatterFor(player)
                val expireDate = try {
                        LocalDate.parse(world.expireDate, inputFormatter)
                } catch (e: Exception) {
                        LocalDate.now().plusYears(1)
                }
                val daysRemaining = ChronoUnit.DAYS.between(now, expireDate)

                val expiresAtValue = if (expireDate.year < 2900) {
                        lang.getMessage(player, MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_WORLD_ITEM_EXPIRES_VALUE, mapOf("days" to daysRemaining, "date" to displayFormatter.format(expireDate)))
                } else null
                val isArchived = world.isArchived
                val warpAction = lang.getMessage(player, MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_WORLD_ITEM_WARP)
                val settingsAction = lang.getMessage(player, MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_WORLD_ITEM_SETTINGS)
                val isCurrentWorld = isCurrentWorld(player, world)
                val payload = mapOf(WORLD_UUID to world.uuid.toString())
                val ownMenu = playerUuid == player.uniqueId
                val canOpenSettings = canOpenWorldSettings(player, world)
                val actions = buildList {
                        if (ownMenu) {
                                add(menuGestureAction(
                                        ACTION_WORLD,
                                        MenuGesture.SHIFT_LEFT,
                                        lang.getMessage(player, MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_WORLD_ITEM_MOVE_TO_TOP),
                                        payload,
                                        safety = MenuActionSafety.REVERSIBLE,
                                        reversibleContract = MwmMenuActionSemantics.contract("player-world-priority"),
                                ))
                        }
                        when {
                                isArchived -> add(menuGestureAction(
                                        ACTION_WORLD,
                                        MenuGesture.PLAIN_LEFT,
                                        lang.getMessage(player, MyworldGuiAdminKeys.GUI_UNARCHIVE_CONFIRM_ACTION),
                                        payload,
                                        safety = MenuActionSafety.CONFIRM_ENTRY,
                                ))
                                isCurrentWorld && canOpenSettings -> add(menuGestureAction(
                                        ACTION_WORLD,
                                        MenuGesture.PLAIN_LEFT_RIGHT,
                                        settingsAction,
                                        payload,
                                        safety = MenuActionSafety.NAVIGATION_ONLY,
                                ))
                                !isCurrentWorld -> {
                                        add(menuGestureAction(ACTION_WORLD, MenuGesture.PLAIN_LEFT, warpAction, payload, safety = MenuActionSafety.EXTERNAL_SIDE_EFFECT))
                                        if (canOpenSettings) {
                                                add(menuGestureAction(ACTION_WORLD, MenuGesture.PLAIN_RIGHT, settingsAction, payload, safety = MenuActionSafety.NAVIGATION_ONLY))
                                        }
                                }
                        }
                }
                return CCSystem.getAPI().getGuiElementService().menuEntry(
                        player,
                        GuiMenuEntrySpec(
                                slot = slot,
                                material = world.icon,
                                name = GuiNameSpec.TargetIdentity(
                                        lang.getComponent(player, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_NAME, mapOf("world" to world.name)),
                                ),
                                role = if (actions.isEmpty()) GuiElementRole.CONTENT else GuiElementRole.ACTION,
                                // Shift操作が同時に存在する場合は共通層が複数操作として扱い、単一時だけ案内を一般化します。
                                interactionGuidance = GuiInteractionGuidance.SINGLE_ACTION_CLICK,
                                description = listOfNotNull(world.description.takeIf(String::isNotBlank)),
                                data = buildList {
                                        add(GuiMenuEntryData(lang.getMessage(player, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_OWNER), ownerName))
                                        add(GuiMenuEntryData(
                                                lang.getMessage(player, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_PUBLISH),
                                                publishLevelName,
                                                when (world.publishLevel.name) {
                                                        "PUBLIC" -> GuiValueTone.SUCCESS
                                                        "FRIEND" -> GuiValueTone.PRIMARY
                                                        else -> GuiValueTone.DANGER
                                                },
                                        ))
                                        add(GuiMenuEntryData(lang.getMessage(player, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_FAVORITE), favorites, GuiValueTone.DANGER))
                                        add(GuiMenuEntryData(
                                                lang.getMessage(player, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_RECENT_VISITORS),
                                                lang.getMessage(player, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_RECENT_VISITORS_VALUE, mapOf("count" to visitors)),
                                                GuiValueTone.SUCCESS,
                                        ))
                                        tagNames?.let { add(GuiMenuEntryData(lang.getMessage(player, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_TAGS), it, GuiValueTone.PRIMARY)) }
                                        expiresAtValue?.let {
                                                add(GuiMenuEntryData(lang.getMessage(player, MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_WORLD_ITEM_EXPIRES_AT), it))
                                        }
                                },
                                warnings = if (isArchived) {
                                        listOf(lang.getMessage(player, MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_WORLD_ITEM_EXPIRED))
                                } else {
                                        emptyList()
                                },
                                actions = actions,
                                glint = isArchived || daysRemaining < 0,
                        ),
                )
        }

        private fun isCurrentWorld(player: Player, world: WorldData): Boolean {
                return plugin.worldConfigRepository.findByWorldName(player.world.name)?.uuid == world.uuid
        }

        private fun createUserSettingsEntry(player: Player, slot: Int): MenuElement {
                val lang = plugin.languageManager
                return CCSystem.getAPI().getGuiElementService().menuEntry(
                        player,
                        GuiMenuEntrySpec(
                                slot = slot,
                                material = Material.WRITABLE_BOOK,
                                name = me.awabi2048.myworldmanager.util.fixedLabelName(lang.getMessage(player, MyworldGuiSettingsKeys.GUI_USER_SETTINGS_BUTTON_DISPLAY), GuiNameStyle.PRIMARY),
                                role = GuiElementRole.ACTION,
                                description = lang.getMessageList(player, MyworldGuiSettingsKeys.GUI_USER_SETTINGS_BUTTON_DESCRIPTION),
                                actions = listOf(
                                        menuGestureAction(
                                                ACTION_SETTINGS,
                                                MenuGesture.ANY,
                                                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_USER_SETTINGS_BUTTON_ACTION),
                                                safety = MenuActionSafety.NAVIGATION_ONLY,
                                        ),
                                ),
                        ),
                )
        }

        private fun createCreationEntry(player: Player, slot: Int): MenuElement {
                val lang = plugin.languageManager
                return CCSystem.getAPI().getGuiElementService().menuEntry(
                        player,
                        GuiMenuEntrySpec(
                                slot = slot,
                                material = Material.NETHER_STAR,
                                name = GuiNameSpec.FixedLabel(lang.getComponent(player, MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_CREATION_BUTTON_DISPLAY)),
                                role = GuiElementRole.ACTION,
                                description = lang.getMessageList(player, MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_CREATION_BUTTON_DESCRIPTION),
                                actions = listOf(
                                        menuGestureAction(
                                                ACTION_CREATE,
                                                MenuGesture.ANY,
                                                lang.getMessage(player, MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_CREATION_BUTTON_ACTION),
                                                safety = MenuActionSafety.NAVIGATION_ONLY,
                                        ),
                                ),
                        ),
                )
        }

        private fun createCreationUnavailableEntry(
                player: Player,
                slot: Int,
                reason: CreationBlockReason,
        ): MenuElement {
                val unavailableReason = plugin.languageManager.getComponent(player, reason.displayKey)
                return CCSystem.getAPI().getGuiElementService().menuUnavailable(
                        player,
                        GuiMenuEntrySpec(
                                slot = slot,
                                material = Material.BARRIER,
                                name = GuiNameSpec.FixedLabel(unavailableReason),
                                role = GuiElementRole.CONTENT,
                                warnings = plugin.languageManager.getMessageList(player, reason.loreKey),
                        ),
                        unavailableReason,
                )
        }

        private fun creationBlockReason(
                player: Player,
                currentCreateCount: Int,
                maxSlot: Int,
                bypassLimits: Boolean
        ): CreationBlockReason? {
                // 作成権限は最優先。bypassLimits や運用フラグよりも先に判定する。
                if (!PermissionManager.checkPermission(player, PermissionManager.WORLD_CREATE)) {
                        return CreationBlockReason.NO_PERMISSION
                }
                if (!WorldCreationChecks.check(player, notify = false)) return CreationBlockReason.POLICY_DENIED
                if (bypassLimits) return null
                if (currentCreateCount >= maxSlot) return CreationBlockReason.NO_SLOT
                return null
        }

        private fun createStatsEntry(
                player: Player,
                slot: Int,
                targetPlayerUuid: UUID,
                targetPlayerName: String?,
                currentCreateCount: Int,
                maxSlot: Int,
                stats: PlayerStats,
        ): MenuElement {
                val lang = plugin.languageManager
                val bypassLimits = PermissionManager.canBypassWorldLimits(player)
                val lore = GuiLoreSpec.Blocks(buildList {
                        if (MyWorldManagerApi.isWorldPointEconomyEnabled()) {
                                val pointIcon = if (plugin.playerPlatformResolver.isBedrock(player)) "" else "🛖 "
                                add(
                                        GuiLoreBlock(
                                                listOf(
                                                        GuiLoreLine.Data(
                                                                lang.getMessage(player, MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_STATS_BUTTON_POINTS_LABEL),
                                                                "$pointIcon${stats.worldPoint}",
                                                                "§6",
                                                        ),
                                                        GuiLoreLine.Text(lang.getMessage(player, MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_STATS_BUTTON_POINTS_DESCRIPTION)),
                                                ),
                                        ),
                                )
                        }
                        if (MyWorldManagerApi.isWorldSlotSystemEnabled()) {
                                add(
                                        GuiLoreBlock(
                                                if (bypassLimits) {
                                                        listOf(
                                                                GuiLoreLine.Data(
                                                                        lang.getMessage(player, MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_STATS_BUTTON_WORLD_COUNT_LABEL),
                                                                        currentCreateCount,
                                                                        "§a§l",
                                                                ),
                                                                GuiLoreLine.Text(lang.getMessage(player, MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_STATS_BUTTON_SLOTS_BYPASS_DESCRIPTION)),
                                                        )
                                                } else {
                                                        listOf(
                                                                GuiLoreLine.Data(
                                                                        lang.getMessage(player, MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_STATS_BUTTON_SLOTS_LABEL),
                                                                        "$currentCreateCount/$maxSlot",
                                                                        "§a§l",
                                                                ),
                                                                GuiLoreLine.Text(lang.getMessage(player, MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_STATS_BUTTON_SLOTS_DESCRIPTION)),
                                                        )
                                                },
                                        ),
                                )
                        } else {
                                add(
                                        GuiLoreBlock(
                                                listOf(
                                                        GuiLoreLine.Data(
                                                                lang.getMessage(player, MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_STATS_BUTTON_WORLD_COUNT_LABEL),
                                                                currentCreateCount,
                                                                "§a§l",
                                                        ),
                                                ),
                                        ),
                                )
                        }
                })
                return CCSystem.getAPI().getGuiElementService().menuDisplay(
                        GuiMenuDisplaySpec(
                                slot,
                                GuiItemSpec(
                                        Material.PLAYER_HEAD,
                                        GuiNameSpec.FixedLabel(
                                                lang.getComponent(
                                                        player,
                                                        MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_STATS_BUTTON_DISPLAY,
                                                        mapOf(
                                                                "player" to (
                                                                        targetPlayerName
                                                                                ?: PlayerNameUtil.getNameOrDefault(
                                                                                        targetPlayerUuid,
                                                                                        lang.getMessage(player, CommonKeys.GENERAL_UNKNOWN),
                                                                                )
                                                                        ),
                                                        ),
                                                ),
                                        ),
                                        lore,
                                        GuiElementRole.CONTENT,
                                        1,
                                ),
                                playerHeadOwner = targetPlayerUuid,
                        ),
                )
        }

        private fun navigationEntry(player: Player, slot: Int, next: Boolean, targetPage: Int): MenuElement {
                val key = if (next) CommonKeys.GUI_COMMON_NEXT_PAGE else CommonKeys.GUI_COMMON_PREV_PAGE
                val iconId = if (next) "next_page" else "prev_page"
                return CCSystem.getAPI().getGuiElementService().menuEntry(
                        player,
                        GuiMenuEntrySpec(
                                slot = slot,
                                material = plugin.menuConfigManager.getIconMaterial("player_world", iconId, Material.ARROW),
                                name = GuiNameSpec.FixedLabel(plugin.languageManager.getComponent(player, key)),
                                role = GuiElementRole.NAVIGATION,
                                actions = listOf(
                                        menuGestureAction(
                                                ACTION_PAGE,
                                                MenuGesture.ANY,
                                                plugin.languageManager.getMessage(player, key),
                                                mapOf(PAGE to targetPage.toString()),
                                                safety = MenuActionSafety.NAVIGATION_ONLY,
                                        ),
                                ),
                        ),
                )
        }

        private fun backEntry(player: Player, slot: Int): MenuElement =
                CCSystem.getAPI().getGuiElementService().backEntry(
                        player,
                        slot,
                        plugin.menuConfigManager.getIconMaterial("world_settings", "back", Material.REDSTONE),
                )

        private fun createPendingEntry(player: Player, slot: Int): MenuElement {
                val lang = plugin.languageManager
                val pendingCount = plugin.pendingDecisionManager.getPendingCount(player.uniqueId)
                val latestPendingText = plugin.pendingDecisionManager
                        .getLatestPendingCreatedAt(player.uniqueId)
                        ?.let {
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                        .withZone(ZoneId.systemDefault())
                                        .format(Instant.ofEpochMilli(it))
                        }
                        ?: lang.getMessage(player, MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_PENDING_BUTTON_NONE)
                return CCSystem.getAPI().getGuiElementService().menuEntry(
                        player,
                        GuiMenuEntrySpec(
                                slot = slot,
                                material = Material.WRITABLE_BOOK,
                                name = GuiNameSpec.FixedLabel(lang.getComponent(player, MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_PENDING_BUTTON_DISPLAY)),
                                role = GuiElementRole.ACTION,
                                description = lang.getMessageList(player, MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_PENDING_BUTTON_DESCRIPTION),
                                data = listOf(
                                        GuiMenuEntryData(
                                                lang.getMessage(player, MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_PENDING_BUTTON_COUNT_LABEL),
                                                pendingCount,
                                                if (pendingCount > 0) GuiValueTone.PRIMARY else GuiValueTone.MUTED,
                                        ),
                                        GuiMenuEntryData(
                                                lang.getMessage(player, MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_PENDING_BUTTON_LATEST_LABEL),
                                                latestPendingText,
                                                GuiValueTone.INFO,
                                        ),
                                ),
                                actions = listOf(
                                        menuGestureAction(
                                                ACTION_PENDING,
                                                MenuGesture.ANY,
                                                lang.getMessage(player, MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_PENDING_BUTTON_ACTION),
                                                safety = MenuActionSafety.NAVIGATION_ONLY,
                                        ),
                                ),
                                glint = pendingCount > 0,
                        ),
                )
        }

        private fun dateFormatterFor(player: Player): DateTimeFormatter {
                val language = plugin.languageManager.resolveLocale(player).lowercase(Locale.ROOT)
                return if (language == "ja_jp") {
                        DateTimeFormatter.ofPattern("yyyy年MM月dd日")
                } else {
                        DateTimeFormatter.ofPattern("yyyy-MM-dd")
                }
        }

        private enum class CreationBlockReason(
                val displayKey: LocalizationKey<String>,
                val loreKey: LocalizationKey<List<String>>,
        ) {
                POLICY_DENIED(
                        MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_CREATION_UNAVAILABLE_POLICY_DENIED_DISPLAY,
                        MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_CREATION_UNAVAILABLE_POLICY_DENIED_LORE,
                ),
                NO_SLOT(
                        MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_CREATION_UNAVAILABLE_NO_SLOT_DISPLAY,
                        MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_CREATION_UNAVAILABLE_NO_SLOT_LORE,
                ),
                NO_PERMISSION(
                        MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_CREATION_UNAVAILABLE_NO_PERMISSION_DISPLAY,
                        MyworldGuiBedrockKeys.GUI_PLAYER_WORLD_CREATION_UNAVAILABLE_NO_PERMISSION_LORE,
                )
        }

        companion object {
                private const val OWNER = "myworldmanager"
                private const val ROUTE_ID = "player-world"
                private const val PAGE = "page"
                private const val TARGET_UUID = "targetUuid"
                private const val TARGET_NAME = "targetName"
                private const val SHOW_BACK = "showBack"
                private const val WORLD_UUID = "worldUuid"
                private const val ACTION_PAGE = "page"
                private const val ACTION_BACK = "back"
                private const val ACTION_CREATE = "create"
                private const val ACTION_SETTINGS = "settings"
                private const val ACTION_PENDING = "pending"
                private const val ACTION_WORLD = "world"
        }
}
