package me.awabi2048.myworldmanager.service

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiSettingsKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuGesture
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.extension.WorldSettingsAction
import me.awabi2048.myworldmanager.api.extension.WorldSettingsActionContract
import me.awabi2048.myworldmanager.api.extension.WorldSettingsActionRequest
import me.awabi2048.myworldmanager.api.extension.WorldSettingsActionOption
import me.awabi2048.myworldmanager.api.extension.WorldSettingsActionRestriction
import me.awabi2048.myworldmanager.gui.WorldSettingsRuntimeOperation
import me.awabi2048.myworldmanager.gui.WorldSettingsRuntimeScreen
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.session.SettingsAction
import org.bukkit.event.inventory.ClickType

/**
 * ワールド設定の標準操作を、表示時・実行時で同じ契約により評価するユースケースです。
 */
class WorldSettingsActionService(private val plugin: MyWorldManager) {
    fun contract(player: org.bukkit.entity.Player, worldData: WorldData, action: WorldSettingsAction): WorldSettingsActionContract {
        val options = when (action) {
            WorldSettingsAction.SET_SPAWN -> if (plugin.playerPlatformResolver.isBedrock(player)) {
                listOf(
                    WorldSettingsActionOption(MenuGesture.LEFT_RIGHT),
                )
            } else {
                listOf(
                    WorldSettingsActionOption(MenuGesture.LEFT),
                    WorldSettingsActionOption(MenuGesture.RIGHT),
                )
            }
            WorldSettingsAction.EDIT_ANNOUNCEMENT -> listOf(
                WorldSettingsActionOption(MenuGesture.LEFT),
                WorldSettingsActionOption(MenuGesture.RIGHT),
            )
            else -> listOf(WorldSettingsActionOption(MenuGesture.ANY))
        }
        val (actionable, restriction) = evaluate(player, worldData, action)
        return WorldSettingsActionContract(action, options, actionable, restriction)
    }

    fun execute(request: WorldSettingsActionRequest): MenuActionResult {
        val worldData = plugin.worldConfigRepository.findByUuid(request.worldUuid)
            ?: return MenuActionResult.Ignored
        val contract = contract(request.player, worldData, request.action)
        if (!contract.actionable || request.click !in contract.acceptedClicks) return MenuActionResult.Ignored

        return when (request.action) {
            WorldSettingsAction.WARP -> warp(request.player, worldData)
            WorldSettingsAction.EDIT_INFO -> editInfo(request.player, worldData)
            WorldSettingsAction.SELECT_ICON -> plugin.worldSettingsIconSelectionService.start(request.player, worldData)
            WorldSettingsAction.SET_SPAWN -> selectSpawnType(request.player, worldData, request.click)
            WorldSettingsAction.MANAGE_MEMBERS -> {
                plugin.settingsSessionManager.updateSessionAction(
                    request.player,
                    worldData.uuid,
                    SettingsAction.MANAGE_MEMBERS,
                    isGui = true,
                )
                MenuActionResult.Success(
                    MenuUpdate.Navigate(plugin.worldSettingsGui.memberManagementRoute(worldData.uuid)),
                )
            }
            WorldSettingsAction.EDIT_ANNOUNCEMENT -> editAnnouncement(request.player, worldData, request.click)
            WorldSettingsAction.MANAGE_TOUR -> MenuActionResult.Success(
                MenuUpdate.Navigate(plugin.tourGui.editRoute(worldData.uuid)),
            )
            WorldSettingsAction.MANAGE_PORTALS -> MenuActionResult.Success(
                MenuUpdate.Navigate(
                    plugin.worldSettingsGui.runtimeRoute(WorldSettingsRuntimeScreen.PORTAL_MANAGEMENT, worldData.uuid),
                ),
            )
        }
    }

    /**
     * 実行可否と、実行できない場合の型付き理由を同時に決定します。
     * 表示時(contract)と実行時(execute)がこの結果を共有することで、
     * 「実行は拒否されるのに警告が表示されない」漏れと、判定ロジックの二重管理を防ぎます。
     */
    private fun evaluate(
        player: org.bukkit.entity.Player,
        worldData: WorldData,
        action: WorldSettingsAction,
    ): Pair<Boolean, WorldSettingsActionRestriction?> {
        if (action == WorldSettingsAction.WARP) {
            val isMember = player.uniqueId == worldData.owner ||
                player.uniqueId in worldData.moderators || player.uniqueId in worldData.members
            // 入場権限の拒否理由は公開レベル等に依存する動的な内容のため、restriction では表現しない。
            val canWarp = MyWorldManagerApi.getWorldAccessPolicy().canEnterWorld(player, worldData, isMember) &&
                !isInTargetWorld(player, worldData)
            return canWarp to null
        }
        val admin = player.hasPermission("myworldmanager.admin")
        val owner = player.uniqueId == worldData.owner
        val moderator = player.uniqueId in worldData.moderators
        val member = owner || moderator || player.uniqueId in worldData.members
        val permitted = when (action) {
            WorldSettingsAction.MANAGE_MEMBERS,
            WorldSettingsAction.MANAGE_PORTALS -> owner || admin
            WorldSettingsAction.MANAGE_TOUR -> member || admin
            else -> owner || moderator || admin
        }
        if (!permitted) return false to null
        // 対象ワールド内にいる必要がある操作は、ワールド外からであることを型付き理由として伝える。
        val requiresTargetWorld = when (action) {
            WorldSettingsAction.SET_SPAWN,
            WorldSettingsAction.MANAGE_TOUR -> true
            else -> false
        }
        if (requiresTargetWorld && !isInTargetWorld(player, worldData)) {
            return false to WorldSettingsActionRestriction.NOT_IN_TARGET_WORLD
        }
        return when (action) {
            WorldSettingsAction.MANAGE_PORTALS ->
                plugin.portalRepository.findAll().any { it.worldKey == worldData.worldKey } to null
            else -> true to null
        }
    }

    /** 対象ワールド内にいるかどうか。GUI表示側と同じ isPlayerInWorld 判定へ統一します。 */
    private fun isInTargetWorld(player: org.bukkit.entity.Player, worldData: WorldData): Boolean =
        MyWorldManagerApi.getWorldService()?.isPlayerInWorld(player, worldData) == true

    private fun warp(player: org.bukkit.entity.Player, worldData: WorldData): MenuActionResult {
        val session = plugin.settingsSessionManager.getSession(player)
        plugin.settingsSessionManager.updateSessionAction(
            player, worldData.uuid, SettingsAction.VIEW_SETTINGS, isGui = true,
            isPlayerWorldFlow = session?.isPlayerWorldFlow,
            parentShowBackButton = session?.parentShowBackButton,
        )
        CCSystem.getAPI().getMenuRuntimeService().suspendForExternal(player)
        plugin.worldService.teleportToWorld(player, worldData.uuid, closeInventoryOnLoad = false) {
            if (!player.isOnline) return@teleportToWorld
            player.sendMessage(plugin.languageManager.getMessage(player, MyworldMessagesKeys.MESSAGES_WARP_SUCCESS, mapOf("world" to worldData.name)))
            CCSystem.getAPI().getMenuRuntimeService().finishExternal(player)
        }
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun editInfo(player: org.bukkit.entity.Player, worldData: WorldData): MenuActionResult {
        return plugin.worldSettingsInputService.editInfo(player, worldData)
    }

    private fun selectSpawnType(
        player: org.bukkit.entity.Player,
        worldData: WorldData,
        click: ClickType,
    ): MenuActionResult {
        if (!plugin.playerPlatformResolver.isBedrock(player)) {
            return beginSpawnSetting(player, worldData, click)
        }
        val runtime = CCSystem.getAPI().getMenuRuntimeService()
        runtime.suspendForExternal(player)
        val sent = plugin.floodgateFormBridge.sendSimpleForm(
            player,
            plugin.languageManager.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_SPAWN_DISPLAY),
            plugin.languageManager.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_SPAWN_FORM_CONTENT),
            listOf(
                plugin.languageManager.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_SPAWN_TYPE_GUEST),
                plugin.languageManager.getMessage(player, MyworldGuiSettingsKeys.GUI_SETTINGS_SPAWN_TYPE_MEMBER),
                plugin.languageManager.getMessage(player, CommonKeys.GUI_COMMON_BACK),
            ),
            { index ->
                when (index) {
                    0 -> beginSpawnSetting(player, worldData, ClickType.LEFT, suspendRuntime = false)
                    1 -> beginSpawnSetting(player, worldData, ClickType.RIGHT, suspendRuntime = false)
                    else -> runtime.finishExternal(player)
                }
            },
            { runtime.finishExternal(player) },
        )
        if (!sent) {
            runtime.finishExternal(player)
            return MenuActionResult.Rejected()
        }
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun beginSpawnSetting(
        player: org.bukkit.entity.Player,
        worldData: WorldData,
        click: ClickType,
        suspendRuntime: Boolean = true,
    ): MenuActionResult {
        val action = if (click.isLeftClick) SettingsAction.SET_SPAWN_GUEST else SettingsAction.SET_SPAWN_MEMBER
        val typeKey = if (click.isLeftClick) {
            MyworldGuiSettingsKeys.GUI_SETTINGS_SPAWN_TYPE_GUEST
        } else {
            MyworldGuiSettingsKeys.GUI_SETTINGS_SPAWN_TYPE_MEMBER
        }
        val typeName = plugin.languageManager.getMessage(player, typeKey)
        player.sendMessage(plugin.languageManager.getMessage(player, MyworldMessagesKeys.MESSAGES_SPAWN_SET_START, mapOf("type" to typeName)))
        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, action)
        if (suspendRuntime) {
            CCSystem.getAPI().getMenuRuntimeService().suspendForExternal(player)
        }
        plugin.worldSettingsSpawnPreviewService.start(player)
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun editAnnouncement(player: org.bukkit.entity.Player, worldData: WorldData, click: ClickType): MenuActionResult {
        return plugin.worldSettingsInputService.editAnnouncement(player, worldData, click.isRightClick)
    }
}
