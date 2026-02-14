package me.awabi2048.myworldmanager.ui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.ui.bedrock.BedrockMenuService
import org.bukkit.entity.Player

class MenuEntryRouter(
    private val plugin: MyWorldManager,
    private val platformResolver: PlayerPlatformResolver,
    private val bedrockMenuService: BedrockMenuService
) {

    fun openPlayerWorld(player: Player, page: Int = 0, showBackButton: Boolean = false) {
        if (platformResolver.isBedrock(player)) {
            bedrockMenuService.openPlayerWorld(player, page, showBackButton)
            return
        }

        plugin.playerWorldGui.open(player, page, showBackButton)
    }

    fun openUserSettings(player: Player, showBackButton: Boolean = false) {
        if (platformResolver.isBedrock(player)) {
            bedrockMenuService.openSettings(player, showBackButton)
            return
        }

        plugin.userSettingsGui.open(player, showBackButton)
    }

    fun openWorldSettings(player: Player, worldData: WorldData, showBackButton: Boolean = false) {
        if (platformResolver.isBedrock(player)) {
            bedrockMenuService.openCurrentWorldMenu(player, worldData, showBackButton)
            return
        }

        plugin.worldSettingsGui.open(player, worldData, showBackButton)
    }
}
