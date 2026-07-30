package me.awabi2048.myworldmanager.service

import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.session.MenuExternalInput
import me.awabi2048.myworldmanager.session.SettingsAction
import org.bukkit.entity.Player

/** アイコン選択開始時のセッションとRouteを所有します。 */
internal class WorldSettingsIconSelectionService(private val plugin: MyWorldManager) {
    fun start(player: Player, worldData: WorldData): MenuActionResult {
        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.SELECT_ICON)
        plugin.settingsSessionManager.getSession(player)?.beginExternalInput(MenuExternalInput.SELECT_ICON)
        player.sendMessage(plugin.languageManager.getMessage("messages.icon_prompt"))
        return MenuActionResult.Success(MenuUpdate.Replace(plugin.worldSettingsGui.iconSelectionRoute(worldData.uuid)))
    }
}
