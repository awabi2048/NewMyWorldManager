package me.awabi2048.myworldmanager.service

import com.awabi2048.ccsystem.api.gui.*
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.extension.ReversibleWorldPublishPolicy
import me.awabi2048.myworldmanager.api.extension.WorldPublishReversibleRestoreResult
import me.awabi2048.myworldmanager.api.extension.WorldPublishReversibleState
import me.awabi2048.myworldmanager.model.TourNavigationMode
import me.awabi2048.myworldmanager.session.WorldCreationSessionSnapshot
import java.util.UUID
import java.time.LocalDate
import com.awabi2048.ccsystem.api.gui.GuiCycle
import com.awabi2048.ccsystem.api.gui.GuiCycleDirection
import me.awabi2048.myworldmanager.session.*

object MwmReversibleContracts {
    const val OWNER = "myworldmanager"
    const val USER_SETTINGS_PROVIDER = "$OWNER:user-settings"
    const val DISPLAY_ORDER_PROVIDER = "$OWNER:world-display-order"
    const val CREATION_SESSION_PROVIDER = "$OWNER:creation-session"
    const val MENU_SESSION_PROVIDER = "$OWNER:menu-session"
    const val PLAYER_STATE_PROVIDER = "$OWNER:player-state"
    const val WORLD_STATE_PROVIDER = "$OWNER:world-state"
    const val DRAFT_PROVIDER = "$OWNER:draft"
    const val SETTINGS_SESSION_PROVIDER = "$OWNER:settings-session"
    const val PORTAL_STATE_PROVIDER = "$OWNER:portal-state"
    const val OPERATION = "operation"

    fun userSetting(operation: String) = MenuReversibleContract(USER_SETTINGS_PROVIDER, mapOf(OPERATION to operation))
    fun displayOrder() = MenuReversibleContract(DISPLAY_ORDER_PROVIDER)
    fun creationSession(operation: String = "start") = MenuReversibleContract(CREATION_SESSION_PROVIDER, mapOf(OPERATION to operation))
    fun menuSession(operation: String) = MenuReversibleContract(MENU_SESSION_PROVIDER, mapOf(OPERATION to operation))
    fun playerState(operation: String) = MenuReversibleContract(PLAYER_STATE_PROVIDER, mapOf(OPERATION to operation))
    fun worldState(operation: String) = MenuReversibleContract(WORLD_STATE_PROVIDER, mapOf(OPERATION to operation))
    fun draft(operation: String) = MenuReversibleContract(DRAFT_PROVIDER, mapOf(OPERATION to operation))
    fun settingsSession(operation: String) = MenuReversibleContract(SETTINGS_SESSION_PROVIDER, mapOf(OPERATION to operation))
    fun portalState(operation: String) = MenuReversibleContract(PORTAL_STATE_PROVIDER, mapOf(OPERATION to operation))
}

class MwmReversibleStateProviders(private val plugin: MyWorldManager) {
    fun register(registry: MenuReversibleStateProviderRegistry) {
        registry.register(MenuReversibleStateProviderDefinition(MwmReversibleContracts.OWNER, "user-settings", UserSettingsProvider(plugin)))
        registry.register(MenuReversibleStateProviderDefinition(MwmReversibleContracts.OWNER, "world-display-order", DisplayOrderProvider(plugin)))
        registry.register(MenuReversibleStateProviderDefinition(MwmReversibleContracts.OWNER, "creation-session", CreationSessionProvider(plugin)))
        registry.register(MenuReversibleStateProviderDefinition(MwmReversibleContracts.OWNER, "menu-session", MenuSessionProvider(plugin)))
        registry.register(MenuReversibleStateProviderDefinition(MwmReversibleContracts.OWNER, "player-state", PlayerStateProvider(plugin)))
        registry.register(MenuReversibleStateProviderDefinition(MwmReversibleContracts.OWNER, "world-state", WorldStateProvider(plugin)))
        registry.register(MenuReversibleStateProviderDefinition(MwmReversibleContracts.OWNER, "draft", DraftProvider(plugin)))
        registry.register(MenuReversibleStateProviderDefinition(MwmReversibleContracts.OWNER, "settings-session", SettingsSessionProvider(plugin)))
        registry.register(MenuReversibleStateProviderDefinition(MwmReversibleContracts.OWNER, "portal-state", PortalStateProvider(plugin)))
    }

    fun unregister(registry: MenuReversibleStateProviderRegistry) = registry.unregisterOwner(MwmReversibleContracts.OWNER)
}

private data class PortalState(
    val portalId: UUID,
    val beforeText: Boolean,
    val expectedText: Boolean,
    val beforeColor: org.bukkit.Color,
    val expectedColor: org.bukkit.Color,
) : MenuReversibleProviderState

private class PortalStateProvider(private val plugin: MyWorldManager) : MenuReversibleStateProvider {
    private val colors = listOf(
        org.bukkit.Color.WHITE, org.bukkit.Color.SILVER, org.bukkit.Color.GRAY, org.bukkit.Color.BLACK,
        org.bukkit.Color.RED, org.bukkit.Color.MAROON, org.bukkit.Color.YELLOW, org.bukkit.Color.OLIVE,
        org.bukkit.Color.LIME, org.bukkit.Color.GREEN, org.bukkit.Color.AQUA, org.bukkit.Color.TEAL,
        org.bukkit.Color.BLUE, org.bukkit.Color.NAVY, org.bukkit.Color.FUCHSIA, org.bukkit.Color.PURPLE,
        org.bukkit.Color.ORANGE,
    )

    override fun capture(context: MenuReversibleStateCaptureContext): MenuReversibleProviderCaptureResult {
        val raw = context.interaction.routePayload["portal_id"]
            ?: return MenuReversibleProviderCaptureResult.Rejected("portal id is missing")
        val id = runCatching { UUID.fromString(raw) }.getOrNull()
            ?: return MenuReversibleProviderCaptureResult.Rejected("portal id is invalid: $raw")
        val portal = plugin.portalRepository.findAll().find { it.id == id }
            ?: return MenuReversibleProviderCaptureResult.Rejected("portal no longer exists: $id")
        val operation = context.interaction.contract.arguments[MwmReversibleContracts.OPERATION]
        val direction = if (context.interaction.click.isRightClick) GuiCycleDirection.PREVIOUS else GuiCycleDirection.NEXT
        val state = PortalState(
            id, portal.showText, if (operation == "text") !portal.showText else portal.showText,
            portal.particleColor, if (operation == "color") GuiCycle.select(portal.particleColor, colors, direction) else portal.particleColor,
        )
        return MenuReversibleProviderCaptureResult.Captured(state)
    }

    override fun restore(context: MenuReversibleStateRestoreContext): MenuReversibleProviderRestoreResult {
        val state = context.state as? PortalState ?: return MenuReversibleProviderRestoreResult.Rejected("invalid portal state type")
        return when (plugin.portalManager.restoreAppearance(
            state.portalId,
            state.beforeText,
            state.beforeColor,
            state.expectedText,
            state.expectedColor,
        )) {
            PortalManager.RestoreAppearanceResult.RESTORED -> MenuReversibleProviderRestoreResult.Restored
            PortalManager.RestoreAppearanceResult.TARGET_MISSING ->
                MenuReversibleProviderRestoreResult.Rejected("portal no longer exists: ${state.portalId}")
            PortalManager.RestoreAppearanceResult.CONCURRENT_CHANGE ->
                MenuReversibleProviderRestoreResult.Rejected("portal state changed concurrently: ${state.portalId}")
        }
    }
}

private data class SettingsSessionState(
    val before: SettingsSessionSnapshot,
    val expectedAfter: SettingsSessionSnapshot,
) : MenuReversibleProviderState
private class SettingsSessionProvider(private val plugin: MyWorldManager) : MenuReversibleStateProvider {
    override fun capture(context: MenuReversibleStateCaptureContext): MenuReversibleProviderCaptureResult {
        val operation = context.interaction.contract.arguments[MwmReversibleContracts.OPERATION]
            ?: return MenuReversibleProviderCaptureResult.Rejected("settings session operation is missing")
        val before = plugin.settingsSessionManager.snapshot(context.player.uniqueId)
            ?: return MenuReversibleProviderCaptureResult.Rejected("settings session is missing")
        val world = plugin.worldConfigRepository.findByUuid(before.worldUuid)
            ?: return MenuReversibleProviderCaptureResult.Rejected("settings target world no longer exists: ${before.worldUuid}")
        val expected = when (operation) {
            "weather" -> {
                val options = plugin.config.getStringList("environment.weather.options")
                if (options.isEmpty()) return MenuReversibleProviderCaptureResult.Rejected("weather options are empty")
                before.copy(tempWeather = GuiCycle.select(
                    before.tempWeather ?: world.fixedWeather ?: "DEFAULT",
                    options,
                    GuiCycleDirection.NEXT,
                ))
            }
            else -> return MenuReversibleProviderCaptureResult.Rejected("unsupported settings session operation: $operation")
        }
        return MenuReversibleProviderCaptureResult.Captured(SettingsSessionState(before, expected))
    }

    override fun restore(context: MenuReversibleStateRestoreContext): MenuReversibleProviderRestoreResult {
        val state = context.state as? SettingsSessionState
            ?: return MenuReversibleProviderRestoreResult.Rejected("invalid settings session state type")
        if (plugin.worldConfigRepository.findByUuid(state.before.worldUuid) == null) {
            return MenuReversibleProviderRestoreResult.Rejected("settings target world no longer exists: ${state.before.worldUuid}")
        }
        if (!plugin.settingsSessionManager.restoreIfCurrent(
                context.player.uniqueId, state.expectedAfter, state.before,
            )) {
            return MenuReversibleProviderRestoreResult.Rejected("settings session changed concurrently")
        }
        return MenuReversibleProviderRestoreResult.Restored
    }
}

private sealed interface DraftSnapshot : MenuReversibleProviderState {
    data class Tour(val before: TourEditSessionSnapshot, val expectedAfter: TourEditSessionSnapshot?) : DraftSnapshot
    data class Template(
        val before: me.awabi2048.myworldmanager.gui.TemplateWizardGui.WizardSessionSnapshot,
        val expectedAfter: me.awabi2048.myworldmanager.gui.TemplateWizardGui.WizardSessionSnapshot?,
    ) : DraftSnapshot
}

private class DraftProvider(private val plugin: MyWorldManager) : MenuReversibleStateProvider {
    override fun capture(context: MenuReversibleStateCaptureContext): MenuReversibleProviderCaptureResult {
        val operation = context.interaction.contract.arguments[MwmReversibleContracts.OPERATION]
            ?: return MenuReversibleProviderCaptureResult.Rejected("draft operation is missing")
        val playerId = context.player.uniqueId
        val state = when (operation) {
            "tour_icon_pick" -> {
                val before = plugin.tourSessionManager.snapshotEdit(playerId)
                    ?: return MenuReversibleProviderCaptureResult.Rejected("tour edit session is missing")
                if (plugin.worldConfigRepository.findByUuid(before.worldUuid) == null) {
                    return MenuReversibleProviderCaptureResult.Rejected("tour target world no longer exists: ${before.worldUuid}")
                }
                DraftSnapshot.Tour(before, before.copy(awaitingIconPick = true))
            }
            "tour_discard" -> {
                val before = plugin.tourSessionManager.snapshotEdit(playerId)
                    ?: return MenuReversibleProviderCaptureResult.Rejected("tour edit session is missing")
                if (plugin.worldConfigRepository.findByUuid(before.worldUuid) == null) {
                    return MenuReversibleProviderCaptureResult.Rejected("tour target world no longer exists: ${before.worldUuid}")
                }
                DraftSnapshot.Tour(before, null)
            }
            "tour_remove_waypoint" -> {
                val before = plugin.tourSessionManager.snapshotEdit(playerId)
                    ?: return MenuReversibleProviderCaptureResult.Rejected("tour edit session is missing")
                if (plugin.worldConfigRepository.findByUuid(before.worldUuid) == null) {
                    return MenuReversibleProviderCaptureResult.Rejected("tour target world no longer exists: ${before.worldUuid}")
                }
                val waypointRaw = context.interaction.arguments["waypoint"]
                    ?: return MenuReversibleProviderCaptureResult.Rejected("tour waypoint is missing")
                val waypoint = runCatching { UUID.fromString(waypointRaw) }.getOrNull()
                    ?: return MenuReversibleProviderCaptureResult.Rejected("tour waypoint is invalid: $waypointRaw")
                if (before.draft.waypoints.none { it.uuid == waypoint }) {
                    return MenuReversibleProviderCaptureResult.Rejected("tour waypoint no longer exists: $waypoint")
                }
                DraftSnapshot.Tour(
                    before,
                    before.copy(draft = before.draft.copy(
                        waypoints = before.draft.waypoints.filterNot { it.uuid == waypoint },
                    )),
                )
            }
            "template_cancel" -> {
                val before = plugin.templateWizardGui.snapshot(playerId)
                    ?: return MenuReversibleProviderCaptureResult.Rejected("template wizard session is missing")
                if (org.bukkit.Bukkit.getWorld(before.sourceWorldName) == null) {
                    return MenuReversibleProviderCaptureResult.Rejected("template source world no longer exists: ${before.sourceWorldName}")
                }
                DraftSnapshot.Template(before, null)
            }
            "template_origin" -> {
                val before = plugin.templateWizardGui.snapshot(playerId)
                    ?: return MenuReversibleProviderCaptureResult.Rejected("template wizard session is missing")
                if (org.bukkit.Bukkit.getWorld(before.sourceWorldName) == null) {
                    return MenuReversibleProviderCaptureResult.Rejected("template source world no longer exists: ${before.sourceWorldName}")
                }
                if (context.player.world.name != before.sourceWorldName) {
                    return MenuReversibleProviderCaptureResult.Rejected("template source world changed")
                }
                val location = context.player.location
                DraftSnapshot.Template(before, before.copy(origin = me.awabi2048.myworldmanager.gui.TemplateWizardGui.WizardLocationSnapshot(
                    context.player.world.uid, context.player.world.name,
                    location.x, location.y, location.z, location.yaw, location.pitch,
                )))
            }
            else -> return MenuReversibleProviderCaptureResult.Rejected("unsupported draft operation: $operation")
        }
        return MenuReversibleProviderCaptureResult.Captured(state)
    }

    override fun restore(context: MenuReversibleStateRestoreContext): MenuReversibleProviderRestoreResult {
        val playerId = context.player.uniqueId
        return when (val state = context.state) {
            is DraftSnapshot.Tour -> {
                if (plugin.worldConfigRepository.findByUuid(state.before.worldUuid) == null) {
                    return MenuReversibleProviderRestoreResult.Rejected("tour target world no longer exists: ${state.before.worldUuid}")
                }
                if (plugin.tourSessionManager.snapshotEdit(playerId) != state.expectedAfter) {
                    return MenuReversibleProviderRestoreResult.Rejected("tour draft changed concurrently")
                }
                plugin.tourSessionManager.restoreEdit(playerId, state.before)
                MenuReversibleProviderRestoreResult.Restored
            }
            is DraftSnapshot.Template -> {
                if (org.bukkit.Bukkit.getWorld(state.before.sourceWorldName) == null) {
                    return MenuReversibleProviderRestoreResult.Rejected("template source world no longer exists: ${state.before.sourceWorldName}")
                }
                if (plugin.templateWizardGui.snapshot(playerId) != state.expectedAfter) {
                    return MenuReversibleProviderRestoreResult.Rejected("template draft changed concurrently")
                }
                if (!plugin.templateWizardGui.restore(playerId, state.before)) {
                    return MenuReversibleProviderRestoreResult.Rejected("template origin world no longer exists")
                }
                MenuReversibleProviderRestoreResult.Restored
            }
            else -> MenuReversibleProviderRestoreResult.Rejected("invalid draft state type")
        }
    }
}

private sealed interface WorldStateSnapshot : MenuReversibleProviderState {
    data class StandardPublish(
        val worldUuid: UUID,
        val plan: StandardWorldPublishCyclePlan,
    ) : WorldStateSnapshot
    data class PolicyPublish(
        val worldUuid: UUID,
        val policyId: String,
        val before: WorldPublishReversibleState,
        val expectedAfter: WorldPublishReversibleState,
    ) : WorldStateSnapshot
    data class Notification(val worldUuid: UUID, val before: Boolean, val expectedAfter: Boolean) : WorldStateSnapshot
    data class MemberRole(
        val worldUuid: UUID,
        val memberUuid: UUID,
        val beforeMember: Boolean,
        val beforeModerator: Boolean,
        val expectedMember: Boolean,
        val expectedModerator: Boolean,
    ) : WorldStateSnapshot
}

private class WorldStateProvider(private val plugin: MyWorldManager) : MenuReversibleStateProvider {
    override fun capture(context: MenuReversibleStateCaptureContext): MenuReversibleProviderCaptureResult {
        val operation = context.interaction.contract.arguments[MwmReversibleContracts.OPERATION]
            ?: return MenuReversibleProviderCaptureResult.Rejected("world state operation is missing")
        val raw = context.interaction.arguments["world_uuid"] ?: context.interaction.arguments["world"]
            ?: context.interaction.routePayload["world_uuid"] ?: context.interaction.routePayload["world"]
            ?: return MenuReversibleProviderCaptureResult.Rejected("target world is missing")
        val worldUuid = runCatching { UUID.fromString(raw) }.getOrNull()
            ?: return MenuReversibleProviderCaptureResult.Rejected("target world is invalid: $raw")
        val world = plugin.worldConfigRepository.findByUuid(worldUuid)
            ?: return MenuReversibleProviderCaptureResult.Rejected("target world no longer exists: $worldUuid")
        val state = when (operation) {
            "publish" -> {
                val policy = MyWorldManagerApi.getWorldPublishPolicy()
                if (policy.handlesPublishCycle(world)) {
                    val reversiblePolicy = policy as? ReversibleWorldPublishPolicy
                        ?: return MenuReversibleProviderCaptureResult.Rejected(
                            "publish policy '${policy.getId()}' owns the operation but is not reversible",
                        )
                    val before = reversiblePolicy.capturePublishCycleState(context.player, world)
                    WorldStateSnapshot.PolicyPublish(
                        worldUuid,
                        reversiblePolicy.getId(),
                        before,
                        reversiblePolicy.expectedPublishCycleState(context.player, world, before),
                    )
                } else {
                    WorldStateSnapshot.StandardPublish(
                        worldUuid,
                        plugin.worldPublishService.captureStandardCycle(context.player, world),
                    )
                }
            }
            "notification" -> WorldStateSnapshot.Notification(worldUuid, world.notificationEnabled, !world.notificationEnabled)
            "member_role" -> {
                val memberRaw = context.interaction.arguments["target_uuid"]
                    ?: return MenuReversibleProviderCaptureResult.Rejected("member target is missing")
                val memberUuid = runCatching { UUID.fromString(memberRaw) }.getOrNull()
                    ?: return MenuReversibleProviderCaptureResult.Rejected("member target is invalid: $memberRaw")
                val beforeMember = memberUuid in world.members
                val beforeModerator = memberUuid in world.moderators
                WorldStateSnapshot.MemberRole(
                    worldUuid, memberUuid, beforeMember, beforeModerator,
                    beforeModerator, !beforeModerator,
                )
            }
            else -> return MenuReversibleProviderCaptureResult.Rejected("unsupported world state operation: $operation")
        }
        return MenuReversibleProviderCaptureResult.Captured(state)
    }

    override fun restore(context: MenuReversibleStateRestoreContext): MenuReversibleProviderRestoreResult {
        return when (val state = context.state) {
            is WorldStateSnapshot.StandardPublish -> {
                if (!plugin.worldPublishService.isWorldPresent(state.worldUuid)) {
                    return MenuReversibleProviderRestoreResult.Rejected("target world no longer exists: ${state.worldUuid}")
                }
                val expectedAfter = state.plan.expectedAfter
                    ?: return MenuReversibleProviderRestoreResult.Rejected("standard publish action did not complete")
                if (!plugin.worldPublishService.restoreStandardCycle(
                        context.player,
                        state.worldUuid,
                        state.plan.before,
                        expectedAfter,
                    )
                ) {
                    return MenuReversibleProviderRestoreResult.Rejected("publish state changed concurrently: ${state.worldUuid}")
                }
                MenuReversibleProviderRestoreResult.Restored
            }
            is WorldStateSnapshot.PolicyPublish -> {
                val world = plugin.worldConfigRepository.findByUuid(state.worldUuid)
                    ?: return MenuReversibleProviderRestoreResult.Rejected("target world no longer exists: ${state.worldUuid}")
                val policy = MyWorldManagerApi.getWorldPublishPolicy() as? ReversibleWorldPublishPolicy
                    ?: return MenuReversibleProviderRestoreResult.Rejected("publish policy is no longer reversible")
                if (policy.getId() != state.policyId || !policy.handlesPublishCycle(world)) {
                    return MenuReversibleProviderRestoreResult.Rejected("publish policy changed concurrently: ${state.policyId}")
                }
                when (val restored = policy.restorePublishCycleState(
                    context.player,
                    world,
                    state.before,
                    state.expectedAfter,
                )) {
                    WorldPublishReversibleRestoreResult.Restored -> MenuReversibleProviderRestoreResult.Restored
                    is WorldPublishReversibleRestoreResult.Rejected ->
                        MenuReversibleProviderRestoreResult.Rejected(restored.reason)
                }
            }
            is WorldStateSnapshot.Notification -> {
                when (plugin.worldSettingsStateService.restoreNotification(
                    state.worldUuid,
                    state.before,
                    state.expectedAfter,
                )) {
                    WorldSettingsStateService.RestoreResult.RESTORED -> MenuReversibleProviderRestoreResult.Restored
                    WorldSettingsStateService.RestoreResult.TARGET_MISSING ->
                        MenuReversibleProviderRestoreResult.Rejected("target world no longer exists: ${state.worldUuid}")
                    WorldSettingsStateService.RestoreResult.CONCURRENT_CHANGE ->
                        MenuReversibleProviderRestoreResult.Rejected("notification state changed concurrently: ${state.worldUuid}")
                }
            }
            is WorldStateSnapshot.MemberRole -> {
                when (plugin.worldSettingsStateService.restoreMemberRole(
                    state.worldUuid,
                    state.memberUuid,
                    state.beforeMember,
                    state.beforeModerator,
                    state.expectedMember,
                    state.expectedModerator,
                )) {
                    WorldSettingsStateService.RestoreResult.RESTORED -> MenuReversibleProviderRestoreResult.Restored
                    WorldSettingsStateService.RestoreResult.TARGET_MISSING ->
                        MenuReversibleProviderRestoreResult.Rejected("target world no longer exists: ${state.worldUuid}")
                    WorldSettingsStateService.RestoreResult.CONCURRENT_CHANGE ->
                        MenuReversibleProviderRestoreResult.Rejected("member role changed concurrently: ${state.memberUuid}")
                }
            }
            else -> MenuReversibleProviderRestoreResult.Rejected("invalid world state type")
        }
    }
}

private sealed interface PlayerStateSnapshot : MenuReversibleProviderState {
    data class Favorite(
        val worldUuid: UUID,
        val beforeDate: String?,
        val expectedDate: String?,
        val beforeCount: Int,
        val expectedCount: Int,
    ) : PlayerStateSnapshot
    data class Meet(
        val beforeStatus: String,
        val expectedStatus: String,
        val beforeSession: MeetSession,
        val expectedSession: MeetSession,
    ) : PlayerStateSnapshot
}

private class PlayerStateProvider(private val plugin: MyWorldManager) : MenuReversibleStateProvider {
    override fun capture(context: MenuReversibleStateCaptureContext): MenuReversibleProviderCaptureResult {
        val operation = context.interaction.contract.arguments[MwmReversibleContracts.OPERATION]
            ?: return MenuReversibleProviderCaptureResult.Rejected("player state operation is missing")
        val playerId = context.player.uniqueId
        val stats = plugin.playerStatsRepository.findByUuid(playerId)
        val state = when (operation) {
            "favorite_toggle" -> {
                val raw = context.interaction.arguments["world_uuid"] ?: context.interaction.routePayload["world_uuid"]
                    ?: return MenuReversibleProviderCaptureResult.Rejected("favorite target world is missing")
                val worldUuid = runCatching { UUID.fromString(raw) }.getOrNull()
                    ?: return MenuReversibleProviderCaptureResult.Rejected("favorite target world is invalid: $raw")
                val world = plugin.worldConfigRepository.findByUuid(worldUuid)
                    ?: return MenuReversibleProviderCaptureResult.Rejected("target world no longer exists: $worldUuid")
                val beforeDate = stats.favoriteWorlds[worldUuid]
                PlayerStateSnapshot.Favorite(
                    worldUuid, beforeDate, if (beforeDate == null) LocalDate.now().toString() else null,
                    world.favorite, if (beforeDate == null) world.favorite + 1 else (world.favorite - 1).coerceAtLeast(0),
                )
            }
            "meet_status" -> {
                val expected = when (stats.meetStatus) { "JOIN_ME" -> "ASK_ME"; "ASK_ME" -> "BUSY"; else -> "JOIN_ME" }
                val beforeSession = plugin.meetSessionManager.snapshot(playerId)
                PlayerStateSnapshot.Meet(stats.meetStatus, expected, beforeSession, beforeSession.copy(currentPage = 0))
            }
            else -> return MenuReversibleProviderCaptureResult.Rejected("unsupported player state operation: $operation")
        }
        return MenuReversibleProviderCaptureResult.Captured(state)
    }

    override fun restore(context: MenuReversibleStateRestoreContext): MenuReversibleProviderRestoreResult {
        val playerId = context.player.uniqueId
        return when (val state = context.state) {
            is PlayerStateSnapshot.Favorite -> {
                when (plugin.favoriteStateService.restore(
                    playerId,
                    state.worldUuid,
                    state.beforeDate,
                    state.expectedDate,
                    state.beforeCount,
                    state.expectedCount,
                )) {
                    FavoriteStateService.RestoreResult.RESTORED -> MenuReversibleProviderRestoreResult.Restored
                    FavoriteStateService.RestoreResult.TARGET_MISSING ->
                        MenuReversibleProviderRestoreResult.Rejected("target world no longer exists: ${state.worldUuid}")
                    FavoriteStateService.RestoreResult.CONCURRENT_CHANGE ->
                        MenuReversibleProviderRestoreResult.Rejected("favorite state changed concurrently: ${state.worldUuid}")
                }
            }
            is PlayerStateSnapshot.Meet -> {
                val stats = plugin.playerStatsRepository.findByUuid(playerId)
                if (stats.meetStatus != state.expectedStatus || plugin.meetSessionManager.snapshot(playerId) != state.expectedSession) {
                    return MenuReversibleProviderRestoreResult.Rejected("meet state changed concurrently")
                }
                stats.meetStatus = state.beforeStatus
                plugin.playerStatsRepository.save(stats)
                plugin.meetSessionManager.restore(playerId, state.beforeSession)
                MenuReversibleProviderRestoreResult.Restored
            }
            else -> MenuReversibleProviderRestoreResult.Rejected("invalid player state type")
        }
    }
}

private sealed interface MenuSessionSnapshot : MenuReversibleProviderState {
    data class Admin(val before: AdminGuiSession, val expectedAfter: AdminGuiSession) : MenuSessionSnapshot
    data class Discovery(val before: DiscoverySession, val expectedAfter: DiscoverySession) : MenuSessionSnapshot
    data class Favorite(val before: FavoriteSession, val expectedAfter: FavoriteSession) : MenuSessionSnapshot
}

private class MenuSessionProvider(private val plugin: MyWorldManager) : MenuReversibleStateProvider {
    override fun capture(context: MenuReversibleStateCaptureContext): MenuReversibleProviderCaptureResult {
        val operation = context.interaction.contract.arguments[MwmReversibleContracts.OPERATION]
            ?: return MenuReversibleProviderCaptureResult.Rejected("menu session operation is missing")
        val direction = when {
            context.interaction.click.isLeftClick -> GuiCycleDirection.NEXT
            context.interaction.click.isRightClick -> GuiCycleDirection.PREVIOUS
            else -> return MenuReversibleProviderCaptureResult.Rejected("unsupported cycle click: ${context.interaction.click}")
        }
        val playerId = context.player.uniqueId
        val state: MenuSessionSnapshot = when (operation) {
            "admin_archive_filter", "admin_publish_filter", "admin_player_filter", "admin_sort", "admin_portal_sort" -> {
                val before = plugin.adminGuiSessionManager.snapshot(playerId)
                val after = before.copy()
                when (operation) {
                    "admin_archive_filter" -> after.archiveFilter = GuiCycle.select(after.archiveFilter, ArchiveFilter.entries, direction)
                    "admin_publish_filter" -> after.publishFilter = GuiCycle.select(after.publishFilter, PublishFilter.entries, direction)
                    "admin_player_filter" -> after.playerFilterType = GuiCycle.select(after.playerFilterType, PlayerFilterType.entries, direction)
                    "admin_sort" -> after.sortBy = GuiCycle.select(
                        after.sortBy,
                        plugin.adminGuiSessionManager.availableSortTypes(),
                        direction,
                    )
                    "admin_portal_sort" -> after.portalSortBy = GuiCycle.select(after.portalSortBy, PortalSortType.entries, direction)
                }
                if (operation == "admin_portal_sort") after.portalPage = 0 else after.currentPage = 0
                MenuSessionSnapshot.Admin(before, after)
            }
            "discovery_sort", "discovery_tag", "discovery_special_filter" -> {
                val before = plugin.discoverySessionManager.snapshot(playerId)
                val after = before.copy()
                when (operation) {
                    "discovery_sort" -> after.sort = GuiCycle.select(after.sort, DiscoverySort.entries, direction)
                    "discovery_tag" -> after.selectedTag = GuiCycle.selectNullable(
                        after.selectedTag, plugin.worldTagManager.getEnabledTagIds() + null, direction,
                    )
                    "discovery_special_filter" -> after.specialFilter = GuiCycle.select(after.specialFilter, DiscoverySpecialFilter.entries, direction)
                }
                MenuSessionSnapshot.Discovery(before, after)
            }
            "favorite_tag" -> {
                val before = plugin.favoriteSessionManager.snapshot(playerId)
                val after = before.copy(selectedTag = GuiCycle.selectNullable(
                    before.selectedTag, plugin.worldTagManager.getEnabledTagIds() + null, direction,
                ))
                MenuSessionSnapshot.Favorite(before, after)
            }
            else -> return MenuReversibleProviderCaptureResult.Rejected("unsupported menu session operation: $operation")
        }
        return MenuReversibleProviderCaptureResult.Captured(state)
    }

    override fun restore(context: MenuReversibleStateRestoreContext): MenuReversibleProviderRestoreResult {
        val playerId = context.player.uniqueId
        return when (val state = context.state) {
            is MenuSessionSnapshot.Admin -> {
                if (plugin.adminGuiSessionManager.snapshot(playerId) != state.expectedAfter) rejectConcurrent("admin")
                else { plugin.adminGuiSessionManager.restore(playerId, state.before); MenuReversibleProviderRestoreResult.Restored }
            }
            is MenuSessionSnapshot.Discovery -> {
                if (plugin.discoverySessionManager.snapshot(playerId) != state.expectedAfter) rejectConcurrent("discovery")
                else { plugin.discoverySessionManager.restore(playerId, state.before); MenuReversibleProviderRestoreResult.Restored }
            }
            is MenuSessionSnapshot.Favorite -> {
                if (plugin.favoriteSessionManager.snapshot(playerId) != state.expectedAfter) rejectConcurrent("favorite")
                else { plugin.favoriteSessionManager.restore(playerId, state.before); MenuReversibleProviderRestoreResult.Restored }
            }
            else -> MenuReversibleProviderRestoreResult.Rejected("invalid menu session state type")
        }
    }

    private fun rejectConcurrent(kind: String) =
        MenuReversibleProviderRestoreResult.Rejected("$kind menu session changed concurrently")
}

private data class UserSettingState(val operation: String, val before: String, val expectedAfter: String) : MenuReversibleProviderState
private class UserSettingsProvider(private val plugin: MyWorldManager) : MenuReversibleStateProvider {
    override fun capture(context: MenuReversibleStateCaptureContext): MenuReversibleProviderCaptureResult {
        val operation = context.interaction.contract.arguments[MwmReversibleContracts.OPERATION]
            ?: return MenuReversibleProviderCaptureResult.Rejected("user settings operation is missing")
        val service = plugin.userSettingsService
        val value = when (operation) {
            "notification" -> service.notification(context.player.uniqueId).toString()
            "critical_visibility" -> service.criticalVisibility(context.player.uniqueId).toString()
            "tour_navigation" -> service.tourNavigation(context.player.uniqueId).name
            else -> return MenuReversibleProviderCaptureResult.Rejected("unsupported user settings operation: $operation")
        }
        val expectedAfter = when (operation) {
            "notification", "critical_visibility" -> (!value.toBooleanStrict()).toString()
            "tour_navigation" -> {
                val values = TourNavigationMode.entries
                values[(values.indexOf(TourNavigationMode.valueOf(value)) + 1) % values.size].name
            }
            else -> error("validated above")
        }
        return MenuReversibleProviderCaptureResult.Captured(UserSettingState(operation, value, expectedAfter))
    }

    override fun restore(context: MenuReversibleStateRestoreContext): MenuReversibleProviderRestoreResult {
        val state = context.state as? UserSettingState
            ?: return MenuReversibleProviderRestoreResult.Rejected("invalid user settings state type")
        return try {
            val current = when (state.operation) {
                "notification" -> plugin.userSettingsService.notification(context.player.uniqueId).toString()
                "critical_visibility" -> plugin.userSettingsService.criticalVisibility(context.player.uniqueId).toString()
                "tour_navigation" -> plugin.userSettingsService.tourNavigation(context.player.uniqueId).name
                else -> return MenuReversibleProviderRestoreResult.Rejected("unsupported user settings operation: ${state.operation}")
            }
            if (current != state.expectedAfter) {
                return MenuReversibleProviderRestoreResult.Rejected(
                    "user setting changed concurrently: ${state.operation}, expected=${state.expectedAfter}, actual=$current",
                )
            }
            when (state.operation) {
                "notification" -> plugin.userSettingsService.setNotification(context.player.uniqueId, state.before.toBooleanStrict())
                "critical_visibility" -> plugin.userSettingsService.setCriticalVisibility(context.player.uniqueId, state.before.toBooleanStrict())
                "tour_navigation" -> plugin.userSettingsService.setTourNavigation(context.player, TourNavigationMode.valueOf(state.before))
                else -> return MenuReversibleProviderRestoreResult.Rejected("unsupported user settings operation: ${state.operation}")
            }
            MenuReversibleProviderRestoreResult.Restored
        } catch (exception: RuntimeException) {
            MenuReversibleProviderRestoreResult.Rejected("failed to restore ${state.operation}: ${exception.message ?: exception.javaClass.simpleName}")
        }
    }
}

private data class DisplayOrderState(val targetWorld: UUID, val before: List<UUID>, val expectedAfter: List<UUID>) : MenuReversibleProviderState
private class DisplayOrderProvider(private val plugin: MyWorldManager) : MenuReversibleStateProvider {
    override fun capture(context: MenuReversibleStateCaptureContext): MenuReversibleProviderCaptureResult {
        val raw = context.interaction.arguments["capability_world_uuid"]
            ?: context.interaction.arguments["world_uuid"]
            ?: return MenuReversibleProviderCaptureResult.Rejected("target world argument is missing")
        val worldUuid = runCatching { UUID.fromString(raw) }.getOrNull()
            ?: return MenuReversibleProviderCaptureResult.Rejected("capability_world_uuid is invalid: $raw")
        if (plugin.worldConfigRepository.findByUuid(worldUuid) == null) {
            return MenuReversibleProviderCaptureResult.Rejected("target world no longer exists: $worldUuid")
        }
        val order = plugin.playerStatsRepository.findByUuid(context.player.uniqueId).worldDisplayOrder.toList()
        val expectedAfter = order.toMutableList().also { it.remove(worldUuid); it.add(0, worldUuid) }.toList()
        return MenuReversibleProviderCaptureResult.Captured(DisplayOrderState(worldUuid, order, expectedAfter))
    }

    override fun restore(context: MenuReversibleStateRestoreContext): MenuReversibleProviderRestoreResult {
        val state = context.state as? DisplayOrderState
            ?: return MenuReversibleProviderRestoreResult.Rejected("invalid display order state type")
        if (plugin.worldConfigRepository.findByUuid(state.targetWorld) == null) {
            return MenuReversibleProviderRestoreResult.Rejected("target world no longer exists: ${state.targetWorld}")
        }
        val stats = plugin.playerStatsRepository.findByUuid(context.player.uniqueId)
        if (stats.worldDisplayOrder != state.expectedAfter) {
            return MenuReversibleProviderRestoreResult.Rejected(
                "world display order changed concurrently for ${state.targetWorld}",
            )
        }
        if (!MyWorldManagerApi.restorePlayerWorldDisplayOrder(
                context.player.uniqueId,
                state.targetWorld,
                state.before,
            )
        ) {
            return MenuReversibleProviderRestoreResult.Rejected("target world no longer exists: ${state.targetWorld}")
        }
        return MenuReversibleProviderRestoreResult.Restored
    }
}

private data class CreationSessionState(
    val before: WorldCreationSessionSnapshot?,
    val expectedAfter: WorldCreationSessionSnapshot?,
    val startPlan: WorldCreationStartPlan? = null,
) : MenuReversibleProviderState
private class CreationSessionProvider(private val plugin: MyWorldManager) : MenuReversibleStateProvider {
    override fun capture(context: MenuReversibleStateCaptureContext): MenuReversibleProviderCaptureResult =
        run {
            val before = plugin.creationSessionManager.snapshot(context.player.uniqueId)
            val operation = context.interaction.contract.arguments[MwmReversibleContracts.OPERATION] ?: "start"
            val expected = when (operation) {
                "cancel" -> null
                "dimension" -> before?.copy(seedEnvironment = GuiCycle.select(
                    before.seedEnvironment,
                    listOf(org.bukkit.World.Environment.NORMAL, org.bukkit.World.Environment.NETHER, org.bukkit.World.Environment.THE_END),
                    GuiCycleDirection.NEXT,
                )) ?: return MenuReversibleProviderCaptureResult.Rejected("creation session is missing")
                "bedrock_start" -> null
                else -> return MenuReversibleProviderCaptureResult.Rejected("unsupported creation session operation: $operation")
            }
            val startPlan = if (operation == "bedrock_start") {
                plugin.creationSessionManager.captureBedrockStart(context.player.uniqueId)
            } else null
            MenuReversibleProviderCaptureResult.Captured(CreationSessionState(before, expected, startPlan))
        }

    override fun restore(context: MenuReversibleStateRestoreContext): MenuReversibleProviderRestoreResult {
        val state = context.state as? CreationSessionState
            ?: return MenuReversibleProviderRestoreResult.Rejected("invalid creation session state type")
        if (state.before != null && state.before.playerId != context.player.uniqueId) {
            return MenuReversibleProviderRestoreResult.Rejected("creation session belongs to another player")
        }
        val current = plugin.creationSessionManager.snapshot(context.player.uniqueId)
        val expectedAfter = state.startPlan?.expectedAfter ?: state.expectedAfter
        if (expectedAfter == null && state.startPlan != null) {
            return MenuReversibleProviderRestoreResult.Rejected("creation start action did not complete")
        }
        if (current != expectedAfter) {
            return MenuReversibleProviderRestoreResult.Rejected("creation session changed or disappeared before restore")
        }
        plugin.creationSessionManager.restore(context.player.uniqueId, state.before)
        return MenuReversibleProviderRestoreResult.Restored
    }
}
