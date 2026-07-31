package me.awabi2048.myworldmanager.service

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
            else -> listOf(
                WorldSettingsActionOption(MenuGesture.LEFT_RIGHT),
            )
        }
        return WorldSettingsActionContract(action, options, isActionable(player, worldData, action))
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
            WorldSettingsAction.MANAGE_MEMBERS -> MenuActionResult.Success(
                MenuUpdate.Navigate(plugin.worldSettingsGui.memberManagementRoute(worldData.uuid)),
            )
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

    private fun isActionable(player: org.bukkit.entity.Player, worldData: WorldData, action: WorldSettingsAction): Boolean {
        if (action == WorldSettingsAction.WARP) {
            val isMember = player.uniqueId == worldData.owner ||
                player.uniqueId in worldData.moderators || player.uniqueId in worldData.members
            return MyWorldManagerApi.getWorldAccessPolicy().canEnterWorld(player, worldData, isMember) &&
                plugin.worldConfigRepository.findByWorldName(player.world.name)?.uuid != worldData.uuid
        }
        val admin = player.hasPermission("myworldmanager.admin")
        val owner = player.uniqueId == worldData.owner
        val moderator = player.uniqueId in worldData.moderators
        val permitted = when (action) {
            WorldSettingsAction.MANAGE_MEMBERS,
            WorldSettingsAction.MANAGE_PORTALS -> owner || admin
            else -> owner || moderator || admin
        }
        if (!permitted) return false
        return when (action) {
            WorldSettingsAction.SET_SPAWN -> player.world.name == (worldData.customWorldName ?: "my_world.${worldData.uuid}")
            WorldSettingsAction.MANAGE_PORTALS -> plugin.portalRepository.findAll().any { it.worldKey == worldData.worldKey }
            else -> true
        }
    }

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
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.warp_success", mapOf("world" to worldData.name)))
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
            plugin.languageManager.getMessage(player, "gui.settings.spawn.display"),
            plugin.languageManager.getMessage(player, "gui.settings.spawn.form.content"),
            listOf(
                plugin.languageManager.getMessage(player, "gui.settings.spawn.type.guest"),
                plugin.languageManager.getMessage(player, "gui.settings.spawn.type.member"),
                plugin.languageManager.getMessage(player, "gui.common.back"),
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
        val typeKey = if (click.isLeftClick) "gui.settings.spawn.type.guest" else "gui.settings.spawn.type.member"
        val typeName = plugin.languageManager.getMessage(player, typeKey)
        player.sendMessage(plugin.languageManager.getMessage(player, "messages.spawn_set_start", mapOf("type" to typeName)))
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
