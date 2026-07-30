@file:Suppress("REDUNDANT_CALL_OF_CONVERSION_METHOD", "UNNECESSARY_SAFE_CALL")

package me.awabi2048.myworldmanager.listener


import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiCycle
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuCloseReason
import com.awabi2048.ccsystem.api.gui.MenuDialogButton
import com.awabi2048.ccsystem.api.gui.MenuDialogHandler
import com.awabi2048.ccsystem.api.gui.MenuDialogInput
import com.awabi2048.ccsystem.api.gui.MenuDialogRequest
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import java.util.UUID
import java.util.Locale
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.extension.MemberManagementCapabilityContract
import me.awabi2048.myworldmanager.api.extension.MemberManagementCapabilitySubject
import me.awabi2048.myworldmanager.api.service.ExpansionExecutionMode
import me.awabi2048.myworldmanager.api.service.ExpansionSequenceOptions
import me.awabi2048.myworldmanager.api.service.ExpansionSequencePhase
import me.awabi2048.myworldmanager.api.event.MwmMemberRemoveSource
import me.awabi2048.myworldmanager.api.event.MwmMemberRemovedEvent
import me.awabi2048.myworldmanager.api.event.MwmMemberAddSource
import me.awabi2048.myworldmanager.api.event.MwmMemberAddedEvent
import me.awabi2048.myworldmanager.api.event.MwmOwnerTransferSource
import me.awabi2048.myworldmanager.api.event.MwmOwnerTransferredEvent
import me.awabi2048.myworldmanager.model.BorderExpansionRecord
import me.awabi2048.myworldmanager.model.PendingInteractionType
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.service.BorderResetSpawnService
import me.awabi2048.myworldmanager.session.MenuExternalInput
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.session.SettingsClosePolicy
import me.awabi2048.myworldmanager.util.BiomeResolver
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.LanguageManager
import me.awabi2048.myworldmanager.util.PermissionManager
import me.awabi2048.myworldmanager.util.WorldRuntimePolicies
import me.awabi2048.myworldmanager.util.WorldCreationChecks
import me.awabi2048.myworldmanager.util.WorldNameValidation
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Color
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.block.BlockFace
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import me.awabi2048.myworldmanager.gui.DialogConfirmManager
import me.awabi2048.myworldmanager.gui.WorldSettingsRuntimeContext
import me.awabi2048.myworldmanager.gui.WorldSettingsGui
import me.awabi2048.myworldmanager.gui.WorldSettingsRuntimeOperation
import me.awabi2048.myworldmanager.gui.WorldSettingsRuntimeScreen

class WorldSettingsListener : Listener {

        private val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        private val pendingExpansions = mutableMapOf<UUID, PendingExpansion>()
        private val spawnPreviewTasks = mutableMapOf<UUID, BukkitTask>()
        private val borderDirectionPreviewTasks = mutableMapOf<UUID, BukkitTask>()

        fun handleRuntimeInventoryClick(
                player: Player,
                click: ClickType,
                item: ItemStack,
                slot: Int,
                runtimeContext: WorldSettingsRuntimeContext,
        ): MenuActionResult {
                if (player.openInventory.topInventory.getItem(slot)?.isSimilar(item) != true) {
                        return MenuActionResult.Ignored
                }
                handleIconSelectionTopInventoryClick(runtimeContext)?.let { return it }
                handleConfirmationRuntimeClick(player, item, runtimeContext)?.let { return it }
                handleMemberManagementInviteClick(player, click, item, runtimeContext)?.let { return it }
                handleMemberManagementRouteClick(player, click, item, runtimeContext)?.let { return it }
                handleVisitorManagementRuntimeClick(player, click, item, runtimeContext)?.let { return it }
                handleExpansionMethodSelectionRuntimeClick(player, item, runtimeContext)?.let { return it }
                handlePortalManagementRuntimeClick(player, click, item, runtimeContext)?.let { return it }
                handleCriticalSettingsRuntimeClick(player, item, runtimeContext)?.let { return it }
                handleWorldSettingsRuntimeClick(player, click, item, runtimeContext)?.let { return it }
                return MenuActionResult.Ignored
        }

        /**
         * ワールド設定本体のクリックを、Runtime が確定した画面種別として処理します。
         *
         * 画面を開いた時点の Inventory や SettingsSession.action を画面識別に使わず、
         * [WorldSettingsRuntimeContext.screen] だけを入口の契約にします。外部入力や
         * ワールド内操作を続けるための SettingsSession は、ここではワークフロー状態としてのみ使用します。
         */
        private fun handleWorldSettingsRuntimeClick(
                player: Player,
                click: ClickType,
                item: ItemStack,
                runtimeContext: WorldSettingsRuntimeContext,
        ): MenuActionResult? {
                if (runtimeContext.screen != WorldSettingsRuntimeScreen.WORLD_SETTINGS) return null
                val session = plugin.settingsSessionManager.getSession(player) ?: return MenuActionResult.Ignored
                val worldData = runtimeContext.worldUuid?.let(plugin.worldConfigRepository::findByUuid)
                        ?: plugin.worldConfigRepository.findByUuid(session.worldUuid)
                        ?: return MenuActionResult.Ignored
                val operation = runtimeContext.operation ?: return MenuActionResult.Ignored

                if (operation == WorldSettingsRuntimeOperation.WARP) {
                        if (plugin.worldConfigRepository.findByWorldName(player.world.name)?.uuid == worldData.uuid) {
                                return MenuActionResult.Ignored
                        }
                        plugin.settingsSessionManager.updateSessionAction(
                                player, worldData.uuid, SettingsAction.VIEW_SETTINGS, isGui = true,
                                isPlayerWorldFlow = session.isPlayerWorldFlow,
                                parentShowBackButton = session.parentShowBackButton
                        )
                        CCSystem.getAPI().getMenuRuntimeService().suspendForExternal(player)
                        plugin.worldService.teleportToWorld(player, worldData.uuid, closeInventoryOnLoad = false) {
                                if (!player.isOnline) return@teleportToWorld
                                player.sendMessage(plugin.languageManager.getMessage(
                                        player, "messages.warp_success", mapOf("world" to worldData.name)
                                ))
                                CCSystem.getAPI().getMenuRuntimeService().resumeFromExternal(player)
                        }
                        return MenuActionResult.Success(MenuUpdate.None)
                }

                val restrictedOperations = setOf(
                        WorldSettingsRuntimeOperation.SET_SPAWN,
                        WorldSettingsRuntimeOperation.EXPAND,
                        WorldSettingsRuntimeOperation.OPEN_ENVIRONMENT,
                        WorldSettingsRuntimeOperation.OPEN_CRITICAL,
                )
                if (operation in restrictedOperations) {
                        val targetWorldName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
                        if (player.world.name != targetWorldName) {
                                return MenuActionResult.Rejected()
                        }
                }

                when (operation) {
                        WorldSettingsRuntimeOperation.BACK ->
                                return MenuActionResult.Success(MenuUpdate.Back)
                        WorldSettingsRuntimeOperation.EDIT_INFO -> {
                                if (openBedrockWorldInfoInputForm(player, worldData)) return MenuActionResult.Success(MenuUpdate.None)
                                if (plugin.playerPlatformResolver.isBedrock(player)) {
                                        plugin.floodgateFormBridge.notifyFallbackCancelled(player)
                                        return MenuActionResult.Success(MenuUpdate.Refresh)
                                }
                                plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.RENAME_WORLD)
                                showWorldInfoDialog(player, worldData)
                        }
                        WorldSettingsRuntimeOperation.SET_SPAWN -> {
                                val isGuest = click.isLeftClick
                                val action = if (isGuest) SettingsAction.SET_SPAWN_GUEST else SettingsAction.SET_SPAWN_MEMBER
                                val typeKey = if (isGuest) "gui.settings.spawn.type.guest" else "gui.settings.spawn.type.member"
                                val typeName = plugin.languageManager.getMessage(player, typeKey)
                                player.sendMessage(
                                        Component.text().append(Component.text(plugin.languageManager.getMessage(
                                                player, "messages.spawn_set_start", mapOf("type" to typeName)
                                        ))).append(Component.newline()).append(
                                                Component.text("ﾂｧ7繝ｯ繝ｼ繝ｫ繝芽ｨｭ螳壹Γ繝九Η繝ｼ繧帝幕縺・※繧ｭ繝｣繝ｳ繧ｻ繝ｫ縺励∪縺吶・)")
                                                        .clickEvent(ClickEvent.runCommand("/worldmenu"))
                                                        .hoverEvent(HoverEvent.showText(Component.text("ﾂｧa繧ｯ繝ｪ繝・け縺ｧ髢九￥")))
                                        ).build()
                                )
                                plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, action)
                                CCSystem.getAPI().getMenuRuntimeService().suspendForExternal(player)
                                startSpawnPreview(player)
                        }
                        WorldSettingsRuntimeOperation.SELECT_ICON ->
                                return startIconSelection(player, worldData)
                        WorldSettingsRuntimeOperation.EXPAND -> {
                                if (MyWorldManagerApi.getWorldService()?.isPlayerInWorld(player, worldData) != true) {
                                        player.sendMessage(plugin.languageManager.getMessage(player, "gui.settings.common.must_be_in_world"))
                                        return MenuActionResult.Ignored
                                }
                                if (worldData.borderExpansionLevel == WorldData.EXPANSION_LEVEL_SPECIAL) return MenuActionResult.Ignored
                                if (!plugin.playerPlatformResolver.isBedrock(player) && click.isRightClick) {
                                        if (!teleportToBorderCenterSurface(player, worldData)) {
                                                player.sendMessage(plugin.languageManager.getMessage(player, "error.world_load_failed"))
                                        }
                                        return MenuActionResult.Success(MenuUpdate.None)
                                }
                                val maxLevel = plugin.config.getConfigurationSection("expansion.costs")?.getKeys(false)?.size ?: 3
                                if (worldData.borderExpansionLevel >= maxLevel) {
                                        player.sendMessage(plugin.languageManager.getMessage("error.max_expansion_reached"))
                                        return MenuActionResult.Ignored
                                }
                                return MenuActionResult.Success(
                                        MenuUpdate.Navigate(
                                                plugin.worldSettingsGui.runtimeRoute(
                                                        WorldSettingsRuntimeScreen.EXPANSION_METHOD_SELECTION,
                                                        worldData.uuid,
                                                ),
                                        ),
                                )
                        }
                        WorldSettingsRuntimeOperation.CYCLE_PUBLISH -> {
                                if (MyWorldManagerApi.getWorldPublishPolicy().cyclePublishLevel(player, worldData)) {
                                        return reopenWorldSettingsLatest(player, worldData)
                                }
                                val nextLevel = GuiCycle.select(worldData.publishLevel, PublishLevel.values(), GuiCycle.direction(click)
                                        ?: return MenuActionResult.Ignored)
                                worldData.publishLevel = nextLevel
                                if (nextLevel == PublishLevel.PUBLIC) {
                                        worldData.publicAt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                                .format(java.time.LocalDateTime.now())
                                }
                                plugin.worldConfigRepository.save(worldData)
                                return reopenWorldSettingsLatest(player, worldData)
                        }
                        WorldSettingsRuntimeOperation.MANAGE_MEMBERS ->
                                return MenuActionResult.Success(
                                        MenuUpdate.Navigate(
                                                plugin.worldSettingsGui.memberManagementRoute(worldData.uuid),
                                        ),
                                )
                        WorldSettingsRuntimeOperation.EDIT_TAGS -> {
                                plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.MANAGE_TAGS, isGui = true)
                                showTagEditorDialog(player, worldData)
                        }
                        WorldSettingsRuntimeOperation.MANAGE_VISITORS -> {
                                val world = Bukkit.getWorld(worldData.customWorldName ?: "my_world.${worldData.uuid}")
                                val visitorCount = world?.players?.count {
                                        it.uniqueId != worldData.owner && it.uniqueId !in worldData.moderators && it.uniqueId !in worldData.members
                                } ?: 0
                                if (visitorCount == 0) {
                                        player.sendMessage(plugin.languageManager.getMessage(player, "gui.visitor_management.no_visitors"))
                                        return MenuActionResult.Ignored
                                }
                                return MenuActionResult.Success(
                                        MenuUpdate.Navigate(
                                                plugin.worldSettingsGui.runtimeRoute(
                                                        WorldSettingsRuntimeScreen.VISITOR_MANAGEMENT,
                                                        worldData.uuid,
                                                ),
                                        ),
                                )
                        }
                        WorldSettingsRuntimeOperation.TOGGLE_NOTIFICATION -> {
                                worldData.notificationEnabled = !worldData.notificationEnabled
                                plugin.worldConfigRepository.save(worldData)
                                return reopenWorldSettingsLatest(player, worldData)
                        }
                        WorldSettingsRuntimeOperation.TOUR ->
                                return MenuActionResult.Success(
                                        MenuUpdate.Navigate(plugin.tourGui.editRoute(worldData.uuid)),
                                )
                        WorldSettingsRuntimeOperation.OPEN_CRITICAL ->
                                return MenuActionResult.Success(
                                        MenuUpdate.Navigate(
                                                plugin.worldSettingsGui.runtimeRoute(
                                                        WorldSettingsRuntimeScreen.CRITICAL_SETTINGS,
                                                        worldData.uuid,
                                                ),
                                        ),
                                )
                        WorldSettingsRuntimeOperation.EDIT_ANNOUNCEMENT -> {
                                if (openBedrockAnnouncementActionForm(player, worldData)) return MenuActionResult.Success(MenuUpdate.None)
                                if (plugin.playerPlatformResolver.isBedrock(player)) {
                                        plugin.floodgateFormBridge.notifyFallbackCancelled(player)
                                        return MenuActionResult.Success(MenuUpdate.Refresh)
                                }
                                if (click.isRightClick) {
                                        worldData.announcementMessages.clear()
                                        plugin.worldConfigRepository.save(worldData)
                                        player.sendMessage(plugin.languageManager.getMessage("messages.announcement_reset"))
                                        return reopenWorldSettingsLatest(player, worldData)
                                }
                                plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.SET_ANNOUNCEMENT)
                                me.awabi2048.myworldmanager.gui.AnnouncementDialogManager.showAnnouncementEditDialog(player, worldData)
                        }
                        WorldSettingsRuntimeOperation.MANAGE_PORTALS -> {
                                val isOwner = worldData.owner == player.uniqueId || session.isAdminFlow
                                if (!isOwner) {
                                        player.sendMessage(plugin.languageManager.getMessage(player, "general.no_permission"))
                                        return MenuActionResult.Ignored
                                }
                                if (plugin.portalRepository.findAll().none { it.worldKey == worldData.worldKey }) {
                                        player.sendMessage(plugin.languageManager.getMessage(player, "error.no_portals_found"))
                                        return MenuActionResult.Ignored
                                }
                                return MenuActionResult.Success(
                                        MenuUpdate.Navigate(
                                                plugin.worldSettingsGui.runtimeRoute(
                                                        WorldSettingsRuntimeScreen.PORTAL_MANAGEMENT,
                                                        worldData.uuid,
                                                ),
                                        ),
                                )
                        }
                        WorldSettingsRuntimeOperation.OPEN_ENVIRONMENT -> {
                                if (plugin.playerPlatformResolver.isBedrock(player)) {
                                        player.sendMessage(plugin.languageManager.getMessage(player, "messages.bedrock_option_unavailable"))
                                        return MenuActionResult.Ignored
                                }
                                if (!player.hasPermission("myworldmanager.admin")) return MenuActionResult.Ignored
                                return MenuActionResult.Success(
                                        MenuUpdate.Navigate(plugin.environmentGui.prepareOpen(player, worldData)),
                                )
                        }
                        else -> return MenuActionResult.Ignored
                }
                return MenuActionResult.Success(MenuUpdate.None)
        }

        private fun reopenWorldSettingsLatest(
                @Suppress("UNUSED_PARAMETER") player: Player,
                @Suppress("UNUSED_PARAMETER") worldData: WorldData,
        ): MenuActionResult {
                return MenuActionResult.Success(MenuUpdate.Refresh)
        }
        private fun handleExpansionMethodSelectionRuntimeClick(
                player: Player,
                item: ItemStack,
                runtimeContext: WorldSettingsRuntimeContext,
        ): MenuActionResult? {
                if (runtimeContext.screen != WorldSettingsRuntimeScreen.EXPANSION_METHOD_SELECTION) return null
                val session = plugin.settingsSessionManager.getSession(player) ?: return MenuActionResult.Ignored
                val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: return MenuActionResult.Ignored

                when (runtimeContext.operation) {
                        WorldSettingsRuntimeOperation.EXPAND_AUTOMATIC -> {
                                session.expansionDirection = null
                                val cost = calculateExpansionCost(worldData.borderExpansionLevel)
                                return MenuActionResult.Success(
                                        MenuUpdate.Navigate(
                                                plugin.worldSettingsGui.expansionConfirmationRoute(
                                                        worldData.uuid,
                                                        null,
                                                        cost,
                                                ),
                                        ),
                                )
                        }
                        WorldSettingsRuntimeOperation.EXPAND_DIRECTION -> {
                                startExpansionDirectionSelection(player, session)
                                CCSystem.getAPI().getMenuRuntimeService().suspendForExternal(player)
                                val promptKey =
                                        if (plugin.playerPlatformResolver.isBedrock(player)) {
                                                "messages.expand_direction_prompt"
                                        } else {
                                                "messages.expand_direction_prompt"
                                        }
                                player.sendMessage(
                                        Component.text()
                                                .append(Component.text(plugin.languageManager.getMessage(player, promptKey)))
                                                .append(Component.newline())
                                                .append(
                                                        Component.text("§7ワールド設定メニューを開いてキャンセルします。")
                                                                .clickEvent(ClickEvent.runCommand("/worldmenu"))
                                                                .hoverEvent(HoverEvent.showText(Component.text("§aクリックで開く")))
                                                )
                                                .build()
                                )
                                return MenuActionResult.Success(MenuUpdate.None)
                        }
                        WorldSettingsRuntimeOperation.EXPANSION_STEP_BACK -> {
                                if (worldData.latestBorderExpansionRecord() == null) {
                                        player.sendMessage(plugin.languageManager.getMessage(player, "messages.expansion_step_back_unavailable"))
                                        return MenuActionResult.Rejected()
                                } else {
                                        return MenuActionResult.Success(
                                                MenuUpdate.Navigate(
                                                        plugin.worldSettingsGui.runtimeRoute(
                                                                WorldSettingsRuntimeScreen.EXPANSION_STEP_BACK_CONFIRM,
                                                                worldData.uuid,
                                                        ),
                                                ),
                                        )
                                }
                        }
                        WorldSettingsRuntimeOperation.BACK -> {
                                stopBorderDirectionPreview(player)
                                return MenuActionResult.Success(MenuUpdate.Back)
                        }
                        else -> return MenuActionResult.Ignored
                }
        }

        private fun handleVisitorManagementRuntimeClick(
                player: Player,
                click: ClickType,
                item: ItemStack,
                runtimeContext: WorldSettingsRuntimeContext,
        ): MenuActionResult? {
                if (runtimeContext.screen != WorldSettingsRuntimeScreen.VISITOR_MANAGEMENT) return null
                val worldUuid = runtimeContext.worldUuid
                        ?: plugin.settingsSessionManager.getSession(player)?.worldUuid
                        ?: return MenuActionResult.Ignored
                val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
                        ?: return MenuActionResult.Ignored
                val operation = runtimeContext.operation
                if (operation == WorldSettingsRuntimeOperation.PAGE) {
                        val targetPage = runtimeContext.actionPayload[WorldSettingsGui.ROUTE_PAGE]
                                ?.toIntOrNull()
                                ?: return MenuActionResult.Ignored
                        return MenuActionResult.Success(
                                MenuUpdate.Replace(
                                        plugin.worldSettingsGui.runtimeRoute(
                                                WorldSettingsRuntimeScreen.VISITOR_MANAGEMENT,
                                                worldData.uuid,
                                                page = targetPage,
                                        ),
                                ),
                        )
                }
                if (operation == WorldSettingsRuntimeOperation.VISITOR && (click.isLeftClick || click.isRightClick)) {
                        val visitorUuid = runtimeContext.actionPayload[WorldSettingsGui.ROUTE_TARGET_UUID]
                                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                                ?: return MenuActionResult.Ignored
                        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.VISITOR_KICK_CONFIRM, isGui = true)
                        return MenuActionResult.Success(
                                MenuUpdate.Navigate(
                                        plugin.worldSettingsGui.runtimeRoute(
                                                WorldSettingsRuntimeScreen.VISITOR_KICK_CONFIRM,
                                                worldData.uuid,
                                                targetUuid = visitorUuid,
                                        ),
                                ),
                        )
                } else if (operation == WorldSettingsRuntimeOperation.BACK) {
                        stopBorderDirectionPreview(player)
                        return MenuActionResult.Success(MenuUpdate.Back)
                }
                return MenuActionResult.Ignored
        }

        private fun handleCriticalSettingsRuntimeClick(
                player: Player,
                item: ItemStack,
                runtimeContext: WorldSettingsRuntimeContext,
        ): MenuActionResult? {
                if (runtimeContext.screen != WorldSettingsRuntimeScreen.CRITICAL_SETTINGS) return null
                val worldUuid = runtimeContext.worldUuid
                        ?: plugin.settingsSessionManager.getSession(player)?.worldUuid
                        ?: return MenuActionResult.Ignored
                val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
                        ?: return MenuActionResult.Ignored

                when (runtimeContext.operation) {
                        WorldSettingsRuntimeOperation.BACK -> {
                                stopBorderDirectionPreview(player)
                                return MenuActionResult.Success(MenuUpdate.Back)
                        }
                        WorldSettingsRuntimeOperation.RESET_EXPANSION -> {
                                if (worldData.borderExpansionLevel <= 0) return MenuActionResult.Ignored
                                plugin.settingsSessionManager.updateSessionAction(
                                        player, worldData.uuid, SettingsAction.RESET_EXPANSION_CONFIRM, isGui = true
                                )
                                return MenuActionResult.Success(
                                        MenuUpdate.Navigate(
                                                plugin.worldSettingsGui.runtimeRoute(
                                                        WorldSettingsRuntimeScreen.RESET_EXPANSION_CONFIRM,
                                                        worldData.uuid,
                                                ),
                                        ),
                                )
                        }
                        WorldSettingsRuntimeOperation.ARCHIVE -> {
                                val cooldownHours = plugin.config.getLong("critical_settings.archive_cooldown_hours", 24L)
                                val dtFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                                val isOnCooldown = stats.lastArchiveActionAt?.let { lastAt ->
                                        try {
                                                val lastAction = java.time.LocalDateTime.parse(lastAt, dtFormatter)
                                                java.time.Duration.between(lastAction, java.time.LocalDateTime.now()).toHours() < cooldownHours
                                        } catch (e: Exception) { false }
                                } ?: false
                                if (isOnCooldown) {
                                        val hoursRemaining = stats.lastArchiveActionAt?.let { lastAt ->
                                                try {
                                                        val lastAction = java.time.LocalDateTime.parse(lastAt, dtFormatter)
                                                        val elapsed = java.time.Duration.between(lastAction, java.time.LocalDateTime.now()).toHours()
                                                        (cooldownHours - elapsed).coerceAtLeast(0L)
                                                } catch (e: Exception) { 0L }
                                        } ?: 0L
                                        player.sendMessage(plugin.languageManager.getMessage(
                                                player, "messages.archive_cooldown",
                                                mapOf("cooldown_hours" to cooldownHours, "hours_remaining" to hoursRemaining)
                                        ))
                                        return MenuActionResult.Rejected()
                                }
                                plugin.settingsSessionManager.updateSessionAction(
                                        player, worldData.uuid, SettingsAction.ARCHIVE_WORLD_FROM_CRITICAL, isGui = true
                                )
                                return MenuActionResult.Success(
                                        MenuUpdate.Navigate(
                                                plugin.worldSettingsGui.runtimeRoute(
                                                        WorldSettingsRuntimeScreen.ARCHIVE_FROM_CRITICAL_CONFIRM,
                                                        worldData.uuid,
                                                ),
                                        ),
                                )
                        }
                        WorldSettingsRuntimeOperation.DELETE_WORLD -> {
                                if (!canOwnerExecuteDelete(worldData)) {
                                        sendDeleteUnavailableMessage(player)
                                        return MenuActionResult.Rejected()
                                }
                                plugin.settingsSessionManager.updateSessionAction(
                                        player, worldData.uuid, SettingsAction.DELETE_WORLD_CONFIRM, isGui = true
                                )
                                return MenuActionResult.Success(
                                        MenuUpdate.Navigate(
                                                plugin.worldSettingsGui.runtimeRoute(
                                                        WorldSettingsRuntimeScreen.DELETE_WORLD_CONFIRM,
                                                        worldData.uuid,
                                                ),
                                        ),
                                )
                        }
                        else -> return MenuActionResult.Ignored
                }
        }

        private fun handlePortalManagementRuntimeClick(
                player: Player,
                click: ClickType,
                item: ItemStack,
                runtimeContext: WorldSettingsRuntimeContext,
        ): MenuActionResult? {
                if (runtimeContext.screen != WorldSettingsRuntimeScreen.PORTAL_MANAGEMENT) return null
                val worldUuid = runtimeContext.worldUuid
                        ?: plugin.settingsSessionManager.getSession(player)?.worldUuid
                        ?: return MenuActionResult.Ignored
                val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
                        ?: return MenuActionResult.Ignored
                val lang = plugin.languageManager
                val operation = runtimeContext.operation
                if (operation == WorldSettingsRuntimeOperation.PAGE) {
                        val targetPage = runtimeContext.actionPayload[WorldSettingsGui.ROUTE_PAGE]
                                ?.toIntOrNull()
                                ?: return MenuActionResult.Ignored
                        return MenuActionResult.Success(
                                MenuUpdate.Replace(
                                        plugin.worldSettingsGui.runtimeRoute(
                                                WorldSettingsRuntimeScreen.PORTAL_MANAGEMENT,
                                                worldData.uuid,
                                                page = targetPage,
                                        ),
                                ),
                        )
                }
                if (operation == WorldSettingsRuntimeOperation.PORTAL) {
                        val portalId = runtimeContext.actionPayload[WorldSettingsGui.ROUTE_TARGET_UUID]
                                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                                ?: return MenuActionResult.Ignored
                        val isBedrock = plugin.playerPlatformResolver.isBedrock(player)
                        if (!isBedrock && click.isRightClick) {
                                removePortalFromManagement(player, worldData, portalId, lang)
                                return MenuActionResult.Success(MenuUpdate.Refresh)
                        } else if (click.isLeftClick) {
                                teleportToManagedPortal(player, portalId, lang)
                                return MenuActionResult.Success(MenuUpdate.Close)
                        }
                } else if (operation == WorldSettingsRuntimeOperation.BACK) {
                        stopBorderDirectionPreview(player)
                        return MenuActionResult.Success(MenuUpdate.Back)
                }
                return MenuActionResult.Ignored
        }

        private fun removePortalFromManagement(
                player: Player,
                worldData: WorldData,
                portalId: UUID,
                lang: LanguageManager,
        ) {
                val portal = plugin.portalRepository.findAll().find { it.id == portalId } ?: return
                val refundResult = if (portal.isGate()) plugin.portalManager.refundPointsForRemovedGate(portal) else null
                if (!portal.isGate()) {
                        val block = portal.loadedWorld()?.getBlockAt(portal.x, portal.y, portal.z)
                        if (block != null && block.type == Material.END_PORTAL_FRAME) block.type = Material.AIR
                }
                plugin.portalManager.removePortalVisuals(portal.id)
                plugin.portalRepository.removePortal(portal.id)
                val returnItem = if (portal.isGate()) {
                        me.awabi2048.myworldmanager.util.WorldGateItemUtil.createBaseWorldGateItem(lang, player)
                } else {
                        me.awabi2048.myworldmanager.util.PortalItemUtil.createBasePortalItem(lang, player)
                }
                if (portal.worldUuid != null) {
                        val worldUuid = portal.worldUuid!!
                        val destinationName = plugin.worldConfigRepository.findByUuid(worldUuid)?.name
                                ?: lang.getMessage(player, "general.unknown")
                        if (portal.isGate()) {
                                me.awabi2048.myworldmanager.util.WorldGateItemUtil.bindWorld(returnItem, worldUuid, worldName = destinationName, lang, player)
                        } else {
                                me.awabi2048.myworldmanager.util.PortalItemUtil.bindWorld(returnItem, worldUuid, worldName = destinationName, lang, player)
                        }
                } else if (portal.targetWorldKey != null) {
                        val targetRuntimeName = portal.targetRuntimeName!!
                        val displayName = plugin.config.getString("portal_targets.$targetRuntimeName") ?: targetRuntimeName
                        if (portal.isGate()) {
                                me.awabi2048.myworldmanager.util.WorldGateItemUtil.bindExternalWorld(returnItem, targetRuntimeName, displayName, lang, player)
                        } else {
                                me.awabi2048.myworldmanager.util.PortalItemUtil.bindExternalWorld(returnItem, targetRuntimeName, displayName, lang, player)
                        }
                }
                player.inventory.addItem(returnItem)
                if (portal.isGate()) {
                        val ownerName = Bukkit.getOfflinePlayer(portal.ownerUuid).name ?: portal.ownerUuid.toString()
                        player.sendMessage(lang.getMessage(player, "messages.world_gate_removed_refund", mapOf(
                                "points" to (refundResult?.points ?: 0), "percent" to (refundResult?.percent ?: 0), "owner" to ownerName,
                        )))
                } else {
                        player.sendMessage(lang.getMessage(player, "messages.portal_removed"))
                }
        }

        private fun teleportToManagedPortal(
                player: Player,
                portalId: UUID,
                lang: LanguageManager,
        ) {
                val portal = plugin.portalRepository.findAll().find { it.id == portalId } ?: return
                plugin.portalManager.addIgnorePlayer(player)
                if (!plugin.portalManager.teleportPlayerToPortalLocation(player, portal) {
                                plugin.soundManager.playTeleportSound(player)
                                player.sendMessage(lang.getMessage(player, "messages.warp_generic"))
                        }) {
                        player.sendMessage(lang.getMessage(player, "general.world_not_found"))
                }
        }

        private fun handleIconSelectionTopInventoryClick(
                runtimeContext: WorldSettingsRuntimeContext,
        ): MenuActionResult? {
                if (runtimeContext.screen != WorldSettingsRuntimeScreen.ICON_SELECTION) return null
                return MenuActionResult.Ignored
        }
        private fun handleConfirmationRuntimeClick(
                player: Player,
                item: ItemStack,
                runtimeContext: WorldSettingsRuntimeContext,
        ): MenuActionResult? {
                val worldUuid =
                        runtimeContext.worldUuid
                                ?: plugin.settingsSessionManager.getSession(player)?.worldUuid
                                ?: return null
                val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return null
                return when (runtimeContext.screen) {
                        WorldSettingsRuntimeScreen.MEMBER_REMOVE_CONFIRM ->
                                handleMemberRemoveConfirmationRuntime(
                                        player,
                                        runtimeContext.operation,
                                        worldData,
                                        runtimeContext.targetUuid,
                                )
                        WorldSettingsRuntimeScreen.MEMBER_TRANSFER_CONFIRM ->
                                handleMemberTransferConfirmationRuntime(
                                        player,
                                        runtimeContext.operation,
                                        worldData,
                                        runtimeContext.targetUuid,
                                )
                        WorldSettingsRuntimeScreen.MEMBER_PENDING_INVITE_CANCEL_CONFIRM ->
                                handleMemberPendingInviteCancelConfirmationRuntime(
                                        player,
                                        runtimeContext.operation,
                                        worldData,
                                        runtimeContext.decisionId,
                                )
                        WorldSettingsRuntimeScreen.VISITOR_KICK_CONFIRM ->
                                handleVisitorKickConfirmationRuntime(
                                        player,
                                        runtimeContext.operation,
                                        worldData,
                                        runtimeContext.targetUuid,
                                )
                        WorldSettingsRuntimeScreen.EXPANSION_CONFIRM ->
                                handleExpansionConfirmationRuntime(player, runtimeContext.operation, worldData)
                        WorldSettingsRuntimeScreen.EXPANSION_STEP_BACK_CONFIRM ->
                                handleExpansionStepBackConfirmationRuntime(player, runtimeContext.operation, worldData)
                        WorldSettingsRuntimeScreen.RESET_EXPANSION_CONFIRM ->
                                handleExpansionResetConfirmationRuntime(player, runtimeContext.operation, worldData)
                        WorldSettingsRuntimeScreen.RESET_EXPANSION_SPAWN_UNSAFE_CONFIRM ->
                                handleExpansionResetSpawnUnsafeConfirmationRuntime(player, runtimeContext.operation, worldData)
                        WorldSettingsRuntimeScreen.DELETE_WORLD_CONFIRM ->
                                handleDeleteWorldConfirmationRuntime(player, runtimeContext.operation, worldData)
                        WorldSettingsRuntimeScreen.DELETE_WORLD_FINAL_CONFIRM ->
                                handleDeleteWorldFinalConfirmationRuntime(player, runtimeContext.operation, worldData)
                        WorldSettingsRuntimeScreen.ARCHIVE_CONFIRM ->
                                handleArchiveConfirmationRuntime(player, runtimeContext.operation, worldData)
                        WorldSettingsRuntimeScreen.ARCHIVE_FROM_CRITICAL_CONFIRM ->
                                handleArchiveFromCriticalConfirmationRuntime(player, runtimeContext.operation, worldData)
                        WorldSettingsRuntimeScreen.UNARCHIVE_CONFIRM ->
                                handleUnarchiveConfirmationRuntime(player, runtimeContext.operation, worldData)
                        else -> null
                }
        }
        private fun handleMemberRemoveConfirmationRuntime(
                player: Player,
                operation: WorldSettingsRuntimeOperation?,
                worldData: WorldData,
                memberId: UUID?,
        ): MenuActionResult {
                return when (operation) {
                        WorldSettingsRuntimeOperation.CANCEL ->
                                MenuActionResult.Success(MenuUpdate.Back)
                        WorldSettingsRuntimeOperation.CONFIRM -> {
                                if (memberId != null) {
                                        val memberName = PlayerNameUtil.getNameOrDefault(memberId, "Unknown")
                                        worldData.members.remove(memberId)
                                        worldData.moderators.remove(memberId)
                                        plugin.worldConfigRepository.save(worldData)
                                        Bukkit.getPluginManager().callEvent(MwmMemberRemovedEvent(worldData.uuid, memberId, memberName, player.uniqueId, MwmMemberRemoveSource.MANUAL))
                                        player.sendMessage(plugin.languageManager.getMessage("messages.member_deleted"))
                                        plugin.macroManager.execute("on_member_remove", mapOf("world_uuid" to worldData.uuid.toString(), "member" to memberName))
                                }
                                MenuActionResult.Success(MenuUpdate.Back)
                        }
                        else -> MenuActionResult.Ignored
                }
        }

        private fun handleMemberTransferConfirmationRuntime(
                player: Player,
                operation: WorldSettingsRuntimeOperation?,
                worldData: WorldData,
                newOwnerId: UUID?,
        ): MenuActionResult {
                return when (operation) {
                        WorldSettingsRuntimeOperation.CANCEL ->
                                MenuActionResult.Success(MenuUpdate.Back)
                        WorldSettingsRuntimeOperation.CONFIRM -> {
                                newOwnerId ?: return MenuActionResult.Ignored
                                if (!WorldCreationChecks.checkLimits(plugin, player, newOwnerId)) return MenuActionResult.Rejected()
                                val oldOwnerId = worldData.owner
                                val oldOwnerName = PlayerNameUtil.getNameOrDefault(oldOwnerId, "Unknown")
                                val newOwnerName = PlayerNameUtil.getNameOrDefault(newOwnerId, "Unknown")
                                worldData.owner = newOwnerId
                                if (!worldData.moderators.contains(oldOwnerId)) worldData.moderators.add(oldOwnerId)
                                worldData.moderators.remove(newOwnerId)
                                worldData.members.remove(newOwnerId)
                                plugin.worldConfigRepository.save(worldData)
                                Bukkit.getPluginManager().callEvent(MwmOwnerTransferredEvent(worldData.uuid, oldOwnerId, oldOwnerName, newOwnerId, newOwnerName, player.uniqueId, MwmOwnerTransferSource.MANUAL))
                                player.sendMessage(plugin.languageManager.getMessage(player, "messages.owner_transferred", mapOf("old_owner" to newOwnerName)))
                                plugin.macroManager.execute("on_owner_transfer", mapOf("old_owner" to oldOwnerName, "new_owner" to newOwnerName, "world_uuid" to worldData.uuid.toString()))
                                MenuActionResult.Success(MenuUpdate.Back)
                        }
                        else -> MenuActionResult.Ignored
                }
        }

        private fun handleMemberPendingInviteCancelConfirmationRuntime(
                player: Player,
                operation: WorldSettingsRuntimeOperation?,
                worldData: WorldData,
                decisionId: UUID?,
        ): MenuActionResult {
                return when (operation) {
                        WorldSettingsRuntimeOperation.CANCEL ->
                                MenuActionResult.Success(MenuUpdate.Back)
                        WorldSettingsRuntimeOperation.CONFIRM -> {
                                decisionId ?: return MenuActionResult.Ignored
                                cancelMemberInviteByDecisionId(player, worldData.uuid, decisionId)
                                MenuActionResult.Success(MenuUpdate.Back)
                        }
                        else -> MenuActionResult.Ignored
                }
        }

        private fun handleVisitorKickConfirmationRuntime(
                player: Player,
                operation: WorldSettingsRuntimeOperation?,
                worldData: WorldData,
                visitorUuid: UUID?,
        ): MenuActionResult {
                return when (operation) {
                        WorldSettingsRuntimeOperation.CANCEL ->
                                MenuActionResult.Success(MenuUpdate.Back)
                        WorldSettingsRuntimeOperation.CONFIRM -> {
                                visitorUuid ?: return MenuActionResult.Ignored
                                val visitor = Bukkit.getPlayer(visitorUuid)
                                val worldFolderName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
                                if (visitor != null && visitor.world.name == worldFolderName) {
                                        visitor.teleport(plugin.worldService.getEvacuationLocation())
                                        visitor.sendMessage(plugin.languageManager.getMessage(visitor, "messages.kicked"))
                                        player.sendMessage(plugin.languageManager.getMessage(player, "messages.kicked_success", mapOf("player" to visitor.name)))
                                }
                                MenuActionResult.Success(MenuUpdate.Back)
                        }
                        else -> MenuActionResult.Ignored
                }
        }

        private fun handleExpansionConfirmationRuntime(
                player: Player,
                operation: WorldSettingsRuntimeOperation?,
                worldData: WorldData,
        ): MenuActionResult {
                if (operation == WorldSettingsRuntimeOperation.CANCEL) {
                        return MenuActionResult.Success(MenuUpdate.Back)
                } else if (operation == WorldSettingsRuntimeOperation.CONFIRM) {
                        val cost = calculateExpansionCost(worldData.borderExpansionLevel)
                        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                        if (MyWorldManagerApi.isWorldPointEconomyEnabled() && stats.worldPoint < cost) {
                                player.sendMessage("§cポイントが不足しています。")
                                return MenuActionResult.Rejected()
                        }
                        val messageList = plugin.languageManager.getMessageList(player, "messages.oage_ganbaru_messages")
                        val randomMessage = if (messageList.isNotEmpty()) messageList.random() else plugin.languageManager.getMessage(player, "messages.oage_ganbaru_default")
                        player.sendMessage(randomMessage)
                        player.playSound(player.location, Sound.BLOCK_ANVIL_USE, 0.5f, 0.5f)
                        val task = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                                val pending = pendingExpansions.remove(player.uniqueId) ?: return@Runnable
                                executeExpansionFinal(player, pending.worldData, pending.cost, pending.direction)
                        }, 40L)
                        val direction = plugin.settingsSessionManager.getSession(player)?.expansionDirection
                        pendingExpansions[player.uniqueId] = PendingExpansion(worldData, cost, direction, task)
                        plugin.settingsSessionManager.endSession(player)
                        return MenuActionResult.Success(MenuUpdate.Close)
                }
                return MenuActionResult.Ignored
        }

        private fun handleExpansionStepBackConfirmationRuntime(player: Player, operation: WorldSettingsRuntimeOperation?, worldData: WorldData): MenuActionResult {
                return when (operation) {
                        WorldSettingsRuntimeOperation.CANCEL -> MenuActionResult.Success(MenuUpdate.Back)
                        WorldSettingsRuntimeOperation.CONFIRM -> {
                                executeExpansionStepBack(player, worldData, closeInventory = false)
                                MenuActionResult.Success(MenuUpdate.Close)
                        }
                        else -> MenuActionResult.Ignored
                }
        }

        private fun handleExpansionResetConfirmationRuntime(player: Player, operation: WorldSettingsRuntimeOperation?, worldData: WorldData): MenuActionResult {
                return when (operation) {
                        WorldSettingsRuntimeOperation.CANCEL -> MenuActionResult.Success(MenuUpdate.Back)
                        WorldSettingsRuntimeOperation.CONFIRM -> {
                                val world = resolveWorld(worldData)
                                if (world != null && !isSpawnAreaPlaceable(world.spawnLocation)) {
                                        MenuActionResult.Success(
                                                MenuUpdate.Navigate(
                                                        plugin.worldSettingsGui.runtimeRoute(
                                                                WorldSettingsRuntimeScreen.RESET_EXPANSION_SPAWN_UNSAFE_CONFIRM,
                                                                worldData.uuid,
                                                        ),
                                                ),
                                        )
                                } else {
                                        executeExpansionReset(player, worldData, closeInventory = true)
                                        MenuActionResult.Success(MenuUpdate.Close)
                                }
                        }
                        else -> MenuActionResult.Ignored
                }
        }

        private fun handleExpansionResetSpawnUnsafeConfirmationRuntime(player: Player, operation: WorldSettingsRuntimeOperation?, worldData: WorldData): MenuActionResult {
                return when (operation) {
                        WorldSettingsRuntimeOperation.CANCEL -> MenuActionResult.Success(MenuUpdate.Back)
                        WorldSettingsRuntimeOperation.CONFIRM -> {
                                executeExpansionReset(player, worldData, closeInventory = true)
                                MenuActionResult.Success(MenuUpdate.Close)
                        }
                        else -> MenuActionResult.Ignored
                }
        }

        private fun handleDeleteWorldConfirmationRuntime(player: Player, operation: WorldSettingsRuntimeOperation?, worldData: WorldData): MenuActionResult {
                return when (operation) {
                        WorldSettingsRuntimeOperation.CANCEL -> MenuActionResult.Success(MenuUpdate.Back)
                        WorldSettingsRuntimeOperation.DELETE_WORLD -> MenuActionResult.Success(
                                MenuUpdate.Navigate(
                                        plugin.worldSettingsGui.runtimeRoute(
                                                WorldSettingsRuntimeScreen.DELETE_WORLD_FINAL_CONFIRM,
                                                worldData.uuid,
                                        ),
                                ),
                        )
                        else -> MenuActionResult.Ignored
                }
        }

        private fun handleDeleteWorldFinalConfirmationRuntime(player: Player, operation: WorldSettingsRuntimeOperation?, worldData: WorldData): MenuActionResult {
                return when (operation) {
                        WorldSettingsRuntimeOperation.CANCEL -> MenuActionResult.Success(MenuUpdate.Back)
                        WorldSettingsRuntimeOperation.CONFIRM -> {
                                if (!canOwnerExecuteDelete(worldData)) {
                                        sendDeleteUnavailableMessage(player)
                                        return MenuActionResult.Rejected()
                                }
                                val refundRate = plugin.config.getDouble("critical_settings.refund_percentage", 0.5)
                                val refund = (worldData.cumulativePoints * refundRate).toInt()
                                player.sendMessage(plugin.languageManager.getMessage(player, "messages.world_delete_start", mapOf("world" to worldData.name)))
                                plugin.worldService.deleteWorld(worldData.uuid, player).thenAccept { success: Boolean ->
                                        Bukkit.getScheduler().runTask(plugin, Runnable {
                                                if (success) {
                                                        player.sendMessage(plugin.languageManager.getMessage(player, "messages.world_delete_success", mapOf("points" to refund)))
                                                } else {
                                                        player.sendMessage(plugin.languageManager.getMessage(player, "messages.world_delete_fail"))
                                                }
                                        })
                                }
                                plugin.settingsSessionManager.endSession(player)
                                MenuActionResult.Success(MenuUpdate.Close)
                        }
                        else -> MenuActionResult.Ignored
                }
        }

        private fun handleArchiveConfirmationRuntime(player: Player, operation: WorldSettingsRuntimeOperation?, worldData: WorldData): MenuActionResult {
                return when (operation) {
                        WorldSettingsRuntimeOperation.CANCEL -> {
                                stopBorderDirectionPreview(player)
                                MenuActionResult.Success(MenuUpdate.Back)
                        }
                        WorldSettingsRuntimeOperation.CONFIRM -> {
                                player.sendMessage(plugin.languageManager.getMessage(player, "messages.archive_success", mapOf("world" to worldData.name)))
                                worldData.isArchived = true
                                plugin.worldConfigRepository.save(worldData)
                                plugin.settingsSessionManager.endSession(player)
                                MenuActionResult.Success(MenuUpdate.Close)
                        }
                        else -> MenuActionResult.Ignored
                }
        }

        private fun handleArchiveFromCriticalConfirmationRuntime(player: Player, operation: WorldSettingsRuntimeOperation?, worldData: WorldData): MenuActionResult {
                return when (operation) {
                        WorldSettingsRuntimeOperation.CANCEL -> MenuActionResult.Success(MenuUpdate.Back)
                        WorldSettingsRuntimeOperation.CONFIRM -> {
                                player.sendMessage(plugin.languageManager.getMessage(player, "messages.archive_start"))
                                plugin.worldService.archiveWorld(worldData.uuid).thenAccept { success: Boolean ->
                                        Bukkit.getScheduler().runTask(plugin, Runnable {
                                                if (success) {
                                                        val now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                                        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                                                        stats.lastArchiveActionAt = now
                                                        plugin.playerStatsRepository.save(stats)
                                                        player.sendMessage(plugin.languageManager.getMessage(player, "messages.archive_success", mapOf("world" to worldData.name)))
                                                } else {
                                                        player.sendMessage(plugin.languageManager.getMessage(player, "messages.archive_failed"))
                                                }
                                        })
                                }
                                plugin.settingsSessionManager.endSession(player)
                                MenuActionResult.Success(MenuUpdate.Close)
                        }
                        else -> MenuActionResult.Ignored
                }
        }

        private fun handleUnarchiveConfirmationRuntime(player: Player, operation: WorldSettingsRuntimeOperation?, worldData: WorldData): MenuActionResult {
                return when (operation) {
                        WorldSettingsRuntimeOperation.CANCEL -> {
                                MenuActionResult.Success(MenuUpdate.Back)
                        }
                        WorldSettingsRuntimeOperation.CONFIRM -> {
                                player.sendMessage(plugin.languageManager.getMessage(player, "messages.unarchive_start"))
                                plugin.worldService.unarchiveWorld(worldData.uuid).thenAccept { success: Boolean ->
                                        Bukkit.getScheduler().runTask(plugin, Runnable {
                                                if (success) {
                                                        val now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                                        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                                                        stats.lastArchiveActionAt = now
                                                        plugin.playerStatsRepository.save(stats)
                                                        plugin.worldService.teleportToWorld(player, worldData.uuid) {
                                                                player.sendMessage(plugin.languageManager.getMessage(player, "messages.unarchive_success"))
                                                        }
                                                } else {
                                                        player.sendMessage(plugin.languageManager.getMessage(player, "error.unarchive_failed"))
                                                }
                                        })
                                }
                                plugin.settingsSessionManager.endSession(player)
                                MenuActionResult.Success(MenuUpdate.Close)
                        }
                        else -> MenuActionResult.Ignored
                }
        }

        /**
         * member_management route 固有の招待操作です。画面の識別は Runtime route が担い、
         * SettingsSession.action は Dialog 応答中の入力ワークフロー状態だけに使用します。
         */
        private fun handleMemberManagementInviteClick(
                player: Player,
                click: ClickType,
                item: ItemStack,
                runtimeContext: WorldSettingsRuntimeContext,
        ): MenuActionResult? {
                if (runtimeContext.screen != WorldSettingsRuntimeScreen.MEMBER_MANAGEMENT ||
                                runtimeContext.operation != WorldSettingsRuntimeOperation.INVITE_MEMBER
                ) {
                        return null
                }
                val worldUuid =
                        runtimeContext.worldUuid
                                ?: plugin.settingsSessionManager.getSession(player)?.worldUuid
                                ?: return MenuActionResult.Ignored
                val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
                        ?: return MenuActionResult.Ignored
                val forceAddMode = PermissionManager.canForceAddMember(player) && click.isShiftClick

                if (openBedrockMemberInviteInputForm(player, worldData, forceAddMode)) {
                        return MenuActionResult.Success(MenuUpdate.None)
                }
                if (plugin.playerPlatformResolver.isBedrock(player)) {
                        plugin.floodgateFormBridge.notifyFallbackCancelled(player)
                        reopenMemberManagementLatest(player, worldData.uuid)
                        return MenuActionResult.Success(MenuUpdate.None)
                }

                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.MEMBER_INVITE,
                        isGui = true,
                )
                showMemberInviteDialog(player, forceAddMode)
                return MenuActionResult.Success(MenuUpdate.None)
        }

        /** Route 固有で、外部入力を開始しないメンバー管理操作です。 */
        private fun handleMemberManagementRouteClick(
                player: Player,
                click: ClickType,
                item: ItemStack,
                runtimeContext: WorldSettingsRuntimeContext,
        ): MenuActionResult? {
                if (runtimeContext.screen != WorldSettingsRuntimeScreen.MEMBER_MANAGEMENT) return null
                val worldUuid = runtimeContext.worldUuid ?: return MenuActionResult.Ignored
                val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
                        ?: return MenuActionResult.Ignored
                when (runtimeContext.operation) {
                        WorldSettingsRuntimeOperation.PAGE -> {
                                val targetPage = runtimeContext.actionPayload[WorldSettingsGui.ROUTE_PAGE]
                                        ?.toIntOrNull()
                                        ?: return MenuActionResult.Ignored
                                return MenuActionResult.Success(
                                        MenuUpdate.Replace(
                                                plugin.worldSettingsGui.memberManagementRoute(
                                                        worldData.uuid,
                                                        targetPage,
                                                ),
                                        ),
                                )
                        }
                        WorldSettingsRuntimeOperation.BACK -> {
                                return MenuActionResult.Success(MenuUpdate.Back)
                        }
                        WorldSettingsRuntimeOperation.MEMBER_OWNER_RESET -> {
                                if (plugin.settingsSessionManager.getSession(player)?.isAdminFlow != true) {
                                        PermissionManager.sendNoPermissionMessage(player)
                                        return MenuActionResult.Rejected()
                                }
                                showAdminOwnerResetDialog(player, worldData)
                                return MenuActionResult.Success(MenuUpdate.None)
                        }
                        WorldSettingsRuntimeOperation.MEMBER -> {
                                val memberId = runtimeContext.actionPayload[WorldSettingsGui.ROUTE_TARGET_UUID]
                                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                                if (
                                        memberId != null &&
                                        !click.isShiftClick &&
                                        click.isLeftClick
                                ) {
                                        val capabilityId = runtimeContext.actionPayload[
                                                WorldSettingsGui.ROUTE_CAPABILITY_ID
                                        ]
                                        if (capabilityId != null) {
                                                return CCSystem.getAPI().getMenuCapabilityService().execute(
                                                        capabilityId,
                                                        player,
                                                        click,
                                                        attributes = mapOf(
                                                                MemberManagementCapabilityContract.SUBJECT_ATTRIBUTE to
                                                                        MemberManagementCapabilitySubject(
                                                                                player,
                                                                                worldData,
                                                                                memberId,
                                                                        ),
                                                        ),
                                                )
                                        }
                                }
                                return handleMemberManagementMemberItemClick(
                                        player,
                                        memberId,
                                        worldData,
                                        click.isShiftClick,
                                        click.isLeftClick,
                                        click.isRightClick
                                )
                        }
                        WorldSettingsRuntimeOperation.PENDING_INVITE -> {
                                val decisionId = runtimeContext.actionPayload[WorldSettingsGui.ROUTE_DECISION_ID]
                                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                                        ?: return MenuActionResult.Ignored
                                return handleMemberPendingInviteClick(player, decisionId, worldData, click.isLeftClick)
                        }
                        WorldSettingsRuntimeOperation.PENDING_REQUEST -> {
                                val decisionId = runtimeContext.actionPayload[WorldSettingsGui.ROUTE_DECISION_ID]
                                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                                        ?: return MenuActionResult.Ignored
                                return handleMemberPendingRequestClick(player, decisionId, worldData, click.isLeftClick)
                        }
                        else -> return MenuActionResult.Ignored
                }
        }

        private fun handleMemberPendingInviteClick(
                player: Player,
                decisionId: UUID,
                worldData: WorldData,
                isLeftClick: Boolean,
        ): MenuActionResult {
                if (!isLeftClick) {
                        return MenuActionResult.Ignored
                }
                val lang = plugin.languageManager
                if (!canCancelMemberInvite(player, worldData)) {
                        player.sendMessage(lang.getMessage(player, "general.no_permission"))
                        plugin.soundManager.playActionSound(player, "world_settings", "error")
                        return MenuActionResult.Rejected()
                }

                val interaction = plugin.pendingInteractionRepository.findById(decisionId)
                if (
                        interaction == null ||
                                interaction.type != PendingInteractionType.MEMBER_INVITE ||
                                interaction.worldUuid != worldData.uuid
                ) {
                        player.sendMessage(
                                lang.getMessage(player, "messages.member_invite_cancel_not_found")
                        )
                        return MenuActionResult.Success(MenuUpdate.Refresh)
                }

                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.MEMBER_PENDING_INVITE_CANCEL_CONFIRM,
                        isGui = true
                )
                return MenuActionResult.Success(
                        MenuUpdate.Navigate(
                                plugin.worldSettingsGui.runtimeRoute(
                                        WorldSettingsRuntimeScreen.MEMBER_PENDING_INVITE_CANCEL_CONFIRM,
                                        worldData.uuid,
                                        targetUuid = interaction.targetUuid,
                                        decisionId = decisionId,
                                ),
                        ),
                )
        }

        private fun handleMemberPendingRequestClick(
                player: Player,
                decisionId: UUID,
                worldData: WorldData,
                isLeftClick: Boolean,
        ): MenuActionResult {
                if (!isLeftClick) {
                        return MenuActionResult.Ignored
                }

                val lang = plugin.languageManager
                val interaction = plugin.pendingInteractionRepository.findById(decisionId)
                if (
                        interaction == null ||
                                interaction.type != PendingInteractionType.MEMBER_REQUEST ||
                                interaction.worldUuid != worldData.uuid
                ) {
                        player.sendMessage(lang.getMessage(player, "messages.myworld_pending_none"))
                        return MenuActionResult.Success(MenuUpdate.Refresh)
                }
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.MEMBER_REQUEST_OWNER_CONFIRM,
                        isGui = true
                )
                val route = plugin.memberRequestOwnerConfirmGui.prepareOpen(
                                player,
                                me.awabi2048.myworldmanager.service.MemberRequestInfo(
                                        requestorUuid = interaction.actorUuid,
                                        worldUuid = interaction.worldUuid,
                                        ownerUuid = interaction.targetUuid,
                                        decisionId = interaction.id,
                                        createdAt = interaction.createdAt
                                ),
                                interaction.id.toString()
                        ) ?: return MenuActionResult.Rejected()
                return MenuActionResult.Success(MenuUpdate.Navigate(route))
        }

        private fun handleMemberManagementMemberItemClick(
                player: Player,
                memberId: UUID?,
                worldData: WorldData,
                isShiftClick: Boolean,
                isLeftClick: Boolean,
                isRightClick: Boolean,
        ): MenuActionResult {
                if (memberId == null || memberId == player.uniqueId) {
                        return MenuActionResult.Ignored
                }
                val lang = plugin.languageManager
                if (!isShiftClick) {
                        if (isLeftClick) {
                                toggleMemberRole(player, worldData, memberId)
                                return MenuActionResult.Success(MenuUpdate.Refresh)
                        }
                        return MenuActionResult.Ignored
                }
                if (isLeftClick) {
                        val stats = plugin.playerStatsRepository.findByUuid(memberId)
                        val maxCounts =
                                WorldRuntimePolicies.maxCreateCountDefault(plugin.config) +
                                        stats.unlockedWorldSlot
                        val currentCounts =
                                plugin.worldConfigRepository.findAll().count { it.owner == memberId }
                        val standardLimitReached =
                                MyWorldManagerApi.isWorldSlotSystemEnabled() &&
                                        currentCounts >= maxCounts
                        val addonDecision =
                                MyWorldManagerApi.checkWorldCreation(
                                        me.awabi2048.myworldmanager.api.extension.WorldCreationRequest(
                                                actor = player,
                                                player = null,
                                                operation =
                                                        me.awabi2048.myworldmanager.api.extension
                                                                .WorldCreationOperation.NORMAL,
                                                type = null,
                                                targetUuid = memberId
                                        )
                                )
                        if (
                                !PermissionManager.canBypassWorldLimits(player) &&
                                        (standardLimitReached || !addonDecision.allowed)
                        ) {
                                player.sendMessage(
                                        plugin.languageManager.getMessage(
                                                "messages.owner_transfer_failed_limit"
                                        )
                                )
                                plugin.soundManager.playActionSound(
                                        player,
                                        "creation",
                                        "limit_reached"
                                )
                                return MenuActionResult.Rejected()
                        }

                        plugin.settingsSessionManager.updateSessionAction(
                                player,
                                worldData.uuid,
                                SettingsAction.MEMBER_TRANSFER_CONFIRM,
                                isGui = true
                        )
                        return MenuActionResult.Success(
                                MenuUpdate.Navigate(
                                        plugin.worldSettingsGui.runtimeRoute(
                                                WorldSettingsRuntimeScreen.MEMBER_TRANSFER_CONFIRM,
                                                worldData.uuid,
                                                targetUuid = memberId,
                                        ),
                                ),
                        )
                } else if (isRightClick) {
                        plugin.settingsSessionManager.updateSessionAction(
                                player,
                                worldData.uuid,
                                SettingsAction.MEMBER_REMOVE_CONFIRM,
                                isGui = true
                        )
                        return MenuActionResult.Success(
                                MenuUpdate.Navigate(
                                        plugin.worldSettingsGui.runtimeRoute(
                                                WorldSettingsRuntimeScreen.MEMBER_REMOVE_CONFIRM,
                                                worldData.uuid,
                                                targetUuid = memberId,
                                        ),
                                ),
                        )
                }
                return MenuActionResult.Ignored
        }

        private val borderResetSpawnService = BorderResetSpawnService()
        private val expansionInitialSizeConfigKey = listOf("expansion", "initial_size").joinToString(".")

        data class PendingExpansion(
                val worldData: WorldData,
                val cost: Int,
                val direction: BlockFace?,
                val task: BukkitTask
        )

        private fun externalInputFor(action: SettingsAction): MenuExternalInput {
                return when (action) {
                        SettingsAction.SELECT_ICON -> MenuExternalInput.SELECT_ICON
                        SettingsAction.RENAME_WORLD -> MenuExternalInput.RENAME_WORLD
                        SettingsAction.CHANGE_DESCRIPTION -> MenuExternalInput.CHANGE_DESCRIPTION
                        SettingsAction.SET_ANNOUNCEMENT -> MenuExternalInput.SET_ANNOUNCEMENT
                        SettingsAction.MEMBER_INVITE -> MenuExternalInput.MEMBER_INVITE
                        SettingsAction.MANAGE_TAGS -> MenuExternalInput.MANAGE_TAGS
                        SettingsAction.SET_SPAWN_GUEST,
                        SettingsAction.SET_SPAWN_MEMBER -> MenuExternalInput.SET_SPAWN
                        SettingsAction.EXPAND_DIRECTION_WAIT,
                        SettingsAction.EXPAND_DIRECTION_CONFIRM -> MenuExternalInput.EXPAND_DIRECTION
                        SettingsAction.ADMIN_PLAYER_FILTER -> MenuExternalInput.ADMIN_PLAYER_FILTER
                        else -> MenuExternalInput.NONE
                }
        }

        fun startWorldBorderExpansionSequence(
                player: Player,
                worldUuid: UUID,
                options: ExpansionSequenceOptions
        ): Boolean {
                val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return false
                if (worldData.borderExpansionLevel == WorldData.EXPANSION_LEVEL_SPECIAL) {
                        return false
                }

                val maxLevel =
                        plugin.config.getConfigurationSection("expansion.costs")
                                ?.getKeys(false)
                                ?.size ?: 3
                if (worldData.borderExpansionLevel >= maxLevel) {
                        player.sendMessage(plugin.languageManager.getMessage("error.max_expansion_reached"))
                        return false
                }

                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldUuid,
                        SettingsAction.VIEW_SETTINGS,
                        isGui = false
                )
                val session = plugin.settingsSessionManager.getSession(player) ?: return false
                session.expansionExecutionMode = options.executionMode
                session.expansionCost = 0
                session.expansionDirection = options.direction

                return when (options.startPhase) {
                        ExpansionSequencePhase.METHOD_SELECT -> {
                                plugin.worldSettingsGui.openExpansionMethodSelection(player, worldData)
                                true
                        }
                        ExpansionSequencePhase.DIRECTION_SELECT -> {
                                startExpansionDirectionSelection(player, session)
                                CCSystem.getAPI().getMenuRuntimeService().suspendForExternal(player)
                                true
                        }
                        ExpansionSequencePhase.PREVIEW,
                        ExpansionSequencePhase.CONFIRM -> {
                                session.action = SettingsAction.EXPAND_DIRECTION_CONFIRM
                                if (options.direction != null &&
                                                !options.skipPhases.contains(ExpansionSequencePhase.PREVIEW)
                                ) {
                                        showBorderPreview(player, worldData, options.direction)
                                }
                                sendExpansionConfirmMessage(player)
                                true
                        }
                        ExpansionSequencePhase.EXECUTE -> {
                                executeExpansionByApiMode(player, worldData, options.direction)
                                true
                        }
                        ExpansionSequencePhase.CLEANUP -> {
                                stopBorderDirectionPreview(player)
                                clearBorderPreview(player)
                                plugin.settingsSessionManager.endSession(player)
                                true
                        }
                }
        }

        private fun openBedrockExpandDirectionConfirmForm(
                player: Player,
                worldUuid: UUID,
                direction: org.bukkit.block.BlockFace,
                cost: Int
        ): Boolean {
                if (!plugin.playerPlatformResolver.isBedrock(player)) {
                        return false
                }
                if (!plugin.floodgateFormBridge.isAvailable(player)) {
                        return false
                }

                val session = plugin.settingsSessionManager.getSession(player) ?: return false
                session.action = SettingsAction.EXPAND_CONFIRM
                session.expansionDirection = direction
                session.expansionCost = cost

                val lang = plugin.languageManager
                val directionKey =
                        when (direction) {
                                org.bukkit.block.BlockFace.NORTH_WEST -> "general.direction.north_west"
                                org.bukkit.block.BlockFace.NORTH_EAST -> "general.direction.north_east"
                                org.bukkit.block.BlockFace.SOUTH_WEST -> "general.direction.south_west"
                                org.bukkit.block.BlockFace.SOUTH_EAST -> "general.direction.south_east"
                                else -> "general.direction.unknown"
                        }
                val directionName = lang.getMessage(player, directionKey)
                val methodText =
                        lang.getMessage(
                                player,
                                "gui.expansion.method_direction",
                                mapOf("direction" to directionName)
                        )
                val content =
                        listOf(
                                        "${lang.getMessage(player, "gui.expansion.method_label")}: $methodText",
                                        "${lang.getMessage(player, "gui.expansion.cost_label")}: $cost",
                                        lang.getMessage(player, "gui.expansion.warning")
                                )
                                .joinToString("\n")

                return plugin.floodgateFormBridge.sendSimpleForm(
                        player = player,
                        title = lang.getMessage(player, "gui.expansion.confirm_title"),
                        content = content,
                        buttons =
                                listOf(
                                        lang.getMessage(player, "gui.common.confirm"),
                                        lang.getMessage(player, "messages.expand_retry_button")
                                ),
                        onSelect = { index ->
                                val latestSession = plugin.settingsSessionManager.getSession(player)
                                                ?: return@sendSimpleForm
                                if (latestSession.worldUuid != worldUuid) {
                                        return@sendSimpleForm
                                }

                                if (index == 0) {
                                        val latestWorld =
                                                plugin.worldConfigRepository.findByUuid(worldUuid)
                                                        ?: return@sendSimpleForm
                                        handleExpandConfirm(player, latestWorld)
                                        return@sendSimpleForm
                                }

                                latestSession.action = SettingsAction.EXPAND_DIRECTION_WAIT
                                startBorderDirectionPreview(player)
                                player.sendMessage(
                                        lang.getMessage(player, "messages.expand_direction_prompt")
                                )
                        }
                )
        }

        private fun openBedrockSettingTextInputForm(
                player: Player,
                worldData: WorldData,
                isDescriptionInput: Boolean
        ): Boolean {
                if (!plugin.playerPlatformResolver.isBedrock(player)) {
                        return false
                }
                if (!plugin.floodgateFormBridge.isAvailable(player)) {
                        return false
                }

                val lang = plugin.languageManager
                val titleKey =
                        if (isDescriptionInput) {
                                "gui.bedrock.input.description.title"
                        } else {
                                "gui.bedrock.input.rename.title"
                        }
                val labelKey =
                        if (isDescriptionInput) {
                                "gui.bedrock.input.description.label"
                        } else {
                                "gui.bedrock.input.rename.label"
                        }
                val placeholderKey =
                        if (isDescriptionInput) {
                                "gui.bedrock.input.description.placeholder"
                        } else {
                                "gui.bedrock.input.rename.placeholder"
                        }
                val initialValue = if (isDescriptionInput) worldData.description else worldData.name
                val worldUuid = worldData.uuid

                CCSystem.getAPI().getMenuRuntimeService().suspendForExternal(player)

                val opened =
                        plugin.floodgateFormBridge.sendCustomInputForm(
                                player = player,
                                title = lang.getMessage(player, titleKey),
                                label = lang.getMessage(player, labelKey),
                                placeholder = lang.getMessage(player, placeholderKey),
                                defaultValue = initialValue,
                                onSubmit = { value ->
                                        val latestWorld =
                                                plugin.worldConfigRepository.findByUuid(worldUuid)
                                                        ?: return@sendCustomInputForm
                                        if (isDescriptionInput) {
                                                applyWorldDescriptionUpdate(
                                                        player,
                                                        latestWorld,
                                                        value
                                                )
                                        } else {
                                                applyWorldNameUpdate(player, latestWorld, value)
                                        }
                                },
                                onClosed = {
                                        if (!player.isOnline) {
                                                return@sendCustomInputForm
                                        }
                                        if (plugin.worldConfigRepository.findByUuid(worldUuid) == null) {
                                                return@sendCustomInputForm
                                        }
                                        CCSystem.getAPI().getMenuRuntimeService().resumeFromExternal(player)
                                }
                        )
                return opened
        }

        fun editWorldInfo(player: Player, worldData: WorldData) {
                if (openBedrockWorldInfoInputForm(player, worldData)) return
                if (plugin.playerPlatformResolver.isBedrock(player)) return
                plugin.settingsSessionManager.updateSessionAction(
                        player, worldData.uuid, SettingsAction.RENAME_WORLD
                )
                showWorldInfoDialog(player, worldData)
        }

        fun startIconSelection(player: Player, worldData: WorldData): MenuActionResult {
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.SELECT_ICON
                )
                plugin.settingsSessionManager
                        .getSession(player)
                        ?.let {
                                it.beginExternalInput(MenuExternalInput.SELECT_ICON)
                        }
                player.sendMessage(plugin.languageManager.getMessage("messages.icon_prompt"))
                return MenuActionResult.Success(
                        MenuUpdate.Replace(plugin.worldSettingsGui.iconSelectionRoute(worldData.uuid)),
                )
        }

        fun handleRuntimeIconSelection(
                player: Player,
                clickedItem: ItemStack,
        ): MenuActionResult {
                val session = plugin.settingsSessionManager.getSession(player)
                        ?: return MenuActionResult.Ignored
                if (clickedItem.type == Material.AIR) {
                        return MenuActionResult.Ignored
                }

                val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid)
                        ?: return MenuActionResult.Ignored

                if (clickedItem.type == Material.BLACK_STAINED_GLASS_PANE ||
                        clickedItem.type == Material.GRAY_STAINED_GLASS_PANE
                ) {
                        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BIT, 1.0f, 0.5f)
                        player.sendMessage(plugin.languageManager.getMessage(player, "messages.icon_forbidden"))
                        return MenuActionResult.Rejected()
                }

                worldData.icon = clickedItem.type
                plugin.worldConfigRepository.save(worldData)
                val iconPlaceholder = "\uE000mwm_icon\uE001"
                val itemName = clickedItem.effectiveName().decoration(TextDecoration.ITALIC, false)
                val changedMessage =
                        plugin.languageManager
                                .getComponent(
                                        player,
                                        "messages.icon_changed",
                                        mapOf("icon" to iconPlaceholder)
                                )
                                .replaceText { replacement ->
                                        replacement
                                                .matchLiteral(iconPlaceholder)
                                                .replacement(itemName)
                                }
                player.sendMessage(
                        changedMessage
                )
                plugin.settingsSessionManager.endSession(player)
                return MenuActionResult.Success(
                        MenuUpdate.Replace(plugin.worldSettingsGui.route(worldData.uuid)),
                )
        }

        private fun openBedrockWorldInfoInputForm(player: Player, worldData: WorldData): Boolean {
                if (!plugin.playerPlatformResolver.isBedrock(player)) {
                        return false
                }
                if (!plugin.floodgateFormBridge.isAvailable(player)) {
                        return false
                }

                val lang = plugin.languageManager
                val worldUuid = worldData.uuid
                CCSystem.getAPI().getMenuRuntimeService().suspendForExternal(player)

                return plugin.floodgateFormBridge.sendCustomForm(
                        player = player,
                        title = lang.getMessage(player, "gui.bedrock.input.info_form.title"),
                        inputs =
                                listOf(
                                        me.awabi2048.myworldmanager.ui.bedrock.FloodgateFormBridge
                                                .CustomFormInput(
                                                        label =
                                                                lang.getMessage(
                                                                        player,
                                                                        "gui.bedrock.input.rename.label"
                                                                ),
                                                        placeholder =
                                                                lang.getMessage(
                                                                        player,
                                                                        "gui.bedrock.input.rename.placeholder"
                                                                ),
                                                        defaultValue = worldData.name
                                                ),
                                        me.awabi2048.myworldmanager.ui.bedrock.FloodgateFormBridge
                                                .CustomFormInput(
                                                        label =
                                                                lang.getMessage(
                                                                        player,
                                                                        "gui.bedrock.input.description.label"
                                                                ),
                                                        placeholder =
                                                                lang.getMessage(
                                                                        player,
                                                                        "gui.bedrock.input.description.placeholder"
                                                                ),
                                                        defaultValue = worldData.description
                                                )
                                ),
                        onSubmit = { values ->
                                val latestWorld =
                                        plugin.worldConfigRepository.findByUuid(worldUuid)
                                                ?: return@sendCustomForm
                                val newName = values.getOrNull(0).orEmpty().trim()
                                val newDescription = values.getOrNull(1).orEmpty().trim()
                                applyWorldInfoUpdate(
                                        player,
                                        latestWorld,
                                        newName,
                                        newDescription
                                )
                        },
                        onClosed = {
                                if (!player.isOnline) {
                                        return@sendCustomForm
                                }
                                if (plugin.worldConfigRepository.findByUuid(worldUuid) == null) {
                                        return@sendCustomForm
                                }
                                CCSystem.getAPI().getMenuRuntimeService().resumeFromExternal(player)
                        }
                )
        }

        private fun openBedrockMemberInviteInputForm(
                player: Player,
                worldData: WorldData,
                forceAddMode: Boolean
        ): Boolean {
                if (!plugin.playerPlatformResolver.isBedrock(player)) {
                        return false
                }
                if (!plugin.floodgateFormBridge.isAvailable(player)) {
                        return false
                }

                val lang = plugin.languageManager
                val worldUuid = worldData.uuid
                CCSystem.getAPI().getMenuRuntimeService().suspendForExternal(player)

                return plugin.floodgateFormBridge.sendCustomInputForm(
                        player = player,
                        title = lang.getMessage(player, "gui.bedrock.input.member_invite.title"),
                        label = lang.getMessage(player, "gui.bedrock.input.member_invite.label"),
                        placeholder =
                                lang.getMessage(player, "gui.bedrock.input.member_invite.placeholder"),
                        defaultValue = "",
                        onSubmit = { value ->
                                val latestWorld =
                                        plugin.worldConfigRepository.findByUuid(worldUuid)
                                                ?: return@sendCustomInputForm
                                applyMemberInvite(player, latestWorld, value, forceAddMode)
                        },
                        onClosed = {
                                if (!player.isOnline) {
                                        return@sendCustomInputForm
                                }
                                reopenMemberManagementLatest(player, worldUuid, playSound = false)
                        }
                )
        }

        private fun openBedrockAnnouncementActionForm(player: Player, worldData: WorldData): Boolean {
                if (!plugin.playerPlatformResolver.isBedrock(player)) {
                        return false
                }
                if (!plugin.floodgateFormBridge.isAvailable(player)) {
                        return false
                }

                val lang = plugin.languageManager
                val worldUuid = worldData.uuid
                CCSystem.getAPI().getMenuRuntimeService().suspendForExternal(player)

                return plugin.floodgateFormBridge.sendSimpleForm(
                        player = player,
                        title =
                                lang.getMessage(
                                        player,
                                        "gui.bedrock.input.announcement_menu.title"
                                ),
                        content =
                                lang.getMessage(
                                        player,
                                        "gui.bedrock.input.announcement_menu.content"
                                ),
                        buttons =
                                listOf(
                                        lang.getMessage(
                                                player,
                                                "gui.bedrock.input.announcement_menu.edit"
                                        ),
                                        lang.getMessage(
                                                player,
                                                "gui.bedrock.input.announcement_menu.reset"
                                        )
                                ),
                        onSelect = { index ->
                                val latestWorld =
                                        plugin.worldConfigRepository.findByUuid(worldUuid)
                                                ?: return@sendSimpleForm
                                if (index == 1) {
                                        latestWorld.announcementMessages.clear()
                                        plugin.worldConfigRepository.save(latestWorld)
                                        player.sendMessage(
                                                lang.getMessage(player, "messages.announcement_reset")
                                        )
                                        CCSystem.getAPI().getMenuRuntimeService().resumeFromExternal(player)
                                        return@sendSimpleForm
                                }
                                if (!openBedrockAnnouncementEditForm(player, latestWorld)) {
                                        player.sendMessage(
                                                lang.getMessage(
                                                        player,
                                                        "messages.operation_cancelled"
                                                )
                                        )
                                        CCSystem.getAPI().getMenuRuntimeService().resumeFromExternal(player)
                                }
                        },
                        onClosed = {
                                if (!player.isOnline) {
                                        return@sendSimpleForm
                                }
                                if (plugin.worldConfigRepository.findByUuid(worldUuid) == null) {
                                        return@sendSimpleForm
                                }
                                CCSystem.getAPI().getMenuRuntimeService().resumeFromExternal(player)
                        }
                )
        }

        private fun openBedrockAnnouncementEditForm(player: Player, worldData: WorldData): Boolean {
                if (!plugin.playerPlatformResolver.isBedrock(player)) {
                        return false
                }
                if (!plugin.floodgateFormBridge.isAvailable(player)) {
                        return false
                }

                val lang = plugin.languageManager
                val maxLines = plugin.config.getInt("announcement.max_lines", 5)
                val maxLength = plugin.config.getInt("announcement.max_line_length", 100)
                val worldUuid = worldData.uuid
                val inputs =
                        (0 until maxLines).map { index ->
                                val current = worldData.announcementMessages.getOrNull(index).orEmpty()
                                me.awabi2048.myworldmanager.ui.bedrock.FloodgateFormBridge
                                        .CustomFormInput(
                                                label =
                                                        lang.getMessage(
                                                                player,
                                                                "gui.bedrock.input.announcement_edit.label",
                                                                mapOf("line" to index + 1)
                                                        ),
                                                placeholder =
                                                        lang.getMessage(
                                                                player,
                                                                "gui.bedrock.input.announcement_edit.placeholder",
                                                                mapOf("max" to maxLength)
                                                        ),
                                                defaultValue =
                                                        current
                                                                .removePrefix("§f")
                                                                .replace("§", "&")
                                        )
                        }

                CCSystem.getAPI().getMenuRuntimeService().suspendForExternal(player)

                return plugin.floodgateFormBridge.sendCustomForm(
                        player = player,
                        title =
                                lang.getMessage(
                                        player,
                                        "gui.bedrock.input.announcement_edit.title",
                                        mapOf("max_lines" to maxLines, "max_length" to maxLength)
                                ),
                        inputs = inputs,
                        onSubmit = { values ->
                                val latestWorld =
                                        plugin.worldConfigRepository.findByUuid(worldUuid)
                                                ?: return@sendCustomForm
                                applyAnnouncementUpdateFromForm(
                                        player,
                                        latestWorld,
                                        values
                                )
                        },
                        onClosed = {
                                if (!player.isOnline) {
                                        return@sendCustomForm
                                }
                                if (plugin.worldConfigRepository.findByUuid(worldUuid) == null) {
                                        return@sendCustomForm
                                }
                                CCSystem.getAPI().getMenuRuntimeService().resumeFromExternal(player)
                        }
                )
        }

        private fun applyAnnouncementUpdateFromForm(
                player: Player,
                worldData: WorldData,
                rawInputs: List<String>
        ) {
                val lang = plugin.languageManager
                val maxLines = plugin.config.getInt("announcement.max_lines", 5)
                val maxLength = plugin.config.getInt("announcement.max_line_length", 100)
                val blockedStrings = plugin.config.getStringList("announcement.blocked_strings")

                val trimmed = rawInputs.map { it.trim() }.filter { it.isNotEmpty() }
                if (trimmed.size > maxLines) {
                        player.sendMessage(
                                lang.getMessage(
                                        player,
                                        "messages.announcement_invalid_length",
                                        mapOf(
                                                "max_lines" to maxLines,
                                                "max_length" to maxLength
                                        )
                                )
                        )
                        plugin.worldSettingsGui.open(player, worldData)
                        return
                }

                for (line in trimmed) {
                        if (line.length > maxLength) {
                                player.sendMessage(
                                        lang.getMessage(
                                                player,
                                                "messages.announcement_invalid_length",
                                                mapOf(
                                                        "max_lines" to maxLines,
                                                        "max_length" to maxLength
                                                )
                                        )
                                )
                                plugin.worldSettingsGui.open(player, worldData)
                                return
                        }

                        val blocked =
                                blockedStrings.firstOrNull {
                                        line.contains(it, ignoreCase = true)
                                }
                        if (blocked != null) {
                                player.sendMessage(
                                        lang.getMessage(
                                                player,
                                                "messages.announcement_blocked_string",
                                                mapOf("string" to blocked)
                                        )
                                )
                                plugin.worldSettingsGui.open(player, worldData)
                                return
                        }
                }

                worldData.announcementMessages.clear()
                trimmed.forEach { line ->
                        worldData.announcementMessages.add("§f${line.replace("&", "§")}")
                }

                plugin.worldConfigRepository.save(worldData)
                player.sendMessage(lang.getMessage(player, "messages.announcement_set"))
                plugin.worldSettingsGui.open(player, worldData)
        }

        private fun canCancelMemberInvite(player: Player, worldData: WorldData): Boolean {
                return worldData.owner == player.uniqueId ||
                        worldData.moderators.contains(player.uniqueId)
        }

        private fun reopenMemberManagementLatest(
                player: Player,
                @Suppress("UNUSED_PARAMETER") worldUuid: UUID,
                @Suppress("UNUSED_PARAMETER") playSound: Boolean = false,
        ) {
                if (!player.isOnline) return
                val runtime = CCSystem.getAPI().getMenuRuntimeService()
                if (!runtime.resumeFromExternal(player)) {
                        runtime.refresh(player)
                }
        }

        private fun cancelMemberInviteByDecisionId(
                player: Player,
                worldUuid: UUID,
                decisionId: UUID
        ) {
                val lang = plugin.languageManager
                val latestWorld = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return
                if (!canCancelMemberInvite(player, latestWorld)) {
                        player.sendMessage(lang.getMessage(player, "general.no_permission"))
                        reopenMemberManagementLatest(player, worldUuid)
                        return
                }

                val interaction = plugin.pendingInteractionRepository.findById(decisionId)
                if (
                        interaction == null ||
                                interaction.type != PendingInteractionType.MEMBER_INVITE ||
                                interaction.worldUuid != worldUuid
                ) {
                        player.sendMessage(
                                lang.getMessage(player, "messages.member_invite_cancel_not_found")
                        )
                        reopenMemberManagementLatest(player, worldUuid)
                        return
                }

                plugin.pendingInteractionRepository.remove(decisionId)
                val targetName =
                        PlayerNameUtil.getNameOrDefault(
                                interaction.targetUuid,
                                lang.getMessage(player, "general.unknown")
                        )
                player.sendMessage(
                        lang.getMessage(
                                player,
                                "messages.member_invite_cancelled",
                                mapOf("player" to targetName)
                        )
                )
                reopenMemberManagementLatest(player, worldUuid)
        }

        private fun toggleMemberRole(player: Player, worldData: WorldData, memberId: UUID) {
                val isModerator = worldData.moderators.contains(memberId)
                if (isModerator) {
                        worldData.moderators.remove(memberId)
                        if (!worldData.members.contains(memberId)) {
                                worldData.members.add(memberId)
                        }
                } else {
                        worldData.members.remove(memberId)
                        if (!worldData.moderators.contains(memberId)) {
                                worldData.moderators.add(memberId)
                        }
                }
                plugin.worldConfigRepository.save(worldData)
                reopenMemberManagementLatest(player, worldData.uuid, playSound = false)
        }

        private fun applyMemberInvite(
                player: Player,
                worldData: WorldData,
                targetNameRaw: String,
                forceAddMode: Boolean = false,
                reopenAfter: Boolean = true,
        ) {
                val lang = plugin.languageManager
                val targetName = targetNameRaw.trim()
                val useForceAddMode =
                        forceAddMode && PermissionManager.canForceAddMember(player)

                if (targetName.isEmpty()) {
                        val inputMessageKey =
                                if (useForceAddMode) {
                                        "messages.member_force_add_input"
                                } else {
                                        "messages.member_invite_input"
                                }
                        player.sendMessage(lang.getMessage(player, inputMessageKey))
                        player.playSound(
                                player.location,
                                org.bukkit.Sound.ENTITY_VILLAGER_NO,
                                1.0f,
                                1.0f
                        )
                        if (reopenAfter) {
                                reopenMemberManagementLatest(player, worldData.uuid, playSound = false)
                        }
                        return
                }

                val target = resolveInviteTarget(targetName)

                if (target == null) {
                        player.sendMessage(lang.getMessage(player, "general.player_not_found"))
                        player.playSound(
                                player.location,
                                org.bukkit.Sound.ENTITY_VILLAGER_NO,
                                1.0f,
                                1.0f
                        )
                        if (reopenAfter) {
                                reopenMemberManagementLatest(player, worldData.uuid, playSound = false)
                        }
                        return
                }

                if (target.uniqueId == player.uniqueId) {
                        player.sendMessage(lang.getMessage(player, "messages.invite_self_error"))
                        player.playSound(
                                player.location,
                                org.bukkit.Sound.ENTITY_VILLAGER_NO,
                                1.0f,
                                1.0f
                        )
                        if (reopenAfter) {
                                reopenMemberManagementLatest(player, worldData.uuid, playSound = false)
                        }
                        return
                }

                if (
                        worldData.owner == target.uniqueId ||
                                worldData.members.contains(target.uniqueId) ||
                                worldData.moderators.contains(target.uniqueId)
                ) {
                        player.sendMessage(lang.getMessage(player, "error.invite_already_member"))
                        player.playSound(
                                player.location,
                                org.bukkit.Sound.ENTITY_VILLAGER_NO,
                                1.0f,
                                1.0f
                        )
                        if (reopenAfter) {
                                reopenMemberManagementLatest(player, worldData.uuid, playSound = false)
                        }
                        return
                }

                if (!MyWorldManagerApi.getWorldAccessPolicy().canInviteTarget(player, worldData, target)) {
                        player.sendMessage(me.awabi2048.myworldmanager.util.WorldAccessMessageResolver.inviteTarget(lang, player, worldData, target))
                        player.playSound(
                                player.location,
                                org.bukkit.Sound.ENTITY_VILLAGER_NO,
                                1.0f,
                                1.0f
                        )
                        if (reopenAfter) {
                                reopenMemberManagementLatest(player, worldData.uuid, playSound = false)
                        }
                        return
                }

                if (
                        plugin.pendingInteractionRepository.existsByTargetWorldAndType(
                                target.uniqueId,
                                worldData.uuid,
                                PendingInteractionType.MEMBER_INVITE
                        )
                ) {
                        player.sendMessage(
                                lang.getMessage(player, "messages.member_invite_already_sent")
                        )
                        player.playSound(
                                player.location,
                                org.bukkit.Sound.ENTITY_VILLAGER_NO,
                                1.0f,
                                1.0f
                        )
                        reopenMemberManagementLatest(player, worldData.uuid, playSound = false)
                        return
                }

                if (useForceAddMode) {
                        worldData.members.add(target.uniqueId)
                        plugin.worldConfigRepository.save(worldData)
                        Bukkit.getPluginManager().callEvent(
                                MwmMemberAddedEvent(
                                        worldUuid = worldData.uuid,
                                        memberUuid = target.uniqueId,
                                        memberName = target.name ?: targetName,
                                        addedByUuid = player.uniqueId,
                                        source = MwmMemberAddSource.FORCE_ADD
                                )
                        )

                        if (target is Player && target.isOnline) {
                                target.sendMessage(
                                        lang.getMessage(
                                                target,
                                                "messages.member_force_added_self",
                                                mapOf("world" to worldData.name)
                                        )
                                )
                        }

                        val targetDisplayName = target.name ?: targetName
                        player.sendMessage(
                                lang.getMessage(
                                        player,
                                        "messages.member_force_add_success",
                                        mapOf(
                                                "player" to targetDisplayName,
                                                "world" to worldData.name
                                        )
                                )
                        )

                        val recipients = linkedSetOf<UUID>()
                        recipients.add(worldData.owner)
                        recipients.addAll(worldData.moderators)
                        recipients.addAll(worldData.members)
                        recipients.remove(target.uniqueId)

                        recipients.forEach { memberUuid ->
                                val memberPlayer = Bukkit.getPlayer(memberUuid) ?: return@forEach
                                if (!memberPlayer.isOnline) {
                                        return@forEach
                                }
                                memberPlayer.sendMessage(
                                        lang.getMessage(
                                                memberPlayer,
                                                "messages.member_joined_notify",
                                                mapOf(
                                                        "player" to targetDisplayName,
                                                        "world" to worldData.name
                                                )
                                        )
                                )
                        }

                        plugin.macroManager.execute(
                                "on_member_add",
                                mapOf(
                                        "world_uuid" to worldData.uuid.toString(),
                                        "member" to targetDisplayName
                                )
                        )

                        if (reopenAfter) {
                                reopenMemberManagementLatest(player, worldData.uuid)
                        }
                        return
                }

                val invite = plugin.memberInviteManager.addInvite(
                        target.uniqueId,
                        worldData.uuid,
                        player.uniqueId
                )
                if (target is Player && target.isOnline) {
                        plugin.pendingNotificationService.send(
                                target,
                                me.awabi2048.myworldmanager.service.PendingDecisionManager.PendingType.MEMBER_INVITE,
                                invite.actionCode,
                                player.uniqueId,
                                worldData.uuid
                        )
                }
                player.sendMessage(
                        lang.getMessage(
                                player,
                                        "messages.invite_sent_success",
                                        mapOf(
                                                "player" to (target.name ?: targetName),
                                                "world" to worldData.name
                                        )
                                )
                )
                if (!(target is Player && target.isOnline)) {
                        player.sendMessage(
                                lang.getMessage(
                                        player,
                                        "messages.invite_queued_offline",
                                        mapOf("player" to (target.name ?: targetName))
                                )
                        )
                }

                if (reopenAfter) {
                        reopenMemberManagementLatest(player, worldData.uuid)
                }
        }

        private fun resolveInviteTarget(inputName: String): OfflinePlayer? {
                return PlayerNameUtil.resolveOfflinePlayer(plugin, inputName)
        }

        private fun applyWorldInfoUpdate(
                player: Player,
                worldData: WorldData,
                newName: String,
                newDescription: String
        ) {
                val lang = plugin.languageManager
                var updated = false

                if (newName.isNotBlank()) {
                        val result = plugin.worldValidator.validateName(newName)
                        if (result is WorldNameValidation.Failure) {
                                player.sendMessage(plugin.languageManager.getComponent(player, result.messageKey, result.placeholders))
                        } else if (plugin.worldConfigRepository.findByOwnerAndDisplayName(worldData.owner, newName, worldData.uuid) != null) {
                                player.sendMessage(plugin.languageManager.getMessage(player, "messages.world_name_duplicate"))
                        } else if (worldData.name != newName) {
                                worldData.name = newName
                                updated = true
                                player.sendMessage(lang.getMessage(player, "messages.world_name_change"))
                        }
                }

                if (worldData.description != newDescription) {
                        worldData.description = newDescription
                        updated = true
                        player.sendMessage(lang.getMessage(player, "messages.world_desc_change"))
                }

                if (updated) {
                        plugin.worldConfigRepository.save(worldData)
                }
                CCSystem.getAPI().getMenuRuntimeService().resumeFromExternal(player)
        }

        private fun applyWorldNameUpdate(player: Player, worldData: WorldData, newName: String) {
                val lang = plugin.languageManager
                val result = plugin.worldValidator.validateName(newName)
                if (result is WorldNameValidation.Failure) {
                        player.sendMessage(plugin.languageManager.getComponent(player, result.messageKey, result.placeholders))
                } else if (plugin.worldConfigRepository.findByOwnerAndDisplayName(worldData.owner, newName, worldData.uuid) != null) {
                        player.sendMessage(plugin.languageManager.getMessage(player, "messages.world_name_duplicate"))
                } else {
                        worldData.name = newName
                        plugin.worldConfigRepository.save(worldData)
                        player.sendMessage(lang.getMessage(player, "messages.world_name_change"))
                }

                plugin.settingsSessionManager.endSession(player)
                CCSystem.getAPI().getMenuRuntimeService().resumeFromExternal(player)
        }

        private fun applyWorldDescriptionUpdate(
                player: Player,
                worldData: WorldData,
                newDescription: String
        ) {
                val lang = plugin.languageManager
                worldData.description = newDescription
                plugin.worldConfigRepository.save(worldData)
                player.sendMessage(lang.getMessage(player, "messages.world_desc_change"))

                plugin.settingsSessionManager.endSession(player)
                CCSystem.getAPI().getMenuRuntimeService().resumeFromExternal(player)
        }

        fun onRuntimeInventoryClose(player: Player, reason: MenuCloseReason) {
                val session = plugin.settingsSessionManager.getSession(player) ?: return
                val lang = plugin.languageManager

                if (SettingsClosePolicy.shouldPreserveSession(reason)) {
                        return
                }

                if (session.isExternalInputExpired()) {
                        plugin.settingsSessionManager.endSession(player)
                        return
                }

                // アイコン選択はプレイヤー自身のインベントリを使うため、閉じた時点で明確にキャンセルする。
                if (session.action == SettingsAction.SELECT_ICON) {
                        plugin.settingsSessionManager.endSession(player)
                        player.sendMessage(lang.getMessage(player, "messages.icon_cancelled"))
                        return
                }

                // ブロック選択入力中のアクションは、インベントリを閉じてもセッションを維持する
                val blockInputActions =
                        setOf(
                                SettingsAction.SET_SPAWN_GUEST,
                                SettingsAction.SET_SPAWN_MEMBER,
                                SettingsAction.EXPAND_DIRECTION_WAIT,
                                SettingsAction.EXPAND_DIRECTION_CONFIRM,
                                SettingsAction.RENAME_WORLD,
                                SettingsAction.SET_ANNOUNCEMENT,
                                SettingsAction.MEMBER_INVITE,
                                SettingsAction.MANAGE_TAGS
                        )

                if (session.action in blockInputActions) {
                        if (session.externalInput == MenuExternalInput.NONE) {
                                session.beginExternalInput(externalInputFor(session.action), 300_000L)
                        }
                        return
                }

                // GUI驕ｷ遘ｻ・医し繝悶Γ繝九Η繝ｼ縺ｸ縺ｮ遘ｻ蜍輔ｄ逕ｻ髱｢譖ｴ譁ｰ・峨ｒ閠・・縺励・tick蠕後↓縺ｾ縺險ｭ螳夐未騾｣GUI繧帝幕縺・※縺・ｋ縺九メ繧ｧ繝・け縺吶ｋ
                if (session.action == SettingsAction.EXPAND_DIRECTION_CONFIRM) {
                        return
                }
                clearBorderPreview(player)
                plugin.settingsSessionManager.endSession(player)
        }

        @EventHandler
        fun onWorldChange(event: PlayerChangedWorldEvent) {
                if (MyWorldManagerApi.isLogoutRelocation(event.player)) return
                stopSpawnPreview(event.player)
                stopBorderDirectionPreview(event.player)
                clearBorderPreview(event.player)
                processImmediateExpansion(event.player)
        }

        @EventHandler
        fun onQuit(event: PlayerQuitEvent) {
                stopSpawnPreview(event.player)
                stopBorderDirectionPreview(event.player)
                clearBorderPreview(event.player)
                processImmediateExpansion(event.player)
                CCSystem.getAPI().getMenuRuntimeService().clear(event.player)
        }

        fun startSpawnPreview(player: Player) {
                stopSpawnPreview(player)
                spawnPreviewTasks[player.uniqueId] =
                        Bukkit.getScheduler()
                                .runTaskTimer(
                                        plugin,
                                        Runnable {
                                                if (!player.isOnline) {
                                                        stopSpawnPreview(player)
                                                        return@Runnable
                                                }
                                                val session =
                                                        plugin.settingsSessionManager.getSession(player)
                                                if (session == null ||
                                                                (session.action !=
                                                                        SettingsAction.SET_SPAWN_GUEST &&
                                                                        session.action !=
                                                                                SettingsAction
                                                                                        .SET_SPAWN_MEMBER)
                                                ) {
                                                        stopSpawnPreview(player)
                                                        return@Runnable
                                                }

                                                val targetBlock = player.getTargetBlockExact(6) ?: return@Runnable
                                                val spawnLoc =
                                                        targetBlock.location.clone().add(0.5, 1.0, 0.5)
                                                val yaw = normalizeToCardinalYaw(player.location.yaw)
                                                val placeable = isSpawnAreaPlaceable(spawnLoc)
                                                spawnSpawnPreview(player, spawnLoc, yaw, placeable)
                                        },
                                        0L,
                                        2L
                                )
        }

        private fun stopSpawnPreview(player: Player) {
                spawnPreviewTasks.remove(player.uniqueId)?.cancel()
        }

        private fun normalizeToCardinalYaw(yawRaw: Float): Float {
                var yaw = yawRaw
                while (yaw < 0) yaw += 360
                while (yaw >= 360) yaw -= 360

                return when {
                        yaw >= 45 && yaw < 135 -> 90.0f
                        yaw >= 135 && yaw < 225 -> 180.0f
                        yaw >= 225 && yaw < 315 -> 270.0f
                        else -> 0.0f
                }
        }

        private fun isSpawnAreaPlaceable(spawnLoc: Location): Boolean {
                val feetBlock = spawnLoc.block
                val headBlock = spawnLoc.clone().add(0.0, 1.0, 0.0).block
                return feetBlock.isPassable && headBlock.isPassable
        }

        private fun spawnSpawnPreview(
                player: Player,
                spawnLoc: Location,
                yaw: Float,
                placeable: Boolean
        ) {
                val world = spawnLoc.world ?: return
                val frameColor = if (placeable) Color.fromRGB(64, 255, 120) else Color.fromRGB(255, 80, 80)
                val frameDust = Particle.DustOptions(frameColor, 0.5f)
                val arrowDust = Particle.DustOptions(Color.fromRGB(80, 160, 255), 0.5f)

                val feetBlockY = spawnLoc.blockY
                spawnSpawnBlockOutline(
                        player,
                        spawnLoc.blockX,
                        feetBlockY,
                        spawnLoc.blockZ,
                        frameDust,
                        drawBottomFace = true,
                        drawTopFace = false
                )
                spawnSpawnBlockOutline(
                        player,
                        spawnLoc.blockX,
                        feetBlockY + 1,
                        spawnLoc.blockZ,
                        frameDust,
                        drawBottomFace = false,
                        drawTopFace = true
                )

                val rad = Math.toRadians(yaw.toDouble())
                val forwardX = -kotlin.math.sin(rad)
                val forwardZ = kotlin.math.cos(rad)
                val arrowStart =
                        Location(
                                world,
                                spawnLoc.x - forwardX * 0.5,
                                spawnLoc.y + 0.15,
                                spawnLoc.z - forwardZ * 0.5
                        )
                spawnDirectionArrow(player, arrowStart, yaw, arrowDust)
        }

        private fun spawnSpawnBlockOutline(
                player: Player,
                blockX: Int,
                blockY: Int,
                blockZ: Int,
                dust: Particle.DustOptions,
                drawBottomFace: Boolean,
                drawTopFace: Boolean
        ) {
                val minX = blockX.toDouble()
                val minY = blockY.toDouble()
                val minZ = blockZ.toDouble()
                val maxX = blockX + 1.0
                val maxY = blockY + 1.0
                val maxZ = blockZ + 1.0

                if (drawBottomFace) {
                        spawnLineWithTenParticles(player, minX, minY, minZ, maxX, minY, minZ, dust)
                        spawnLineWithTenParticles(player, minX, minY, maxZ, maxX, minY, maxZ, dust)
                        spawnLineWithTenParticles(player, minX, minY, minZ, minX, minY, maxZ, dust)
                        spawnLineWithTenParticles(player, maxX, minY, minZ, maxX, minY, maxZ, dust)
                }
                if (drawTopFace) {
                        spawnLineWithTenParticles(player, minX, maxY, minZ, maxX, maxY, minZ, dust)
                        spawnLineWithTenParticles(player, minX, maxY, maxZ, maxX, maxY, maxZ, dust)
                        spawnLineWithTenParticles(player, minX, maxY, minZ, minX, maxY, maxZ, dust)
                        spawnLineWithTenParticles(player, maxX, maxY, minZ, maxX, maxY, maxZ, dust)
                }

                spawnLineWithTenParticles(player, minX, minY, minZ, minX, maxY, minZ, dust)
                spawnLineWithTenParticles(player, maxX, minY, minZ, maxX, maxY, minZ, dust)
                spawnLineWithTenParticles(player, minX, minY, maxZ, minX, maxY, maxZ, dust)
                spawnLineWithTenParticles(player, maxX, minY, maxZ, maxX, maxY, maxZ, dust)
        }

        private fun spawnDirectionArrow(
                player: Player,
                start: Location,
                yaw: Float,
                dust: Particle.DustOptions
        ) {
                val rad = Math.toRadians(yaw.toDouble())
                val forwardX = -kotlin.math.sin(rad)
                val forwardZ = kotlin.math.cos(rad)

                val tipX = start.x + forwardX * 1.0
                val tipZ = start.z + forwardZ * 1.0
                spawnLineWithTenParticles(player, start.x, start.y, start.z, tipX, start.y, tipZ, dust)

                val baseX = tipX - forwardX * 0.4
                val baseZ = tipZ - forwardZ * 0.4
                val sideX = -forwardZ * 0.2
                val sideZ = forwardX * 0.2

                spawnLineWithFiveParticles(
                        player,
                        tipX,
                        start.y,
                        tipZ,
                        baseX + sideX,
                        start.y,
                        baseZ + sideZ,
                        dust
                )
                spawnLineWithFiveParticles(
                        player,
                        tipX,
                        start.y,
                        tipZ,
                        baseX - sideX,
                        start.y,
                        baseZ - sideZ,
                        dust
                )
        }

        private fun spawnLineWithFiveParticles(
                player: Player,
                startX: Double,
                startY: Double,
                startZ: Double,
                endX: Double,
                endY: Double,
                endZ: Double,
                dust: Particle.DustOptions
        ) {
                for (i in 0..4) {
                        val t = i.toDouble() / 4.0
                        val x = startX + (endX - startX) * t
                        val y = startY + (endY - startY) * t
                        val z = startZ + (endZ - startZ) * t
                        player.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust)
                }
        }

        private fun spawnLineWithTenParticles(
                player: Player,
                startX: Double,
                startY: Double,
                startZ: Double,
                endX: Double,
                endY: Double,
                endZ: Double,
                dust: Particle.DustOptions
        ) {
                for (i in 0..9) {
                        val t = i.toDouble() / 9.0
                        val x = startX + (endX - startX) * t
                        val y = startY + (endY - startY) * t
                        val z = startZ + (endZ - startZ) * t
                        player.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust)
                }
        }

        private fun spawnLineWithTenParticlesWax(
                player: Player,
                startX: Double,
                startY: Double,
                startZ: Double,
                endX: Double,
                endY: Double,
                endZ: Double,
                particle: Particle
        ) {
                for (i in 0..9) {
                        val t = i.toDouble() / 9.0
                        val x = startX + (endX - startX) * t
                        val y = startY + (endY - startY) * t
                        val z = startZ + (endZ - startZ) * t
                        player.spawnParticle(particle, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
                }
        }

        private fun spawnSpawnBlockOutlineWax(
                player: Player,
                blockX: Int,
                blockY: Int,
                blockZ: Int,
                particle: Particle,
                drawBottomFace: Boolean,
                drawTopFace: Boolean
        ) {
                val minX = blockX.toDouble()
                val minY = blockY.toDouble()
                val minZ = blockZ.toDouble()
                val maxX = blockX + 1.0
                val maxY = blockY + 1.0
                val maxZ = blockZ + 1.0

                if (drawBottomFace) {
                        spawnLineWithTenParticlesWax(player, minX, minY, minZ, maxX, minY, minZ, particle)
                        spawnLineWithTenParticlesWax(player, minX, minY, maxZ, maxX, minY, maxZ, particle)
                        spawnLineWithTenParticlesWax(player, minX, minY, minZ, minX, minY, maxZ, particle)
                        spawnLineWithTenParticlesWax(player, maxX, minY, minZ, maxX, minY, maxZ, particle)
                }
                if (drawTopFace) {
                        spawnLineWithTenParticlesWax(player, minX, maxY, minZ, maxX, maxY, minZ, particle)
                        spawnLineWithTenParticlesWax(player, minX, maxY, maxZ, maxX, maxY, maxZ, particle)
                        spawnLineWithTenParticlesWax(player, minX, maxY, minZ, minX, maxY, maxZ, particle)
                        spawnLineWithTenParticlesWax(player, maxX, maxY, minZ, maxX, maxY, maxZ, particle)
                }

                spawnLineWithTenParticlesWax(player, minX, minY, minZ, minX, maxY, minZ, particle)
                spawnLineWithTenParticlesWax(player, maxX, minY, minZ, maxX, maxY, minZ, particle)
                spawnLineWithTenParticlesWax(player, minX, minY, maxZ, minX, maxY, maxZ, particle)
                spawnLineWithTenParticlesWax(player, maxX, minY, maxZ, maxX, maxY, maxZ, particle)
        }

        private fun showSpawnConfirmEffect(player: Player, spawnLoc: Location, isGuest: Boolean) {
                val waxParticle = if (isGuest) Particle.WAX_ON else Particle.WAX_OFF
                val blockY = spawnLoc.blockY
                spawnSpawnBlockOutlineWax(player, spawnLoc.blockX, blockY, spawnLoc.blockZ, waxParticle,
                        drawBottomFace = true, drawTopFace = false)
                spawnSpawnBlockOutlineWax(player, spawnLoc.blockX, blockY + 1, spawnLoc.blockZ, waxParticle,
                        drawBottomFace = false, drawTopFace = true)
        }

        private fun startExpansionDirectionSelection(
                player: Player,
                session: me.awabi2048.myworldmanager.session.SettingsSession
        ) {
                session.action = SettingsAction.EXPAND_DIRECTION_WAIT
                startBorderDirectionPreview(player)
        }

        private fun startBorderDirectionPreview(player: Player) {
                stopBorderDirectionPreview(player)
                borderDirectionPreviewTasks[player.uniqueId] =
                        Bukkit.getScheduler()
                                .runTaskTimer(
                                        plugin,
                                        Runnable {
                                                if (!player.isOnline) {
                                                        stopBorderDirectionPreview(player)
                                                        return@Runnable
                                                }

                                                val session =
                                                        plugin.settingsSessionManager.getSession(player)
                                                if (session == null ||
                                                                session.action !=
                                                                        SettingsAction.EXPAND_DIRECTION_WAIT
                                                ) {
                                                        stopBorderDirectionPreview(player)
                                                        return@Runnable
                                                }

                                                spawnBorderDirectionArrows(player)
                                        },
                                        0L,
                                        2L
                                )
        }

        private fun stopBorderDirectionPreview(player: Player) {
                borderDirectionPreviewTasks.remove(player.uniqueId)?.cancel()
        }

        private fun spawnBorderDirectionArrows(player: Player) {
                val center = player.location
                var yaw = center.yaw
                while (yaw < -180) yaw += 360
                while (yaw >= 180) yaw -= 360

                val (dirX, dirZ) = when {
                        yaw >= 0 && yaw < 90 -> -1.0 to 1.0    // SOUTH_WEST
                        yaw >= 90 && yaw < 180 -> -1.0 to -1.0  // NORTH_WEST
                        yaw >= -90 && yaw < 0 -> 1.0 to 1.0     // SOUTH_EAST
                        else -> 1.0 to -1.0                      // NORTH_EAST
                }

                val offset = 1.35
                val y = center.y + 1.0
                val dust = Particle.DustOptions(Color.AQUA, 0.5f)

                spawnBorderDirectionArrow(player, center, y, dirX, dirZ, offset, dust)
        }

        private fun spawnBorderDirectionArrow(
                player: Player,
                center: Location,
                y: Double,
                directionX: Double,
                directionZ: Double,
                offset: Double,
                dust: Particle.DustOptions
        ) {
                val length = kotlin.math.sqrt(directionX * directionX + directionZ * directionZ)
                if (length <= 0.0) return

                val normalizedX = directionX / length
                val normalizedZ = directionZ / length
                val start =
                        Location(
                                center.world,
                                center.x + normalizedX * offset - normalizedX * 0.5,
                                y,
                                center.z + normalizedZ * offset - normalizedZ * 0.5
                        )
                val yaw = Math.toDegrees(kotlin.math.atan2(-normalizedX, normalizedZ)).toFloat()
                spawnDirectionArrow(player, start, yaw, dust)
        }

        private fun processImmediateExpansion(player: Player) {
                val pending = pendingExpansions.remove(player.uniqueId) ?: return
                pending.task.cancel()
                executeExpansionFinal(player, pending.worldData, pending.cost, pending.direction)
        }

        private fun executeExpansionFinal(
                player: Player,
                worldData: WorldData,
                cost: Int,
                direction: BlockFace?
        ) {
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                if (performExpansion(worldData, direction)) {
                        chargeWorldPoints(stats, worldData, cost)
                        plugin.playerStatsRepository.save(stats)
                        plugin.worldConfigRepository.save(worldData)
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "messages.expand_complete",
                                        mapOf(
                                                "level_before" to
                                                        (worldData.borderExpansionLevel - 1),
                                                "level_after" to worldData.borderExpansionLevel
                                        )
                                )
                        )
                } else {
player.sendMessage(
                                plugin.languageManager.getMessage("error.expand_failed")
                        )
                }
        }

        private fun executeExpansionByApiMode(
                player: Player,
                worldData: WorldData,
                direction: BlockFace?
        ) {
                val session = plugin.settingsSessionManager.getSession(player)
                if (session != null &&
                                expansionExecutionMode(session) !=
                                        ExpansionExecutionMode.IMMEDIATE_NO_COST
                ) {
                        handleExpandConfirm(player, worldData)
                        return
                }

                if (performExpansion(worldData, direction)) {
                        plugin.worldConfigRepository.save(worldData)
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "messages.expand_complete",
                                        mapOf(
                                                "level_before" to
                                                        (worldData.borderExpansionLevel - 1),
                                                "level_after" to worldData.borderExpansionLevel
                                        )
                                )
                        )
                        plugin.soundManager.playActionSound(player, "creation", "wizard_next")
                } else {
                        player.sendMessage(plugin.languageManager.getMessage("error.expand_failed"))
                }
                clearBorderPreview(player)
                plugin.settingsSessionManager.endSession(player)
                CCSystem.getAPI().getMenuRuntimeService().resumeFromExternal(player)
        }

        private fun expansionExecutionMode(
                session: me.awabi2048.myworldmanager.session.SettingsSession
        ): ExpansionExecutionMode = session.expansionExecutionMode

        @EventHandler
        fun onInteract(event: PlayerInteractEvent) {
                if (event.hand != EquipmentSlot.HAND) return

                val player = event.player
                val settingsSession = plugin.settingsSessionManager.getSession(player) ?: return
                val currentAction = settingsSession.action

                if (currentAction == SettingsAction.EXPAND_DIRECTION_WAIT) {
                        val isBedrock = plugin.playerPlatformResolver.isBedrock(player)
                        val isDirectionClick =
                                if (isBedrock) {
                                        event.action == Action.LEFT_CLICK_AIR ||
                                                event.action == Action.LEFT_CLICK_BLOCK ||
                                                event.action == Action.RIGHT_CLICK_AIR ||
                                                event.action == Action.RIGHT_CLICK_BLOCK
                                } else {
                                        event.action == Action.LEFT_CLICK_AIR ||
                                                event.action == Action.LEFT_CLICK_BLOCK
                                }
                        if (isDirectionClick) {
                                event.isCancelled = true

                                var yaw = player.location.yaw
                                while (yaw < -180) yaw += 360
                                while (yaw >= 180) yaw -= 360

                                val direction =
                                        when {
                                                yaw >= 0 && yaw < 90 -> BlockFace.SOUTH_WEST
                                                yaw >= 90 && yaw < 180 -> BlockFace.NORTH_WEST
                                                yaw >= -90 && yaw < 0 -> BlockFace.SOUTH_EAST
                                                else -> BlockFace.NORTH_EAST
                                        }

                                settingsSession.expansionDirection = direction
                                settingsSession.action = SettingsAction.EXPAND_DIRECTION_CONFIRM
                                stopBorderDirectionPreview(player)

                                val worldData =
                                        plugin.worldConfigRepository.findByUuid(
                                                settingsSession.worldUuid
                                        ) ?: return
                                val cost =
                                        if (expansionExecutionMode(settingsSession) ==
                                                        ExpansionExecutionMode.IMMEDIATE_NO_COST
                                        ) {
                                                0
                                        } else {
                                                calculateExpansionCost(worldData.borderExpansionLevel)
                                        }

                                settingsSession.expansionCost = cost

                                val directionKey =
                                        when (direction) {
                                                BlockFace.NORTH_WEST ->
                                                        "general.direction.north_west"
                                                BlockFace.NORTH_EAST ->
                                                        "general.direction.north_east"
                                                BlockFace.SOUTH_WEST ->
                                                        "general.direction.south_west"
                                                BlockFace.SOUTH_EAST ->
                                                        "general.direction.south_east"
                                                else -> "general.direction.unknown"
                                        }
                                val directionName =
                                        plugin.languageManager.getMessage(player, directionKey)
                                player.sendMessage(
                                        plugin.languageManager.getMessage(
                                                player,
                                                "messages.expand_direction_selected",
                                                mapOf("direction" to directionName)
                                        )
                                )

                                if (isBedrock &&
                                                openBedrockExpandDirectionConfirmForm(
                                                        player,
                                                        worldData.uuid,
                                                        direction,
                                                        cost
                                                )
                                ) {
                                        return
                                }

                                showBorderPreview(player, worldData, direction)
                                sendExpansionConfirmMessage(player)
                        }
                        return
                }

                if (currentAction == SettingsAction.SET_SPAWN_GUEST ||
                                currentAction == SettingsAction.SET_SPAWN_MEMBER
                ) {
                        if (event.action != Action.RIGHT_CLICK_BLOCK &&
                                        event.action != Action.LEFT_CLICK_BLOCK
                        )
                                return
                        val clickedBlock = event.clickedBlock ?: return

                        event.isCancelled = true
                        val loc = clickedBlock.location.clone().add(0.5, 1.0, 0.5)

                        val normalizedYaw = normalizeToCardinalYaw(player.location.yaw)

                        loc.yaw = normalizedYaw
                        loc.pitch = 0.0f

                        if (!isSpawnAreaPlaceable(loc)) {
                                player.sendMessage(
                                        plugin.languageManager.getMessage(
                                                player,
                                                "error.spawn_set_blocked"
                                        )
                                )
                                return
                        }

                        val worldData =
                                plugin.worldConfigRepository.findByUuid(settingsSession.worldUuid)
                        if (worldData != null) {
                                if (currentAction == SettingsAction.SET_SPAWN_GUEST) {
                                        worldData.spawnPosGuest = loc
                                        player.sendMessage(
                                                plugin.languageManager.getMessage(
                                                        "messages.spawn_guest_set"
                                                )
                                        )
                                } else {
                                        worldData.spawnPosMember = loc
                                        player.sendMessage(
                                                plugin.languageManager.getMessage(
                                                        "messages.spawn_member_set"
                                                )
                                        )
                                }
                                plugin.worldConfigRepository.save(worldData)
                                stopSpawnPreview(player)
                                showSpawnConfirmEffect(player, loc, currentAction == SettingsAction.SET_SPAWN_GUEST)
                                CCSystem.getAPI().getMenuRuntimeService().resumeFromExternal(player)
                        }
                        return
                }
        }

        private fun calculateExpansionCost(currentLevel: Int): Int {
                val targetLevel = currentLevel + 1
                return WorldRuntimePolicies.expansionCost(plugin.config, targetLevel)
        }

        private fun calculateTotalExpansionCost(level: Int): Int {
                return WorldRuntimePolicies.totalExpansionCost(plugin.config, level)
        }

        private fun performExpansion(worldData: WorldData, direction: BlockFace?): Boolean {
                val worldName = "my_world.${worldData.uuid}"
                val world = Bukkit.getWorld(worldName) ?: return false

                val border = world.worldBorder
                val oldSize = border.size
                val oldCenter = border.center.clone()
                val levelBefore = worldData.borderExpansionLevel
                val newSize = oldSize * 2

                if (direction != null) {
                        val radius = oldSize / 2.0

                        var shiftX = 0.0
                        var shiftZ = 0.0

                        when (direction) {
                                BlockFace.NORTH_WEST -> {
                                        shiftX = -radius
                                        shiftZ = -radius
                                }
                                BlockFace.NORTH_EAST -> {
                                        shiftX = radius
                                        shiftZ = -radius
                                }
                                BlockFace.SOUTH_WEST -> {
                                        shiftX = -radius
                                        shiftZ = radius
                                }
                                BlockFace.SOUTH_EAST -> {
                                        shiftX = radius
                                        shiftZ = radius
                                }
                                else -> {}
                        }
                        val newCenter = oldCenter.clone().add(shiftX, 0.0, shiftZ)
                        newCenter.x = Math.round(newCenter.x).toDouble()
                        newCenter.z = Math.round(newCenter.z).toDouble()

                        border.setCenter(newCenter)
                        worldData.borderCenterPos = newCenter
                }

                border.setSize(newSize)

                worldData.borderExpansionLevel += 1
                val newCenter = border.center
                worldData.borderExpansionHistory.add(
                        BorderExpansionRecord(
                                levelBefore = levelBefore,
                                levelAfter = worldData.borderExpansionLevel,
                                direction = direction?.name,
                                oldCenterX = oldCenter.x,
                                oldCenterZ = oldCenter.z,
                                oldSize = oldSize,
                                newCenterX = newCenter.x,
                                newCenterZ = newCenter.z,
                                newSize = newSize
                        )
                )
                plugin.worldConfigRepository.save(worldData)
                return true
        }

        private fun teleportToBorderCenterSurface(player: Player, worldData: WorldData): Boolean {
                val worldName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
                var world = Bukkit.getWorld(worldName)
                val needsLoad = world == null
                if (world == null) {
                        if (!plugin.worldService.loadWorld(worldData.uuid)) {
                                return false
                        }
                        world = Bukkit.getWorld(worldName) ?: return false
                }
                val targetWorld = world

                val center = worldData.borderCenterPos ?: targetWorld.worldBorder.center
                val centerX = Math.round(center.x).toInt()
                val centerZ = Math.round(center.z).toInt()
                val highestY = targetWorld.getHighestBlockYAt(centerX, centerZ)
                val y = (highestY + 1).coerceAtLeast(targetWorld.minHeight + 1)
                val target =
                        org.bukkit.Location(
                                targetWorld,
                                centerX + 0.5,
                                y.toDouble(),
                                centerZ + 0.5,
                                player.location.yaw,
                                player.location.pitch
                        )
                val doTeleport = Runnable {
                        player.teleport(target)
                        plugin.soundManager.playTeleportSound(player)
                }

                if (needsLoad) {
                        val waitTicks = plugin.config.getLong("warp.load_wait_ticks", 10L).coerceAtLeast(0L)
                        Bukkit.getScheduler().runTaskLater(plugin, doTeleport, waitTicks)
                } else {
                        doTeleport.run()
                }
                return true
        }

        fun cycleEnvironmentWeather(player: Player, worldData: WorldData) {
                val session = plugin.settingsSessionManager.getSession(player) ?: return
                val config = plugin.config
                val options = config.getStringList("environment.weather.options")
                if (options.isEmpty()) return

                val currentTemp = session.tempWeather ?: worldData.fixedWeather ?: "DEFAULT"
                session.tempWeather =
                        GuiCycle.select(
                                currentTemp,
                                options,
                                com.awabi2048.ccsystem.api.gui.GuiCycleDirection.NEXT
                        )

        }

        private fun executeGravityChange(
                player: Player,
                worldData: WorldData,
                confirmItem: org.bukkit.inventory.ItemStack
        ) {
                val cost = WorldRuntimePolicies.environmentCost(plugin.config, "gravity")
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)

                if (MyWorldManagerApi.isWorldPointEconomyEnabled() && stats.worldPoint < cost) {
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "gui.creation.insufficient",
                                        mapOf("shortage" to (cost - stats.worldPoint))
                                )
                        )
                        plugin.soundManager.playActionSound(
                                player,
                                "environment",
                                "insufficient_points"
                        )
                        return
                }

                worldData.gravityValue = 0.02
                chargeWorldPoints(stats, worldData, cost)

                plugin.playerStatsRepository.save(stats)
                plugin.worldConfigRepository.save(worldData)

                removeFromInventory(player, confirmItem)

                player.sendMessage(
                        plugin.languageManager.getMessage(
                                player,
                                "messages.env_gravity_changed",
                                mapOf("gravity" to "Moon", "multiplier" to "0.17")
                        )
                )
                sendEnvironmentCostPaid(player, cost, stats.worldPoint)
                plugin.soundManager.playActionSound(player, "environment", "gravity_change")
                plugin.worldEnvironmentService.applyAttributes(worldData.uuid)
                plugin.environmentGui.open(player, worldData)
        }

        private fun executeBiomeChange(
                player: Player,
                worldData: WorldData,
                confirmItem: org.bukkit.inventory.ItemStack
        ) {
                val biomeId = ItemTag.getBiomeId(confirmItem) ?: return
                val lang = plugin.languageManager
                val cost = WorldRuntimePolicies.environmentCost(plugin.config, "biome")
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)

                // Safety Check: Permission & Logic
                val session = plugin.settingsSessionManager.getSession(player)
                val isMember =
                        player.uniqueId == worldData.owner ||
                                worldData.moderators.contains(player.uniqueId) ||
                                worldData.members.contains(player.uniqueId) ||
                                session?.isAdminFlow == true
                val isAdmin = player.hasPermission("myworldmanager.admin")
                val worldFolderName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
                val isAdminWorld = worldData.customWorldName != null

                if (isAdminWorld) {
                        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                        player.sendMessage(lang.getMessage(player, "messages.custom_item.biome_bottle_disabled"))
                        return
                }

                if (!isMember) {
                        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                        player.sendMessage(lang.getMessage(player, "error.custom_item.no_permission"))
                        return
                }

                if (MyWorldManagerApi.isWorldPointEconomyEnabled() && stats.worldPoint < cost) {
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "gui.creation.insufficient",
                                        mapOf("shortage" to (cost - stats.worldPoint))
                                )
                        )
                        plugin.soundManager.playActionSound(
                                player,
                                "environment",
                                "insufficient_points"
                        )
                        return
                }

                try {
                        BiomeResolver.match(biomeId) ?: throw IllegalArgumentException()
                        worldData.fixedBiome = biomeId.uppercase()
                        worldData.partialBiomes.clear()
                        chargeWorldPoints(stats, worldData, cost)

                        plugin.playerStatsRepository.save(stats)
                        plugin.worldConfigRepository.save(worldData)

                        removeFromInventory(player, confirmItem)

                        val biomeName = lang.getMessage(player, "biomes.${biomeId.lowercase()}")
                        player.sendMessage(
                                lang.getMessage(
                                        player,
                                        "messages.env_biome_changed",
                                        mapOf("biome" to biomeName)
                                )
                        )
                        sendEnvironmentCostPaid(player, cost, stats.worldPoint)
                        plugin.soundManager.playActionSound(player, "environment", "biome_change")
                        applyBiomeToWorld(worldData)
                        plugin.environmentGui.open(player, worldData)
                } catch (e: Exception) {
                        player.sendMessage("ﾂｧcInvalid biome data.")
                }
        }

        private fun removeFromInventory(
                player: Player,
                templateItem: org.bukkit.inventory.ItemStack
        ) {
                val inv = player.inventory
                for (i in 0 until inv.size) {
                        val item = inv.getItem(i)
                        if (item != null && item.isSimilar(templateItem)) {
                                item.amount -= 1
                                return
                        }
                }
        }

        private fun applyBiomeToWorld(worldData: WorldData) {
                val world = Bukkit.getWorld("my_world.${worldData.uuid}") ?: return
                val biomeStr = worldData.fixedBiome ?: return
                val biome =
                        try {
                                BiomeResolver.match(biomeStr) ?: throw IllegalArgumentException()
                        } catch (e: Exception) {
                                return
                        }

                val center = worldData.borderCenterPos ?: world.spawnLocation
                val expansion = worldData.borderExpansionLevel
                val initialSize = plugin.config.getDouble(expansionInitialSizeConfigKey, 100.0)
                // Adjust for special level or calculate size
                val size =
                        if (expansion == WorldData.EXPANSION_LEVEL_SPECIAL) 60000000.0
                        else initialSize * Math.pow(2.0, expansion.toDouble())
                val radius = size / 2.0
                val applyRadius = radius + 160.0

                val minX = (center.x - applyRadius).toInt()
                val maxX = (center.x + applyRadius).toInt()
                val minZ = (center.z - applyRadius).toInt()
                val maxZ = (center.z + applyRadius).toInt()

                // Iterate loaded chunks instead of all blocks directly to avoid loading unloaded
                // chunks if
                // possible,
                // or iterate chunks in range.
                // Task says "Change ... to border + 160 blocks range".
                // Efficient way: iterate loaded chunks, and for each column check if in range.
                // If we want to ensure *all* blocks in range are changed (even unloaded), we should
                // iterate
                // strict range.
                // However, iterating 60million blocks is bad.
                // But for a typical MyWorld with expansion 0-3, it's small.
                // Let's assume we only update loaded chunks because unloaded ones will be handled
                // by
                // ChunkLoadEvent.

                world.loadedChunks.forEach { chunk ->
                        val chunkX = chunk.x * 16
                        val chunkZ = chunk.z * 16

                        // Optimization: check if chunk overlaps with range
                        if (chunkX + 15 < minX ||
                                        chunkX > maxX ||
                                        chunkZ + 15 < minZ ||
                                        chunkZ > maxZ
                        ) {
                                return@forEach
                        }

                        for (x in 0..15) {
                                val worldX = chunkX + x
                                for (z in 0..15) {
                                        val worldZ = chunkZ + z

                                        if (worldX in minX..maxX && worldZ in minZ..maxZ) {
                                                for (y in
                                                        world.minHeight until
                                                                world.maxHeight step
                                                                16) {
                                                        world.setBiome(worldX, y, worldZ, biome)
                                                }
                                        }
                                }
                        }
                        // Update chunk for clients
                        world.refreshChunk(chunk.x, chunk.z)
                }
        }

        private fun showBorderPreview(player: Player, worldData: WorldData, direction: BlockFace) {
                var world = Bukkit.getWorld(worldData.uuid)

                if (world == null) {
                    val folderName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
                    world = Bukkit.getWorld(folderName)
                }

                if (world == null) {
                    return
                }

                val currentSize = world.worldBorder.size
                val nextSize = currentSize * 2 // performExpansionと同じロジック

                val currentCenter = world.worldBorder.center
                val radius = currentSize / 2.0 // performExpansionと同じロジック
                var shiftX = 0.0
                var shiftZ = 0.0

                when (direction) {
                        BlockFace.NORTH_WEST -> {
                                shiftX = -radius
                                shiftZ = -radius
                        }
                        BlockFace.NORTH_EAST -> {
                                shiftX = radius
                                shiftZ = -radius
                        }
                        BlockFace.SOUTH_WEST -> {
                                shiftX = -radius
                                shiftZ = radius
                        }
                        BlockFace.SOUTH_EAST -> {
                                shiftX = radius
                                shiftZ = radius
                        }
                        else -> {}
                }
                val newCenter = currentCenter.clone().add(shiftX, 0.0, shiftZ)
                newCenter.x = Math.round(newCenter.x).toDouble()
                newCenter.z = Math.round(newCenter.z).toDouble()

                val border = Bukkit.createWorldBorder()
                border.center = newCenter
                border.size = nextSize
                player.worldBorder = border

                player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f)
        }

        private fun clearBorderPreview(player: Player) {
                player.worldBorder = null
        }

        private fun sendExpansionConfirmMessage(player: Player) {
                val lang = plugin.languageManager
                val confirmText = lang.getMessage(player, "messages.expand_confirm_chat")
                val confirmBtn = lang.getMessage(player, "messages.expand_confirm_button")
                val confirmHover = lang.getMessage(player, "messages.expand_confirm_hover")
                val retryBtn = lang.getMessage(player, "messages.expand_retry_button")
                val retryHover = lang.getMessage(player, "messages.expand_retry_hover")
                val confirmCommand = plugin.internalCommandTokenManager.buildCommand(player, "expand_confirm")
                val retryCommand = plugin.internalCommandTokenManager.buildCommand(player, "expand_retry")

                val message = net.kyori.adventure.text.Component.text()
                        .append(net.kyori.adventure.text.Component.newline())
                        .append(net.kyori.adventure.text.Component.text(confirmText))
                        .append(net.kyori.adventure.text.Component.newline())
                        .append(net.kyori.adventure.text.Component.newline())
                        .append(net.kyori.adventure.text.Component.text(confirmBtn)
                                .hoverEvent(net.kyori.adventure.text.Component.text(confirmHover))
                                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(confirmCommand)))
                        .append(net.kyori.adventure.text.Component.text("   "))
                        .append(net.kyori.adventure.text.Component.text(retryBtn)
                                .hoverEvent(net.kyori.adventure.text.Component.text(retryHover))
                                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(retryCommand)))
                        .append(net.kyori.adventure.text.Component.newline())
                        .build()

                player.sendMessage(message)
        }

        @EventHandler
        fun onCommandPreprocess(event: PlayerCommandPreprocessEvent) {
                val player = event.player
                val message = event.message
                if (!message.startsWith("/mwm_internal ")) return

                val args = message.substring("/mwm_internal ".length).trim().split(Regex("\\s+"))
                if (args.isEmpty() || args[0].isBlank()) return

                event.isCancelled = true
                val lang = plugin.languageManager
                if (args.size < 2) {
                        return
                }

                val action = args[0]
                val token = args[1]
                val payloadArgs = args.drop(2)

                if (!plugin.internalCommandTokenManager.consume(player, action, token, payloadArgs)) {
                        return
                }

                when (action) {
                        "expand_confirm" -> {
                                plugin.soundManager.playChatClickSound(player)
                                val session = plugin.settingsSessionManager.getSession(player) ?: return
                                if (session.action == SettingsAction.EXPAND_DIRECTION_CONFIRM) {
                                        val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: return
                                        val direction = session.expansionDirection
                                        val cost = session.expansionCost
                                        if (expansionExecutionMode(session) ==
                                                        ExpansionExecutionMode.IMMEDIATE_NO_COST
                                        ) {
                                                clearBorderPreview(player)
                                                executeExpansionByApiMode(player, worldData, direction)
                                                return
                                        }
                                        openExpandConfirmationByPreference(
                                                player,
                                                worldData.uuid,
                                                direction,
                                                cost
                                        )
                                }
                        }
                        "expand_retry" -> {
                                plugin.soundManager.playChatClickSound(player)
                                val session = plugin.settingsSessionManager.getSession(player) ?: return
                                if (session.action == SettingsAction.EXPAND_DIRECTION_CONFIRM) {
                                        clearBorderPreview(player)
                                        session.action = SettingsAction.EXPAND_DIRECTION_WAIT
                                        startBorderDirectionPreview(player)
                                        val promptKey =
                                                if (plugin.playerPlatformResolver.isBedrock(player)) {
                                                        "messages.expand_direction_prompt"
                                                } else {
                                                        "messages.expand_direction_prompt"
                                                }
                                        player.sendMessage(lang.getMessage(player, promptKey))
                                }
                        }
                        "mspt-sort" -> {
                                if (!player.isOp) return
                                plugin.soundManager.playChatClickSound(player)
                                val session = plugin.adminGuiSessionManager.getSession(player.uniqueId)
                                session.sortBy = me.awabi2048.myworldmanager.session.AdminSortType.MSPT_DESC
                                plugin.worldGui.open(player, 0, false, true)
                        }
                        "inviteaccept" -> {
                                plugin.soundManager.playChatClickSound(player)
                                plugin.inviteCommand.handleAccept(player)
                        }
                        "memberinviteaccept" -> {
                                plugin.soundManager.playChatClickSound(player)
                                val inviteId =
                                        payloadArgs.firstOrNull()
                                                ?.takeIf { it != "0" }
                                                ?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
                                val invite = plugin.memberInviteManager.getInvite(player.uniqueId, inviteId)
                                if (invite == null) {
                                        player.sendMessage(lang.getMessage(player, "error.invite_expired"))
                                        return
                                }

                                val worldData = plugin.worldConfigRepository.findByUuid(invite.worldUuid)
                                if (worldData == null) {
                                        player.sendMessage(lang.getMessage(player, "error.invite_world_not_found"))
                                        plugin.memberInviteManager.removeInvite(invite.id)
                                        return
                                }

                                val senderName = PlayerNameUtil.getNameOrDefault(invite.senderUuid, "Unknown")
                                plugin.settingsSessionManager.updateSessionAction(
                                        player,
                                        invite.worldUuid,
                                        SettingsAction.MEMBER_INVITE,
                                        isGui = false
                                )
                                val title = Component.text(lang.getMessage(player, "gui.member_invite_accept_confirm.title"))
                                val center = me.awabi2048.myworldmanager.util.GuiItemFactory.playerHead(
                                        Bukkit.getOfflinePlayer(invite.senderUuid),
                                        Component.text(senderName),
                                        GuiLoreSpec.Rich(
                                                lang.getMessageList(
                                                        player,
                                                        "gui.member_invite_accept_confirm.lore",
                                                        mapOf("world" to worldData.name, "player" to senderName)
                                                ).map(GuiLoreLine::Text),
                                                GuiLoreFrame.BOTH
                                        ),
                                        me.awabi2048.myworldmanager.util.ItemTag.TYPE_GUI_INFO
                                )
                                val confirmItem = me.awabi2048.myworldmanager.util.GuiItemFactory.item(
                                        Material.LIME_CONCRETE,
                                        lang.getMessage(player, "gui.member_invite_accept_confirm.confirm"),
                                        GuiLoreSpec.None,
                                        me.awabi2048.myworldmanager.util.ItemTag.TYPE_GUI_CONFIRM
                                )
                                val cancelItem = me.awabi2048.myworldmanager.util.GuiItemFactory.item(
                                        Material.RED_CONCRETE,
                                        lang.getMessage(player, "gui.member_invite_accept_confirm.cancel"),
                                        GuiLoreSpec.None,
                                        me.awabi2048.myworldmanager.util.ItemTag.TYPE_GUI_CANCEL
                                )
                                plugin.confirmationMenuGui.open(
                                        player = player,
                                        menuId = "member_invite_accept",
                                        title = title,
                                        centerItem = center,
                                        confirmItem = confirmItem,
                                        cancelItem = cancelItem,
                                        onConfirm = {
                                                plugin.memberInviteManager.handleMemberInviteAccept(player, invite.id)
                                                MenuActionResult.Success(MenuUpdate.Close)
                                        },
                                        onCancel = { MenuActionResult.Success(MenuUpdate.Back) }
                                )
                        }
                        "memberrequest" -> {
                                if (payloadArgs.size < 2) return
                                val key = payloadArgs[0]
                                val requestAction = payloadArgs[1]
                                plugin.memberRequestManager.handleInternalCommand(player, key, requestAction)
                        }
                        "tourstart" -> {
                            plugin.soundManager.playChatClickSound(player)
                            if (payloadArgs.size < 2) return
                            val worldUuid = runCatching { UUID.fromString(payloadArgs[0]) }.getOrNull() ?: return
                            val tourUuid = runCatching { UUID.fromString(payloadArgs[1]) }.getOrNull() ?: return
                            val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return
                            val tour = plugin.tourManager.getTour(worldData, tourUuid) ?: return
                            when (plugin.tourManager.startTour(player, worldData, tour)) {
                                me.awabi2048.myworldmanager.service.TourManager.StartTourResult.STARTED -> CCSystem.getAPI().getMenuRuntimeService().close(player)
                                me.awabi2048.myworldmanager.service.TourManager.StartTourResult.INVALID_TOUR ->
                                    player.sendMessage(lang.getMessage(player, "messages.tour.none_available"))
                                me.awabi2048.myworldmanager.service.TourManager.StartTourResult.WRONG_WORLD ->
                                    player.sendMessage(lang.getMessage(player, "messages.no_in_myworld"))
                            }
                        }
                }
        }





        private fun showWorldInfoDialog(player: Player, worldData: WorldData) {
            val lang = plugin.languageManager
            CCSystem.getAPI().getMenuDialogService().show(
                player,
                MenuDialogRequest(
                    owner = "myworldmanager",
                    id = "settings-world-info",
                    title = Component.text(
                        lang.getMessage(player, "gui.bedrock.input.info_form.title"),
                        NamedTextColor.YELLOW,
                    ),
                    body = listOf(Component.text(lang.getMessage(player, "gui.settings.info.dialog.body"))),
                    inputs = listOf(
                        MenuDialogInput.Text(
                            "world_name",
                            Component.text(lang.getMessage(player, "gui.bedrock.input.rename.label")),
                            worldData.name.take(16),
                            maxLength = 16,
                        ),
                        MenuDialogInput.Text(
                            "world_desc",
                            Component.text(lang.getMessage(player, "gui.bedrock.input.description.label")),
                            worldData.description.take(100),
                            maxLength = 100,
                        ),
                    ),
                    confirm = MenuDialogButton(
                        Component.text(lang.getMessage(player, "gui.common.confirm"), NamedTextColor.GREEN),
                        MenuDialogHandler { target, response ->
                            applyWorldInfoUpdate(
                                target,
                                worldData,
                                response.textValue("world_name").trim(),
                                response.textValue("world_desc").trim(),
                            )
                            MenuActionResult.Success(MenuUpdate.None)
                        },
                    ),
                    cancel = MenuDialogButton(
                        Component.text(lang.getMessage(player, "gui.common.cancel"), NamedTextColor.GRAY),
                        MenuDialogHandler { target, _ ->
                            MenuActionResult.Success(MenuUpdate.Resume)
                        },
                    ),
                ),
            )
        }

        private fun showMemberInviteDialog(
                player: Player,
                forceAddMode: Boolean
        ) {
            val lang = plugin.languageManager
            val inviteInputMessageKey =
                if (forceAddMode) "messages.member_force_add_input" else "messages.member_invite_input"
            CCSystem.getAPI().getMenuDialogService().show(
                player,
                MenuDialogRequest(
                    owner = "myworldmanager",
                    id = "settings-member-invite",
                    title = Component.text(
                        lang.getMessage(player, "gui.member_management.invite.name"),
                        NamedTextColor.YELLOW,
                    ),
                    body = listOf(Component.text(lang.getMessage(player, inviteInputMessageKey))),
                    inputs = listOf(
                        MenuDialogInput.Text(
                            "member_invite_target",
                            Component.text(lang.getMessage(player, "gui.bedrock.input.member_invite.label")),
                            maxLength = 32,
                        ),
                    ),
                    confirm = MenuDialogButton(
                        Component.text(lang.getMessage(player, "gui.common.confirm"), NamedTextColor.GREEN),
                        MenuDialogHandler { target, response ->
                            val session = plugin.settingsSessionManager.getSession(target)
                                ?: return@MenuDialogHandler MenuActionResult.Rejected(
                                    lang.getComponent(target, "messages.member_invite_input"),
                                )
                            val currentWorld = plugin.worldConfigRepository.findByUuid(session.worldUuid)
                                ?: return@MenuDialogHandler MenuActionResult.Rejected(
                                    lang.getComponent(target, "general.world_not_found"),
                                )
                            applyMemberInvite(
                                target,
                                currentWorld,
                                response.textValue("member_invite_target").trim(),
                                forceAddMode,
                                reopenAfter = false,
                            )
                            MenuActionResult.Success(MenuUpdate.Resume)
                        },
                    ),
                    cancel = MenuDialogButton(
                        Component.text(lang.getMessage(player, "gui.common.cancel"), NamedTextColor.RED),
                        MenuDialogHandler { _, _ ->
                            MenuActionResult.Success(MenuUpdate.Resume)
                        },
                    ),
                ),
            )
        }

        private fun showTagEditorDialog(player: Player, worldData: WorldData) {
                val lang = plugin.languageManager
                val currentTags = worldData.tags
                val allTags = plugin.worldTagManager.getEditableTagIds(currentTags)

                val inputs = allTags.map { tagId ->
                        val tagName = plugin.worldTagManager.getDisplayName(player, tagId)
                        val isSelected = currentTags.contains(tagId)
                        MenuDialogInput.BooleanInput("tag_$tagId", Component.text(tagName), isSelected)
                }

                CCSystem.getAPI().getMenuDialogService().show(
                    player,
                    MenuDialogRequest(
                        owner = "myworldmanager",
                        id = "settings-tags",
                        title = Component.text("タグ設定", NamedTextColor.YELLOW),
                        body = listOf(
                            Component.text("ワールドのタグを設定します。\n有効にするタグのスイッチをオンにしてください。"),
                        ),
                        inputs = inputs,
                        confirm = MenuDialogButton(
                            Component.text("Submit", NamedTextColor.GREEN),
                            MenuDialogHandler { target, response ->
                                worldData.tags.clear()
                                worldData.tags.addAll(
                                    allTags.filter { tagId -> response.booleanValue("tag_$tagId") },
                                )
                                plugin.worldConfigRepository.save(worldData)
                                MenuActionResult.Success(MenuUpdate.Resume)
                            },
                        ),
                        cancel = MenuDialogButton(
                            Component.text("Close", NamedTextColor.GRAY),
                            MenuDialogHandler { target, _ ->
                                MenuActionResult.Success(MenuUpdate.Resume)
                            },
                        ),
                    ),
                )
        }

        private fun openExpandConfirmationByPreference(
                player: Player,
                worldUuid: UUID,
                direction: org.bukkit.block.BlockFace?,
                cost: Int
        ) {
                val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return

                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldUuid,
                        SettingsAction.EXPAND_CONFIRM,
                        isGui = true
                )
                plugin.settingsSessionManager.getSession(player)?.let {
                        it.expansionDirection = direction
                        it.expansionCost = cost
                }

                CCSystem.getAPI().getMenuRuntimeService().navigate(
                        player,
                        plugin.worldSettingsGui.expansionConfirmationRoute(worldData.uuid, direction, cost),
                )
        }

        private fun openExpansionStepBackConfirmationByPreference(
                player: Player,
                worldData: WorldData
        ) {
                val lang = plugin.languageManager
                val record = worldData.latestBorderExpansionRecord()
                if (record == null) {
                        player.sendMessage(
                                lang.getMessage(player, "messages.expansion_step_back_unavailable")
                        )
                        plugin.worldSettingsGui.openExpansionMethodSelection(player, worldData)
                        return
                }

                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.STEP_BACK_EXPANSION_CONFIRM,
                        isGui = true
                )

                CCSystem.getAPI().getMenuRuntimeService().navigate(
                        player,
                        plugin.worldSettingsGui.runtimeRoute(
                                WorldSettingsRuntimeScreen.EXPANSION_STEP_BACK_CONFIRM,
                                worldData.uuid,
                        ),
                )
        }

        // Helper to show generic simple confirmation
        fun showEnvironmentConfirmDialog(
            player: Player,
            worldData: WorldData,
            type: String,
            cost: Int,
        ) {
            val lang = plugin.languageManager

            val titleKey = "gui.environment.$type.display" // e.g. gui.environment.gravity.display
            val title = Component.text(lang.getMessage(player, titleKey), NamedTextColor.YELLOW)

            val centerMaterial = when (type) {
                "gravity" -> Material.FEATHER
                "weather" -> Material.WHITE_WOOL
                "biome" -> Material.GRASS_BLOCK
                else -> Material.PAPER
            }
            val centerItem = me.awabi2048.myworldmanager.util.GuiItemFactory.item(
                centerMaterial,
                title,
                GuiLoreSpec.Rich(
                    buildList {
                        add(GuiLoreLine.Text(lang.getMessage(player, "gui.common.confirm_action")))
                        if (MyWorldManagerApi.isWorldPointEconomyEnabled()) {
                            add(GuiLoreLine.Data(lang.getMessage(player, "gui.settings.expand.blocks.cost"), cost, "§e"))
                        }
                    },
                    GuiLoreFrame.BOTH
                ),
                me.awabi2048.myworldmanager.util.ItemTag.TYPE_GUI_INFO
            )
            val confirmItem = me.awabi2048.myworldmanager.util.GuiItemFactory.item(
                Material.LIME_CONCRETE,
                lang.getMessage(player, "gui.common.confirm"),
                GuiLoreSpec.None,
                me.awabi2048.myworldmanager.util.ItemTag.TYPE_GUI_CONFIRM
            )
            val cancelItem = me.awabi2048.myworldmanager.util.GuiItemFactory.item(
                Material.RED_CONCRETE,
                lang.getMessage(player, "gui.common.cancel"),
                GuiLoreSpec.None,
                me.awabi2048.myworldmanager.util.ItemTag.TYPE_GUI_CANCEL
            )

            plugin.confirmationMenuGui.open(
                player = player,
                menuId = "environment_confirm",
                title = title,
                centerItem = centerItem,
                confirmItem = confirmItem,
                cancelItem = cancelItem,
                onConfirm = {
                    val session = plugin.settingsSessionManager.getSession(player)
                        ?: return@open MenuActionResult.Rejected()
                    when (type) {
                        "gravity" -> handleEnvGravityConfirm(player, worldData)
                        "weather" -> handleWeatherConfirm(player, worldData)
                        "biome" -> {
                            val biomeId = session.tempBiomeId
                                ?: return@open MenuActionResult.Rejected()
                            handleEnvBiomeConfirm(player, worldData, biomeId)
                        }
                    }
                    MenuActionResult.Success(
                        MenuUpdate.Navigate(plugin.environmentGui.prepareOpen(player, worldData)),
                    )
                },
                onCancel = { MenuActionResult.Success(MenuUpdate.Back) }
            )
        }

        private fun showVisitorKickConfirmDialog(player: Player, targetName: String, targetUuid: UUID) {
            val worldData = plugin.worldConfigRepository.findByUuid(plugin.settingsSessionManager.getSession(player)?.worldUuid ?: return) ?: return
            plugin.worldSettingsGui.openVisitorKickConfirmation(player, worldData, targetUuid)
        }


        private fun handleEnvGravityConfirm(player: Player, worldData: WorldData) {
                val cost = WorldRuntimePolicies.environmentCost(plugin.config, "gravity")
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)

                if (MyWorldManagerApi.isWorldPointEconomyEnabled() && stats.worldPoint < cost) {
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "gui.creation.insufficient",
                                        mapOf("shortage" to (cost - stats.worldPoint))
                                )
                        )
                        plugin.soundManager.playActionSound(player, "environment", "insufficient_points")
                        return
                }

                worldData.gravityValue = 0.02
                chargeWorldPoints(stats, worldData, cost)

                plugin.playerStatsRepository.save(stats)
                plugin.worldConfigRepository.save(worldData)

                player.sendMessage(
                        plugin.languageManager.getMessage(
                                player,
                                "messages.env_gravity_changed",
                                mapOf("gravity" to "Moon", "multiplier" to "0.17")
                        )
                )
                sendEnvironmentCostPaid(player, cost, stats.worldPoint)
                plugin.soundManager.playActionSound(player, "environment", "gravity_change")
                plugin.worldEnvironmentService.applyAttributes(worldData.uuid)
        }

        private fun handleWeatherConfirm(player: Player, worldData: WorldData) {
                val session = plugin.settingsSessionManager.getSession(player) ?: return
                val nextWeather = session.tempWeather ?: return
                val cost = WorldRuntimePolicies.environmentCost(plugin.config, "weather")
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)

                if (MyWorldManagerApi.isWorldPointEconomyEnabled() && stats.worldPoint < cost) {
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "gui.creation.insufficient",
                                        mapOf("shortage" to (cost - stats.worldPoint))
                                )
                        )
                        plugin.soundManager.playActionSound(player, "environment", "insufficient_points")
                        return
                }

                chargeWorldPoints(stats, worldData, cost)
                worldData.fixedWeather = if (nextWeather == "DEFAULT") null else nextWeather
                session.tempWeather = null

                plugin.playerStatsRepository.save(stats)
                plugin.worldConfigRepository.save(worldData)

                sendEnvironmentCostPaid(player, cost, stats.worldPoint)
                plugin.worldEnvironmentService.applyWeather(worldData.uuid)
                plugin.soundManager.playActionSound(player, "environment", "weather_change")
        }

        private fun handleEnvBiomeConfirm(player: Player, worldData: WorldData, biomeId: String) {
                val lang = plugin.languageManager
                val cost = WorldRuntimePolicies.environmentCost(plugin.config, "biome")
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)

                if (MyWorldManagerApi.isWorldPointEconomyEnabled() && stats.worldPoint < cost) {
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "gui.creation.insufficient",
                                        mapOf("shortage" to (cost - stats.worldPoint))
                                )
                        )
                        plugin.soundManager.playActionSound(player, "environment", "insufficient_points")
                        return
                }

                try {
                        BiomeResolver.match(biomeId) ?: throw IllegalArgumentException()
                        worldData.fixedBiome = biomeId.uppercase()
                        worldData.partialBiomes.clear()
                        chargeWorldPoints(stats, worldData, cost)

                        plugin.playerStatsRepository.save(stats)
                        plugin.worldConfigRepository.save(worldData)

                        val biomeName = lang.getMessage(player, "biomes.${biomeId.lowercase()}")
                        player.sendMessage(lang.getMessage(player, "messages.env_biome_changed", mapOf("biome" to biomeName)))
                        sendEnvironmentCostPaid(player, cost, stats.worldPoint)
                        plugin.soundManager.playActionSound(player, "environment", "biome_change")
                        applyBiomeToWorld(worldData)

                } catch (e: Exception) {
                        player.sendMessage("§cInvalid Biome: $biomeId")
                }
        }

        private fun handleExpandConfirm(player: Player, worldData: WorldData) {
                val session = plugin.settingsSessionManager.getSession(player) ?: return
                val cost = session.expansionCost
                val direction = session.expansionDirection
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)

                if (MyWorldManagerApi.isWorldPointEconomyEnabled() && stats.worldPoint < cost) {
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "gui.creation.insufficient",
                                        mapOf("shortage" to (cost - stats.worldPoint))
                                )
                        )
                        plugin.soundManager.playActionSound(player, "environment", "insufficient_points")
                        return
                }

                if (performExpansion(worldData, direction)) {
                        chargeWorldPoints(stats, worldData, cost)
                        plugin.playerStatsRepository.save(stats)
                        plugin.worldConfigRepository.save(worldData)

                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "messages.expand_complete",
                                        mapOf(
                                                "level_before" to (worldData.borderExpansionLevel - 1),
                                                "level_after" to worldData.borderExpansionLevel
                                        )
                                )
                        )
                        plugin.soundManager.playActionSound(player, "creation", "wizard_next")
                } else {
                        player.sendMessage(plugin.languageManager.getMessage("error.expand_failed"))
                }
                if (!CCSystem.getAPI().getMenuRuntimeService().resumeFromExternal(player)) {
                        plugin.worldSettingsGui.open(player, worldData)
                }
        }

        private fun resolveWorld(worldData: WorldData): World? {
                val worldName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
                return Bukkit.getWorld(worldName)
        }

        private fun worldDataResetTarget(worldData: WorldData): Pair<Location, Double>? {
                val world = resolveWorld(worldData) ?: return null
                val center = world.spawnLocation.clone()
                val size = plugin.config.getDouble(expansionInitialSizeConfigKey, 100.0)
                return center to size
        }

        private fun worldDataStepBackTarget(worldData: WorldData): Pair<Location, Double>? {
                val record = worldData.latestBorderExpansionRecord() ?: return null
                val world = resolveWorld(worldData) ?: return null
                return Location(world, record.oldCenterX, world.spawnLocation.y, record.oldCenterZ) to record.oldSize
        }

        private fun appendSpawnAdjustmentWarning(
                player: Player,
                worldData: WorldData,
                target: Pair<Location, Double>?
        ): List<String>? {
                if (target == null) return null
                val (center, size) = target
                val world = center.world ?: return null
                if (!borderResetSpawnService.preview(world, worldData, center, size).hasChanges) {
                        return null
                }
                return plugin.languageManager.getMessageList(
                        player,
                        "gui.confirm.spawn_adjustment_warning"
                )
        }

        private fun teleportPlayersOutsideBorder(world: World, targetLocation: Location) {
                val worldBorder = world.worldBorder
                val safeTarget = targetLocation.clone()
                world.players
                        .filter { !worldBorder.isInside(it.location) }
                        .forEach { it.teleport(safeTarget) }
        }

        private fun executeExpansionStepBack(
                player: Player,
                worldData: WorldData,
                closeInventory: Boolean
        ) {
                val record = worldData.latestBorderExpansionRecord()
                if (record == null) {
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "messages.expansion_step_back_unavailable"
                                )
                        )
                        plugin.worldSettingsGui.openExpansionMethodSelection(player, worldData)
                        return
                }

                val world = resolveWorld(worldData)
                if (world == null) {
                        player.sendMessage(plugin.languageManager.getMessage(player, "messages.world_not_found"))
                        return
                }

                val oldCenter = Location(world, record.oldCenterX, world.spawnLocation.y, record.oldCenterZ)
                world.worldBorder.setCenter(oldCenter)
                world.worldBorder.setSize(record.oldSize)
                worldData.borderCenterPos = oldCenter
                worldData.borderExpansionLevel = record.levelBefore
                val removedCost = WorldRuntimePolicies.expansionCost(plugin.config, record.levelAfter)
                if (MyWorldManagerApi.isWorldPointEconomyEnabled()) {
                        worldData.cumulativePoints = (worldData.cumulativePoints - removedCost).coerceAtLeast(0)
                }
                val recordIndex = worldData.borderExpansionHistory.indexOfLast { it == record }
                if (recordIndex >= 0) {
                        worldData.borderExpansionHistory.removeAt(recordIndex)
                }

                borderResetSpawnService.apply(world, worldData)
                teleportPlayersOutsideBorder(world, oldCenter)
                plugin.worldConfigRepository.save(worldData)

                player.sendMessage(
                        plugin.languageManager.getMessage(
                                player,
                                "messages.expansion_step_back_success",
                                mapOf(
                                        "level_before" to record.levelAfter,
                                        "level_after" to record.levelBefore
                                )
                        )
                )

                if (closeInventory) {
                        CCSystem.getAPI().getMenuRuntimeService().close(player)
                        plugin.settingsSessionManager.endSession(player)
                } else {
                        plugin.worldSettingsGui.openExpansionMethodSelection(player, worldData)
                }
        }

        private fun executeExpansionReset(
                player: Player,
                worldData: WorldData,
                closeInventory: Boolean
        ) {
                val totalExpCost = calculateTotalExpansionCost(worldData.borderExpansionLevel)
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                val world = resolveWorld(worldData)

                if (world != null) {
                        val initialSize = plugin.config.getDouble(expansionInitialSizeConfigKey, 100.0)
                        val spawnLocation = world.spawnLocation.clone()
                        world.worldBorder.size = initialSize
                        world.worldBorder.center = spawnLocation
                        worldData.borderCenterPos = spawnLocation
                        borderResetSpawnService.apply(world, worldData)
                        teleportPlayersOutsideBorder(world, spawnLocation)
                }

                val refundRate = plugin.config.getDouble("critical_settings.refund_percentage", 0.5)
                val refund = if (MyWorldManagerApi.isWorldPointEconomyEnabled()) {
                        (totalExpCost * refundRate).toInt()
                } else {
                        0
                }

                if (MyWorldManagerApi.isWorldPointEconomyEnabled()) {
                        stats.worldPoint += refund
                        worldData.cumulativePoints = (worldData.cumulativePoints - totalExpCost).coerceAtLeast(0)
                }
                worldData.borderExpansionLevel = 0
                worldData.borderExpansionHistory.clear()

                plugin.playerStatsRepository.save(stats)
                plugin.worldConfigRepository.save(worldData)

                player.sendMessage(
                        plugin.languageManager.getMessage(
                                player,
                                if (MyWorldManagerApi.isWorldPointEconomyEnabled()) {
                                        "messages.expansion_reset_success"
                                } else {
                                        "messages.expansion_reset_success_without_points"
                                },
                                mapOf("points" to refund)
                        )
                )

                if (closeInventory) {
                        CCSystem.getAPI().getMenuRuntimeService().close(player)
                        plugin.settingsSessionManager.endSession(player)
                } else {
                        plugin.worldSettingsGui.open(player, worldData)
                }
        }

        private fun handleResetExpansionConfirm(player: Player, worldData: WorldData) {
                val world = resolveWorld(worldData)
                if (world != null && !isSpawnAreaPlaceable(world.spawnLocation)) {
                        val title = LegacyComponentSerializer.legacySection().deserialize(
                                plugin.languageManager.getMessage(
                                        player,
                                        "gui.confirm.reset_expansion_spawn_unsafe.title"
                                )
                        )
                        val bodyTextLines = plugin.languageManager
                                .getMessageList(player, "gui.confirm.reset_expansion_spawn_unsafe.lore")
                                .toMutableList()
                        if (worldData.hasModifiedBorderExpansion()) {
                                bodyTextLines.addAll(
                                        plugin.languageManager.getMessageList(
                                                player,
                                                "gui.confirm.reset_expansion.modified_warning"
                                        )
                                )
                        }
                        appendSpawnAdjustmentWarning(
                                player,
                                worldData,
                                worldDataResetTarget(worldData)
                        )?.let(bodyTextLines::addAll)
                        val bodyLines = bodyTextLines
                                .map { LegacyComponentSerializer.legacySection().deserialize(it) }
                        plugin.settingsSessionManager.updateSessionAction(
                                player,
                                worldData.uuid,
                                SettingsAction.RESET_EXPANSION_CONFIRM_SPAWN_UNSAFE,
                                isGui = true
                        )
                        DialogConfirmManager.showSimpleConfirmationDialog(
                                player,
                                plugin,
                                title,
                                bodyLines,
                                plugin.languageManager.getMessage(player, "gui.common.confirm"),
                                plugin.languageManager.getMessage(player, "gui.common.cancel"),
                                onConfirm = {
                                        executeExpansionReset(player, worldData, closeInventory = false)
                                        plugin.soundManager.playActionSound(player, "environment", "gravity_change")
                                        MenuActionResult.Success(MenuUpdate.Close)
                                },
                                onCancel = { MenuActionResult.Success(MenuUpdate.Back) }
                        )
                        return
                }
                executeExpansionReset(player, worldData, closeInventory = false)
                plugin.soundManager.playActionSound(player, "environment", "gravity_change")
        }

        private fun handleDeleteWorldConfirm(player: Player, worldData: WorldData) {
                if (!canOwnerExecuteDelete(worldData)) {
                        sendDeleteUnavailableMessage(player)
                        plugin.worldSettingsGui.openCriticalSettings(player, worldData)
                        return
                }
                val refundRate = plugin.config.getDouble("critical_settings.refund_percentage", 0.5)
                val refund = (worldData.cumulativePoints * refundRate).toInt()
                plugin.worldService.deleteWorld(worldData.uuid, player).thenAccept { success ->
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                                if (success) {
                                        player.sendMessage(
                                                plugin.languageManager.getMessage(
                                                        player,
                                                        "messages.world_delete_success",
                                                        mapOf("points" to refund)
                                                )
                                        )
                                        plugin.soundManager.playActionSound(player, "creation", "delete")
                                        plugin.settingsSessionManager.endSession(player)
                                        CCSystem.getAPI().getMenuRuntimeService().close(player)
                                } else {
                                        player.sendMessage(plugin.languageManager.getMessage("error.delete_failed"))
                                        plugin.worldSettingsGui.open(player, worldData)
                                }
                        })
                }
        }

        private fun chargeWorldPoints(
                stats: me.awabi2048.myworldmanager.model.PlayerStats,
                worldData: WorldData,
                cost: Int
        ) {
                if (!MyWorldManagerApi.isWorldPointEconomyEnabled()) return
                stats.worldPoint -= cost
                worldData.cumulativePoints += cost
        }

        private fun sendEnvironmentCostPaid(player: Player, cost: Int, remaining: Int) {
                if (!MyWorldManagerApi.isWorldPointEconomyEnabled()) return
                player.sendMessage(
                        plugin.languageManager.getMessage(
                                player,
                                "messages.env_cost_paid",
                                mapOf(
                                        "cost" to cost,
                                        "remaining_info" to plugin.languageManager.getMessage(
                                                player,
                                                "messages.env_cost_paid_remaining",
                                                mapOf("remaining" to remaining)
                                        )
                                )
                        )
                )
        }

        private fun canOwnerExecuteDelete(worldData: WorldData): Boolean {
                if (!MyWorldManagerApi.isWorldSlotSystemEnabled()) return true
                val ownerStats = plugin.playerStatsRepository.findByUuid(worldData.owner)
                return ownerStats.unlockedWorldSlot > 0
        }

        private fun sendDeleteUnavailableMessage(player: Player) {
                player.sendMessage(
                        plugin.languageManager.getMessage(
                                player,
                                "messages.world_delete_unavailable_slot"
                        )
                )
        }

        private fun handleUnarchiveWorldConfirm(player: Player, worldData: WorldData) {
                CCSystem.getAPI().getMenuRuntimeService().close(player)
                player.sendMessage(
                        plugin.languageManager.getMessage(
                                player,
                                "messages.unarchive_start"
                        )
                )

                plugin.worldService.unarchiveWorld(worldData.uuid)
                        .thenAccept { success: Boolean ->
                                Bukkit.getScheduler().runTask(
                                        plugin,
                                        Runnable {
                                if (success) {
                                                        // クールダウン記録（プレイヤーごと）
                                                        val now = java.time.LocalDateTime.now()
                                                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                                        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                                                        stats.lastArchiveActionAt = now
                                                        plugin.playerStatsRepository.save(stats)
                                                        plugin.worldService.teleportToWorld(player, worldData.uuid) {
                                                                player.sendMessage(
                                                                        plugin.languageManager.getMessage(
                                                                                player,
                                                                                "messages.unarchive_success"
                                                                        )
                                                                )
                                                        }
                                                } else {
                                                        player.sendMessage(
                                                                plugin.languageManager.getMessage(
                                                                        player,
                                                                        "error.unarchive_failed"
                                                                )
                                                        )
                                                }
                                        }
                                )
                        }
                plugin.settingsSessionManager.endSession(player)
        }

        private fun showAdminOwnerResetDialog(player: Player, worldData: WorldData) {
                val lang = plugin.languageManager
                val currentOwner =
                        PlayerNameUtil.getNameOrDefault(
                                worldData.owner,
                                lang.getMessage(player, "general.unknown")
                        )
                CCSystem.getAPI().getMenuDialogService().show(
                    player,
                    MenuDialogRequest(
                        owner = "myworldmanager",
                        id = "settings-admin-owner-reset",
                        title = lang.getComponent(
                            player,
                            "gui.member_management.admin_owner_reset.dialog.title",
                        ),
                        body = listOf(
                            lang.getComponent(
                                player,
                                "gui.member_management.admin_owner_reset.dialog.body",
                                mapOf("world" to worldData.name, "owner" to currentOwner),
                            ),
                        ),
                        inputs = listOf(
                            MenuDialogInput.Text(
                                "new_owner_name",
                                lang.getComponent(
                                    player,
                                    "gui.member_management.admin_owner_reset.dialog.input",
                                ),
                                width = 300,
                            ),
                        ),
                        confirm = MenuDialogButton(
                            lang.getComponent(
                                player,
                                "gui.member_management.admin_owner_reset.dialog.confirm",
                            ),
                            MenuDialogHandler { target, response ->
                                val session = plugin.settingsSessionManager.getSession(target)
                                if (session == null ||
                                    !session.isAdminFlow ||
                                    session.action != SettingsAction.MANAGE_MEMBERS
                                ) {
                                    PermissionManager.sendNoPermissionMessage(target)
                                    return@MenuDialogHandler MenuActionResult.Success(MenuUpdate.Close)
                                }
                                applyAdminOwnerReset(
                                    target,
                                    worldData,
                                    response.textValue("new_owner_name").trim(),
                                )
                                MenuActionResult.Success(MenuUpdate.Close)
                            },
                        ),
                        cancel = MenuDialogButton(
                            lang.getComponent(
                                player,
                                "gui.member_management.admin_owner_reset.dialog.cancel",
                            ),
                            MenuDialogHandler { target, _ ->
                                reopenMemberManagementLatest(target, worldData.uuid)
                                MenuActionResult.Success(MenuUpdate.Close)
                            },
                        ),
                    ),
                )
        }

        private fun applyAdminOwnerReset(
                player: Player,
                worldData: WorldData,
                targetName: String
        ) {
                val lang = plugin.languageManager
                if (targetName.isBlank()) {
                        player.sendMessage(
                                lang.getMessage(
                                        player,
                                        "gui.member_management.admin_owner_reset.error.empty"
                                )
                        )
                        showAdminOwnerResetDialog(player, worldData)
                        return
                }

                val newOwner = PlayerNameUtil.resolveOfflinePlayer(plugin, targetName)
                if (newOwner == null) {
                        player.sendMessage(
                                lang.getMessage(
                                        player,
                                        "gui.member_management.admin_owner_reset.error.not_found",
                                        mapOf("player" to targetName)
                                )
                        )
                        showAdminOwnerResetDialog(player, worldData)
                        return
                }
                if (newOwner.uniqueId == worldData.owner) {
                        player.sendMessage(
                                lang.getMessage(
                                        player,
                                        "gui.member_management.admin_owner_reset.error.same_owner"
                                )
                        )
                        showAdminOwnerResetDialog(player, worldData)
                        return
                }
                if (!WorldCreationChecks.checkLimits(plugin, player, newOwner.uniqueId)) {
                        showAdminOwnerResetDialog(player, worldData)
                        return
                }

                val oldOwnerId = worldData.owner
                val oldOwnerName =
                        PlayerNameUtil.getNameOrDefault(
                                oldOwnerId,
                                lang.getMessage(player, "general.unknown")
                        )
                val newOwnerName =
                        newOwner.name
                                ?: PlayerNameUtil.getNameOrDefault(
                                        newOwner.uniqueId,
                                        lang.getMessage(player, "general.unknown")
                                )

                worldData.owner = newOwner.uniqueId
                if (oldOwnerId != newOwner.uniqueId &&
                                !worldData.moderators.contains(oldOwnerId)
                ) {
                        worldData.moderators.add(oldOwnerId)
                }
                worldData.moderators.remove(newOwner.uniqueId)
                worldData.members.remove(newOwner.uniqueId)
                plugin.worldConfigRepository.save(worldData)

                Bukkit.getPluginManager().callEvent(
                        MwmOwnerTransferredEvent(
                                worldUuid = worldData.uuid,
                                oldOwnerUuid = oldOwnerId,
                                oldOwnerName = oldOwnerName,
                                newOwnerUuid = newOwner.uniqueId,
                                newOwnerName = newOwnerName,
                                transferredByUuid = player.uniqueId,
                                source = MwmOwnerTransferSource.ADMIN_RESET
                        )
                )
                plugin.macroManager.execute(
                        "on_owner_transfer",
                        mapOf(
                                "old_owner" to oldOwnerName,
                                "new_owner" to newOwnerName,
                                "world_uuid" to worldData.uuid.toString()
                        )
                )
                player.sendMessage(
                        lang.getMessage(
                                player,
                                "gui.member_management.admin_owner_reset.success",
                                mapOf("player" to newOwnerName)
                        )
                )
                reopenMemberManagementLatest(player, worldData.uuid)
        }

        private fun handleVisitorKickConfirm(player: Player, worldData: WorldData, visitorUuid: UUID) {
                val visitor = Bukkit.getPlayer(visitorUuid)
                val worldFolderName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
                if (visitor != null && visitor.world.name == worldFolderName) {
                        visitor.teleport(plugin.worldService.getEvacuationLocation())
                        visitor.sendMessage(plugin.languageManager.getMessage(visitor, "messages.kicked"))
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "messages.kicked_success",
                                        mapOf("player" to visitor.name)
                                )
                        )
                plugin.soundManager.playActionSound(player, "world_settings", "kick")
                }
                plugin.worldSettingsGui.openVisitorManagement(player, worldData)
        }

        private fun handleBedrockDialogCancel(player: Player, worldData: WorldData) {
                val session = plugin.settingsSessionManager.getSession(player) ?: return
                when (session.action) {
                        SettingsAction.ENV_CONFIRM -> plugin.environmentGui.open(player, worldData)
                        SettingsAction.EXPAND_CONFIRM -> plugin.worldSettingsGui.openExpansionMethodSelection(player, worldData)
                        SettingsAction.STEP_BACK_EXPANSION_CONFIRM -> plugin.worldSettingsGui.openExpansionMethodSelection(player, worldData)
                        SettingsAction.VISITOR_KICK_CONFIRM -> plugin.worldSettingsGui.openVisitorManagement(player, worldData)
                        SettingsAction.MEMBER_REMOVE_CONFIRM,
                        SettingsAction.MEMBER_TRANSFER_CONFIRM,
                        SettingsAction.MEMBER_PENDING_INVITE_CANCEL_CONFIRM ->
                                reopenMemberManagementLatest(player, worldData.uuid)
                        SettingsAction.RESET_EXPANSION_CONFIRM,
                        SettingsAction.RESET_EXPANSION_CONFIRM_SPAWN_UNSAFE,
                        SettingsAction.DELETE_WORLD_CONFIRM,
                        SettingsAction.DELETE_WORLD_CONFIRM_FINAL,
                        SettingsAction.ARCHIVE_WORLD_FROM_CRITICAL -> plugin.worldSettingsGui.openCriticalSettings(player, worldData)
                        SettingsAction.UNARCHIVE_CONFIRM -> plugin.menuEntryRouter.openPlayerWorld(player)
                        else -> plugin.worldSettingsGui.open(player, worldData)
                }
        }

        private fun handleBedrockDialogAction(player: Player, worldData: WorldData, actionId: String) {
                val keyVal = if (actionId.startsWith("mwm:")) actionId.substringAfter("mwm:") else actionId

                if (keyVal == "confirm/cancel") {
                        handleBedrockDialogCancel(player, worldData)
                        return
                }

                val session = plugin.settingsSessionManager.getSession(player) ?: return

                if (keyVal.startsWith("confirm/env_change/")) {
                        val type = keyVal.substringAfter("confirm/env_change/")
                        when (type) {
                                "gravity" -> handleEnvGravityConfirm(player, worldData)
                                "weather" -> handleWeatherConfirm(player, worldData)
                                "biome" -> {
                                        val biomeId = session.tempBiomeId ?: return
                                        handleEnvBiomeConfirm(player, worldData, biomeId)
                                }
                        }
                        plugin.environmentGui.open(player, worldData)
                        return
                }

                if (keyVal == "confirm/archive_world") {
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "messages.archive_success",
                                        mapOf("world" to worldData.name)
                                )
                        )
                        worldData.isArchived = true
                        plugin.worldConfigRepository.save(worldData)
                        plugin.settingsSessionManager.endSession(player)
                        CCSystem.getAPI().getMenuRuntimeService().close(player)
                        return
                }

                if (keyVal == "confirm/archive_world_critical") {
                        player.sendMessage(plugin.languageManager.getMessage(player, "messages.archive_start"))
                        plugin.worldService.archiveWorld(worldData.uuid)
                                .thenAccept { success: Boolean ->
                                        Bukkit.getScheduler().runTask(plugin, Runnable {
                                                if (success) {
                                                        val now = java.time.LocalDateTime.now()
                                                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                                        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                                                        stats.lastArchiveActionAt = now
                                                        plugin.playerStatsRepository.save(stats)
                                                        player.sendMessage(
                                                                plugin.languageManager.getMessage(
                                                                        player,
                                                                        "messages.archive_success",
                                                                        mapOf("world" to worldData.name)
                                                                )
                                                        )
                                                } else {
                                                        player.sendMessage(plugin.languageManager.getMessage(player, "messages.archive_failed"))
                                                }
                                        })
                                }
                        plugin.settingsSessionManager.endSession(player)
                        return
                }

                if (keyVal == "confirm/expand") {
                        handleExpandConfirm(player, worldData)
                        return
                }

                if (keyVal == "confirm/reset_expansion") {
                        handleResetExpansionConfirm(player, worldData)
                        return
                }

                if (keyVal == "confirm/step_back_expansion") {
                        executeExpansionStepBack(player, worldData, closeInventory = false)
                        return
                }

                if (keyVal == "confirm/delete_world_step1") {
                        if (!canOwnerExecuteDelete(worldData)) {
                                sendDeleteUnavailableMessage(player)
                                plugin.worldSettingsGui.openCriticalSettings(player, worldData)
                                return
                        }
                        plugin.worldSettingsGui.openDeleteWorldConfirmation2(player, worldData)
                        return
                }

                if (keyVal == "confirm/delete_world") {
                        handleDeleteWorldConfirm(player, worldData)
                        return
                }

                if (keyVal == "confirm/unarchive_world") {
                        handleUnarchiveWorldConfirm(player, worldData)
                        return
                }

                if (keyVal.startsWith("confirm/member_remove/")) {
                        val targetUuidStr = keyVal.substringAfter("confirm/member_remove/")
                        val targetUuid = try {
                                UUID.fromString(targetUuidStr)
                        } catch (_: Exception) {
                                return
                        }
                        val memberName = PlayerNameUtil.getNameOrDefault(targetUuid, "Unknown")
                        worldData.members.remove(targetUuid)
                        worldData.moderators.remove(targetUuid)
                        plugin.worldConfigRepository.save(worldData)
                        Bukkit.getPluginManager().callEvent(
                                MwmMemberRemovedEvent(
                                        worldUuid = worldData.uuid,
                                        memberUuid = targetUuid,
                                        memberName = memberName,
                                        removedByUuid = player.uniqueId,
                                        source = MwmMemberRemoveSource.MANUAL
                                )
                        )
                        player.sendMessage(plugin.languageManager.getMessage("messages.member_deleted"))
                        plugin.macroManager.execute(
                                "on_member_remove",
                                mapOf(
                                        "world_uuid" to worldData.uuid.toString(),
                                        "member" to memberName
                                )
                        )
                        reopenMemberManagementLatest(player, worldData.uuid)
                        return
                }

                if (keyVal.startsWith("confirm/member_transfer/")) {
                        val targetUuidStr = keyVal.substringAfter("confirm/member_transfer/")
                        val newOwnerId = try {
                                UUID.fromString(targetUuidStr)
                        } catch (_: Exception) {
                                return
                        }
                        if (!WorldCreationChecks.checkLimits(plugin, player, newOwnerId)) return
                        val oldOwnerId = worldData.owner
                        val oldOwnerName = PlayerNameUtil.getNameOrDefault(oldOwnerId, "Unknown")
                        val newOwnerName = PlayerNameUtil.getNameOrDefault(newOwnerId, "Unknown")
                        worldData.owner = newOwnerId
                        if (!worldData.moderators.contains(oldOwnerId)) {
                                worldData.moderators.add(oldOwnerId)
                        }
                        worldData.moderators.remove(newOwnerId)
                        worldData.members.remove(newOwnerId)
                        plugin.worldConfigRepository.save(worldData)
                        Bukkit.getPluginManager().callEvent(
                                MwmOwnerTransferredEvent(
                                        worldUuid = worldData.uuid,
                                        oldOwnerUuid = oldOwnerId,
                                        oldOwnerName = oldOwnerName,
                                        newOwnerUuid = newOwnerId,
                                        newOwnerName = newOwnerName,
                                        transferredByUuid = player.uniqueId,
                                        source = MwmOwnerTransferSource.MANUAL
                                )
                        )
                        plugin.macroManager.execute(
                                "on_owner_transfer",
                                mapOf(
                                        "world_uuid" to worldData.uuid.toString(),
                                        "old_owner" to oldOwnerName,
                                        "new_owner" to newOwnerName
                                )
                        )
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "messages.owner_transferred",
                                        mapOf("old_owner" to newOwnerName)
                                )
                        )
                        reopenMemberManagementLatest(player, worldData.uuid)
                        return
                }

                if (keyVal.startsWith("confirm/member_pending_invite_cancel/")) {
                        val decisionIdStr =
                                keyVal.substringAfter("confirm/member_pending_invite_cancel/")
                        val decisionId =
                                runCatching { UUID.fromString(decisionIdStr) }.getOrNull() ?: return
                        cancelMemberInviteByDecisionId(player, worldData.uuid, decisionId)
                        return
                }

                if (keyVal.startsWith("confirm/visitor_kick/")) {
                        val targetUuidStr = keyVal.substringAfter("confirm/visitor_kick/")
                        val targetUuid = try {
                                UUID.fromString(targetUuidStr)
                        } catch (_: Exception) {
                                return
                        }
                        handleVisitorKickConfirm(player, worldData, targetUuid)
                }
        }

}
