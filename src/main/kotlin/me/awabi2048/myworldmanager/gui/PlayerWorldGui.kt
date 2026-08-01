package me.awabi2048.myworldmanager.gui

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

                // 変更があれば保存＆ログ出力
                if (beforeCount != afterCount) {
                        plugin.playerStatsRepository.save(stats)
                        plugin.logger.info("[PlayerWorldGui] ${player.name} の worldDisplayOrder から削除されたワールド ${beforeCount - afterCount} 件を削除しました。")
                }

                repository.loadAll()
                val playerWorlds = getPlayerWorlds(targetPlayerUuid)

                // worldDisplayOrder に含まれていないワールドを自動追加
                val currentUuids = playerWorlds.map { it.uuid }
                val missingUuids = currentUuids.filter { !stats.worldDisplayOrder.contains(it) }
                if (missingUuids.isNotEmpty()) {
                        stats.worldDisplayOrder.addAll(missingUuids)
                        plugin.playerStatsRepository.save(stats)
                        plugin.logger.info("[PlayerWorldGui] ${player.name} の worldDisplayOrder に新規ワールド ${missingUuids.size} 件を追加しました。")
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
                val createCount = worlds.count { it.owner == targetUuid }
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
                                        slot = layout.actionSlot,
                                        capability = summaryCapability.requireExplicitActionSafety(),
                                        attributes = summaryAttributes,
                                ),
                        )
                } else {
                        createStatsEntry(player, layout.actionSlot, targetUuid, targetName, createCount, maxSlot, stats)
                }
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
                        elements += navigationEntry(player, layout.nextPageSlot, true, pageLayout.page + 1)
                }
                return InventoryMenuView(
                        layout.size,
                        GuiHelper.inventoryTitle(
                                plugin.languageManager.getMessage(player, "gui.player_world.title"),
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
                val stats = plugin.playerStatsRepository.findByUuid(context.player.uniqueId)
                stats.worldDisplayOrder.remove(worldData.uuid)
                stats.worldDisplayOrder.add(0, worldData.uuid)
                plugin.playerStatsRepository.save(stats)
                context.player.sendMessage("§a「${worldData.name}」を一番上に移動しました。")
                return MenuActionResult.Success(MenuUpdate.Refresh)
        }

        private fun unarchive(player: Player, worldData: WorldData): MenuActionResult {
                if (worldData.owner != player.uniqueId) {
                        player.sendMessage(
                                plugin.languageManager.getMessage(player, "messages.archive_access_denied"),
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
                        player.sendMessage(plugin.languageManager.getMessage(player, "messages.world_loading"))
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
                val ownerName = PlayerNameUtil.getNameOrDefault(world.owner, lang.getMessage(player, "general.unknown"))
                val publishLevelName = lang.getMessage(player, "publish_level.${world.publishLevel.name.lowercase()}")
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
                        lang.getMessage(player, "gui.player_world.world_item.expires_value", mapOf("days" to daysRemaining, "date" to displayFormatter.format(expireDate)))
                } else null
                val isArchived = world.isArchived
                val warpAction = lang.getMessage(player, "gui.player_world.world_item.warp")
                val settingsAction = lang.getMessage(player, "gui.player_world.world_item.settings")
                val isCurrentWorld = isCurrentWorld(player, world)
                val payload = mapOf(WORLD_UUID to world.uuid.toString())
                val ownMenu = playerUuid == player.uniqueId
                val canOpenSettings = canOpenWorldSettings(player, world)
                val actions = buildList {
                        if (ownMenu) {
                                add(menuGestureAction(
                                        ACTION_WORLD,
                                        MenuGesture.SHIFT_LEFT,
                                        lang.getMessage(player, "gui.player_world.world_item.move_to_top"),
                                        payload,
                                        safety = MenuActionSafety.REVERSIBLE,
                                ))
                        }
                        when {
                                isArchived -> add(menuGestureAction(
                                        ACTION_WORLD,
                                        MenuGesture.PLAIN_LEFT,
                                        lang.getMessage(player, "gui.unarchive_confirm.action"),
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
                                name = GuiNameSpec.Component(
                                        lang.getComponent(player, "gui.common.world_item_name", mapOf("world" to world.name)),
                                ),
                                role = if (actions.isEmpty()) GuiElementRole.CONTENT else GuiElementRole.ACTION,
                                description = listOfNotNull(world.description.takeIf(String::isNotBlank)),
                                data = buildList {
                                        add(GuiMenuEntryData(lang.getMessage(player, "gui.common.world_item.owner"), ownerName))
                                        add(GuiMenuEntryData(
                                                lang.getMessage(player, "gui.common.world_item.publish"),
                                                publishLevelName,
                                                when (world.publishLevel.name) {
                                                        "PUBLIC" -> GuiValueTone.SUCCESS
                                                        "FRIEND" -> GuiValueTone.PRIMARY
                                                        else -> GuiValueTone.DANGER
                                                },
                                        ))
                                        add(GuiMenuEntryData(lang.getMessage(player, "gui.common.world_item.favorite"), favorites, GuiValueTone.DANGER))
                                        add(GuiMenuEntryData(
                                                lang.getMessage(player, "gui.common.world_item.recent_visitors"),
                                                lang.getMessage(player, "gui.common.world_item.recent_visitors_value", mapOf("count" to visitors)),
                                                GuiValueTone.SUCCESS,
                                        ))
                                        tagNames?.let { add(GuiMenuEntryData(lang.getMessage(player, "gui.common.world_item.tags"), it, GuiValueTone.PRIMARY)) }
                                        expiresAtValue?.let {
                                                add(GuiMenuEntryData(lang.getMessage(player, "gui.player_world.world_item.expires_at"), it))
                                        }
                                },
                                warnings = if (isArchived) {
                                        listOf(lang.getMessage(player, "gui.player_world.world_item.expired"))
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
                                name = GuiNameSpec.Text(lang.getMessage(player, "gui.user_settings.button.display"), GuiNameStyle.PRIMARY),
                                role = GuiElementRole.ACTION,
                                actions = listOf(
                                        menuGestureAction(
                                                ACTION_SETTINGS,
                                                MenuGesture.ANY,
                                                lang.getMessage(player, "gui.user_settings.button.action"),
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
                                name = GuiNameSpec.Component(lang.getComponent(player, "gui.player_world.creation_button.display")),
                                role = GuiElementRole.ACTION,
                                description = lang.getMessageList(player, "gui.player_world.creation_button.description"),
                                actions = listOf(
                                        menuGestureAction(
                                                ACTION_CREATE,
                                                MenuGesture.ANY,
                                                lang.getMessage(player, "gui.player_world.creation_button.action"),
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
        ): MenuElement = CCSystem.getAPI().getGuiElementService().menuDisplay(
                GuiMenuDisplaySpec(
                        slot,
                        GuiItemSpec(
                                Material.BARRIER,
                                GuiNameSpec.Component(plugin.languageManager.getComponent(player, reason.displayKey)),
                                GuiLoreSpec.Blocks(
                                        listOf(
                                                GuiLoreBlock(
                                                        plugin.languageManager.getMessageList(player, reason.loreKey)
                                                                .map(GuiLoreLine::Text),
                                                ),
                                        ),
                                ),
                                GuiElementRole.CONTENT,
                                1,
                        ),
                ),
        )

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
                                                                lang.getMessage(player, "gui.player_world.stats_button.points_label"),
                                                                "$pointIcon${stats.worldPoint}",
                                                                "§6",
                                                        ),
                                                        GuiLoreLine.Text(lang.getMessage(player, "gui.player_world.stats_button.points_description")),
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
                                                                        lang.getMessage(player, "gui.player_world.stats_button.world_count_label"),
                                                                        currentCreateCount,
                                                                        "§a§l",
                                                                ),
                                                                GuiLoreLine.Text(lang.getMessage(player, "gui.player_world.stats_button.slots_bypass_description")),
                                                        )
                                                } else {
                                                        listOf(
                                                                GuiLoreLine.Data(
                                                                        lang.getMessage(player, "gui.player_world.stats_button.slots_label"),
                                                                        "$currentCreateCount/$maxSlot",
                                                                        "§a§l",
                                                                ),
                                                                GuiLoreLine.Text(lang.getMessage(player, "gui.player_world.stats_button.slots_description")),
                                                        )
                                                },
                                        ),
                                )
                        } else {
                                add(
                                        GuiLoreBlock(
                                                listOf(
                                                        GuiLoreLine.Data(
                                                                lang.getMessage(player, "gui.player_world.stats_button.world_count_label"),
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
                                        GuiNameSpec.Component(
                                                lang.getComponent(
                                                        player,
                                                        "gui.player_world.stats_button.display",
                                                        mapOf(
                                                                "player" to (
                                                                        targetPlayerName
                                                                                ?: PlayerNameUtil.getNameOrDefault(
                                                                                        targetPlayerUuid,
                                                                                        lang.getMessage(player, "general.unknown"),
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
                val key = if (next) "gui.common.next_page" else "gui.common.prev_page"
                val iconId = if (next) "next_page" else "prev_page"
                return CCSystem.getAPI().getGuiElementService().menuEntry(
                        player,
                        GuiMenuEntrySpec(
                                slot = slot,
                                material = plugin.menuConfigManager.getIconMaterial("player_world", iconId, Material.ARROW),
                                name = GuiNameSpec.Component(plugin.languageManager.getComponent(player, key)),
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
                        ?: lang.getMessage(player, "gui.player_world.pending_button.none")
                return CCSystem.getAPI().getGuiElementService().menuEntry(
                        player,
                        GuiMenuEntrySpec(
                                slot = slot,
                                material = Material.WRITABLE_BOOK,
                                name = GuiNameSpec.Component(lang.getComponent(player, "gui.player_world.pending_button.display")),
                                role = GuiElementRole.ACTION,
                                description = lang.getMessageList(player, "gui.player_world.pending_button.description"),
                                data = listOf(
                                        GuiMenuEntryData(
                                                lang.getMessage(player, "gui.player_world.pending_button.count_label"),
                                                pendingCount,
                                                if (pendingCount > 0) GuiValueTone.PRIMARY else GuiValueTone.MUTED,
                                        ),
                                        GuiMenuEntryData(
                                                lang.getMessage(player, "gui.player_world.pending_button.latest_label"),
                                                latestPendingText,
                                                GuiValueTone.INFO,
                                        ),
                                ),
                                actions = listOf(
                                        menuGestureAction(
                                                ACTION_PENDING,
                                                MenuGesture.ANY,
                                                lang.getMessage(player, "gui.player_world.pending_button.action"),
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
