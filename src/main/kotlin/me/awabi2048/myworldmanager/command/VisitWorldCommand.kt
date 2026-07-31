package me.awabi2048.myworldmanager.command

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuDialogButton
import com.awabi2048.ccsystem.api.gui.MenuDialogHandler
import com.awabi2048.ccsystem.api.gui.MenuDialogInput
import com.awabi2048.ccsystem.api.gui.MenuDialogRequest
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.util.PermissionManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class VisitWorldCommand(private val plugin: MyWorldManager) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val lang = plugin.languageManager
        if (!PermissionManager.checkPermission(sender, PermissionManager.COMMAND_FINDWORLD)) {
            PermissionManager.sendNoPermissionMessage(sender)
            return true
        }
        if (sender !is Player) {
            sender.sendMessage(lang.getMessage("general.player_only"))
            return true
        }

        val showBackButton = args.any { it.equals("-menu", ignoreCase = true) }
        val queryParts = args.filterNot { it.equals("-menu", ignoreCase = true) }

        if (queryParts.isEmpty()) {
            openVisitWorldInputByPlatform(sender, showBackButton)
            return true
        }

        processQueryInput(sender, queryParts.joinToString(" "), showBackButton)
        return true
    }

    private fun openVisitWorldInputByPlatform(player: Player, showBackButton: Boolean) {
        if (!plugin.playerPlatformResolver.isBedrock(player)) {
            showVisitWorldInputDialog(player, showBackButton)
            return
        }

        if (!plugin.floodgateFormBridge.isAvailable(player)) {
            plugin.floodgateFormBridge.notifyFallbackCancelled(player)
            return
        }

        val lang = plugin.languageManager
        val opened = plugin.floodgateFormBridge.sendCustomInputForm(
            player = player,
            title = lang.getMessage(player, "gui.visitworld.input.title"),
            label = lang.getMessage(player, "gui.visitworld.input.label"),
            placeholder = lang.getMessage(player, "gui.visitworld.input.placeholder"),
            defaultValue = "",
            onSubmit = { value ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    processQueryInput(player, value, showBackButton)
                })
            },
            onClosed = {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (player.isOnline) {
                        player.sendMessage(plugin.languageManager.getMessage(player, "messages.operation_cancelled"))
                    }
                })
            }
        )

        if (!opened) {
            plugin.floodgateFormBridge.notifyFallbackCancelled(player)
        }
    }

    private fun showVisitWorldInputDialog(player: Player, showBackButton: Boolean) {
        val lang = plugin.languageManager
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = "myworldmanager",
                id = "visit-world-input",
                title = Component.text(lang.getMessage(player, "gui.visitworld.input.title"), NamedTextColor.YELLOW),
                body = listOf(Component.text(lang.getMessage(player, "messages.visitworld_query_input"))),
                inputs = listOf(
                    MenuDialogInput.Text(
                        "visitworld_query",
                        Component.text(lang.getMessage(player, "gui.visitworld.input.label")),
                        maxLength = 64,
                    ),
                ),
                confirm = MenuDialogButton(
                    Component.text(lang.getMessage(player, "gui.common.confirm"), NamedTextColor.GREEN),
                    MenuDialogHandler { actor, response ->
                        processQueryInput(actor, response.textValue("visitworld_query"), showBackButton)
                        MenuActionResult.Success(MenuUpdate.Close)
                    },
                ),
                cancel = MenuDialogButton(
                    Component.text(lang.getMessage(player, "gui.common.cancel"), NamedTextColor.RED),
                    MenuDialogHandler { actor, _ ->
                        actor.sendMessage(lang.getMessage(actor, "messages.operation_cancelled"))
                        MenuActionResult.Success(MenuUpdate.Close)
                    },
                ),
            ),
        )
    }

    private fun processQueryInput(player: Player, rawQuery: String, showBackButton: Boolean = false) {
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.visitworld_query_empty"))
            return
        }

        if (!plugin.visitWorldGui.hasSearchResult(player, query)) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.visitworld_no_result", mapOf("query" to query)))
            plugin.soundManager.playActionSound(player, "visit", "access_denied")
            return
        }

        plugin.visitWorldGui.open(player, query, 0, showBackButton)
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (!PermissionManager.checkPermission(sender, PermissionManager.COMMAND_FINDWORLD)) return emptyList()
        if (sender !is Player) return emptyList()

        if (args.isEmpty()) return emptyList()
        if (args.any { it.equals("-menu", ignoreCase = true) }) return emptyList()

        val input = args.joinToString(" ").trim().lowercase()
        if (input.isEmpty()) return emptyList()

        return plugin.worldConfigRepository.findAll()
            .asSequence()
            .filter { MyWorldManagerApi.getWorldAccessPolicy().canShowInVisitWorldList(sender, it) }
            .map { it.name }
            .distinct()
            .filter { it.lowercase().contains(input) }
            .take(20)
            .toList()
    }
}
