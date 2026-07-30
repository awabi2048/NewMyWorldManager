@file:Suppress("REDUNDANT_CALL_OF_CONVERSION_METHOD")

package me.awabi2048.myworldmanager.listener

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuDialogButton
import com.awabi2048.ccsystem.api.gui.MenuDialogHandler
import com.awabi2048.ccsystem.api.gui.MenuDialogInput
import com.awabi2048.ccsystem.api.gui.MenuDialogRequest
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.session.MenuExternalInput
import me.awabi2048.myworldmanager.session.PlayerFilterType
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

class AdminGuiListener {

    fun openAdminPlayerFilterInput(plugin: MyWorldManager, player: Player) {
        val lang = plugin.languageManager

        if (plugin.playerPlatformResolver.isBedrock(player)) {
            if (!plugin.floodgateFormBridge.isAvailable(player)) {
                plugin.floodgateFormBridge.notifyFallbackCancelled(player)
                plugin.settingsSessionManager.endSession(player)
                CCSystem.getAPI().getMenuRuntimeService().reopenCurrent(player)
                return
            }

        CCSystem.getAPI().getMenuRuntimeService().suspendForExternal(player)
            val opened =
                plugin.floodgateFormBridge.sendCustomInputForm(
                    player = player,
                    title = lang.getMessage(player, "gui.bedrock.input.admin_player_filter.title"),
                    label = lang.getMessage(player, "gui.bedrock.input.admin_player_filter.label"),
                    placeholder =
                        lang.getMessage(player, "gui.bedrock.input.admin_player_filter.placeholder"),
                    defaultValue = "",
                    onSubmit = { value ->
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            applyAdminPlayerFilter(plugin, player, value)
                        })
                    },
                    onClosed = {
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            plugin.settingsSessionManager.endSession(player)
                            if (player.isOnline) {
                                CCSystem.getAPI().getMenuRuntimeService().resumeFromExternal(player)
                            }
                        })
                    }
                )
            if (!opened) {
                plugin.floodgateFormBridge.notifyFallbackCancelled(player)
                plugin.settingsSessionManager.endSession(player)
                CCSystem.getAPI().getMenuRuntimeService().resumeFromExternal(player)
            }
            return
        }

        val prompt = lang.getMessage(player, "messages.admin_player_filter_prompt")
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = "myworldmanager",
                id = "admin-player-filter",
                title = Component.text(prompt, NamedTextColor.YELLOW),
                body = listOf(Component.text(prompt)),
                inputs = listOf(
                    MenuDialogInput.Text(
                        "admin_player_name",
                        Component.text(lang.getMessage(player, "gui.bedrock.input.admin_player_filter.label")),
                    ),
                ),
                confirm = MenuDialogButton(
                    Component.text(lang.getMessage(player, "gui.common.confirm"), NamedTextColor.GREEN),
                    MenuDialogHandler { target, response ->
                        applyAdminPlayerFilter(plugin, target, response.textValue("admin_player_name"))
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                ),
                cancel = MenuDialogButton(
                    Component.text(lang.getMessage(player, "gui.common.cancel"), NamedTextColor.RED),
                    MenuDialogHandler { target, _ ->
                        plugin.settingsSessionManager.endSession(target)
                        target.sendMessage(
                            plugin.languageManager.getMessage(target, "messages.operation_cancelled"),
                        )
                        MenuActionResult.Success(MenuUpdate.Resume)
                    },
                ),
            ),
        )
    }

    private fun applyAdminPlayerFilter(plugin: MyWorldManager, player: Player, targetNameRaw: String) {
        val targetName = targetNameRaw.trim()
        val offlinePlayer = PlayerNameUtil.resolveOfflinePlayer(plugin, targetName)
        if (offlinePlayer == null) {
            player.sendMessage(plugin.languageManager.getMessage(player, "general.player_not_found"))
            plugin.settingsSessionManager.endSession(player)
            CCSystem.getAPI().getMenuRuntimeService().resumeFromExternal(player)
            return
        }

        val adminSession = plugin.adminGuiSessionManager.getSession(player.uniqueId)
        adminSession.playerFilter = offlinePlayer.uniqueId
        if (adminSession.playerFilterType == PlayerFilterType.NONE) {
            adminSession.playerFilterType = PlayerFilterType.OWNER
        }

        player.sendMessage(
            plugin.languageManager.getMessage(
                player,
                "messages.admin_player_filter_set",
                mapOf("player" to (offlinePlayer.name ?: targetName))
            )
        )
        plugin.settingsSessionManager.endSession(player)
        CCSystem.getAPI().getMenuRuntimeService().resumeFromExternal(player)
    }

    fun sendWorldDirectoryCopyMessage(player: Player, worldData: me.awabi2048.myworldmanager.model.WorldData) {
        if (player.gameMode != org.bukkit.GameMode.CREATIVE) {
            return
        }

        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        val lang = plugin.languageManager
        val worldDirectory = worldData.customWorldName ?: "my_world.${worldData.uuid}"
        val bar = net.kyori.adventure.text.Component.text("§8§m－－－－－－－－－－－－－－－－－－")
        val header = net.kyori.adventure.text.Component.text(
            lang.getMessage(player, "messages.internal_data_extracted", mapOf("world" to worldData.name))
        )

        val worldDirectoryText = net.kyori.adventure.text.Component.text(lang.getMessage(player, "messages.copy_world_uuid"))
            .hoverEvent(
                net.kyori.adventure.text.event.HoverEvent.showText(
                    net.kyori.adventure.text.Component.text(lang.getMessage(player, "messages.copy_world_uuid_hover"))
                )
            )
            .clickEvent(net.kyori.adventure.text.event.ClickEvent.copyToClipboard(worldDirectory))

        val ownerUuidText = net.kyori.adventure.text.Component.text(lang.getMessage(player, "messages.copy_owner_uuid"))
            .hoverEvent(
                net.kyori.adventure.text.event.HoverEvent.showText(
                    net.kyori.adventure.text.Component.text(lang.getMessage(player, "messages.copy_owner_uuid_hover"))
                )
            )
            .clickEvent(net.kyori.adventure.text.event.ClickEvent.copyToClipboard(worldData.owner.toString()))

        player.sendMessage(bar)
        player.sendMessage(header)
        player.sendMessage(net.kyori.adventure.text.Component.empty())
        player.sendMessage(worldDirectoryText)
        player.sendMessage(ownerUuidText)
        player.sendMessage(bar)

        plugin.soundManager.playCopySound(player)
    }

}
