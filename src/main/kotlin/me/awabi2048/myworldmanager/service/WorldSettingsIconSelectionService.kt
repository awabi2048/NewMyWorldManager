package me.awabi2048.myworldmanager.service

import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.session.MenuExternalInput
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** アイコン選択開始時のセッションとRouteを所有します。 */
internal class WorldSettingsIconSelectionService(private val plugin: MyWorldManager) {
    fun start(player: Player, worldData: WorldData): MenuActionResult {
        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.SELECT_ICON)
        plugin.settingsSessionManager.getSession(player)?.beginExternalInput(MenuExternalInput.SELECT_ICON)
        player.sendMessage(plugin.languageManager.getMessage("messages.icon_prompt"))
        return MenuActionResult.Success(MenuUpdate.Replace(plugin.worldSettingsGui.iconSelectionRoute(worldData.uuid)))
    }

    fun select(player: Player, clickedItem: ItemStack): MenuActionResult {
        val session = plugin.settingsSessionManager.getSession(player) ?: return MenuActionResult.Ignored
        if (clickedItem.type == Material.AIR) return MenuActionResult.Ignored
        val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: return MenuActionResult.Ignored
        if (clickedItem.type == Material.BLACK_STAINED_GLASS_PANE || clickedItem.type == Material.GRAY_STAINED_GLASS_PANE) {
            player.playSound(player.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_BIT, 1.0f, 0.5f)
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.icon_forbidden"))
            return MenuActionResult.Rejected()
        }
        worldData.icon = clickedItem.type
        plugin.worldConfigRepository.save(worldData)
        val marker = "\uE000mwm_icon\uE001"
        val name = clickedItem.effectiveName().decoration(TextDecoration.ITALIC, false)
        player.sendMessage(plugin.languageManager.getComponent(player, "messages.icon_changed", mapOf("icon" to marker)).replaceText { it.matchLiteral(marker).replacement(name) })
        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.VIEW_SETTINGS, isGui = true)
        val restored = plugin.settingsSessionManager.getSession(player)
        val route = MyWorldManagerApi.prepareWorldSettingsRoute(player, worldData.uuid, me.awabi2048.myworldmanager.api.extension.WorldSettingsNavigationRequest(showBackButton = restored?.showBackButton ?: true, isAdminFlow = restored?.isAdminFlow ?: false, isPlayerWorldFlow = restored?.isPlayerWorldFlow, parentShowBackButton = restored?.parentShowBackButton)) ?: return MenuActionResult.Rejected()
        return MenuActionResult.Success(MenuUpdate.Replace(route))
    }
}
