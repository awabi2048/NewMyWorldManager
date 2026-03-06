package me.awabi2048.myworldmanager.command

import io.papermc.paper.connection.PlayerGameConnection
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.event.player.PlayerCustomClickEvent
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.util.PermissionManager
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import java.util.UUID

class VisitWorldCommand(private val plugin: MyWorldManager) : CommandExecutor, TabCompleter, Listener {

    private val pendingShowBackButton = mutableMapOf<UUID, Boolean>()

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

    @EventHandler
    fun onVisitWorldDialogResponse(event: PlayerCustomClickEvent) {
        val identifier = event.identifier
        if (identifier != Key.key("mwm:visitworld/input_submit") && identifier != Key.key("mwm:visitworld/input_cancel")) {
            return
        }

        val conn = event.commonConnection as? PlayerGameConnection ?: return
        val player = conn.player
        val showBackButton = pendingShowBackButton.remove(player.uniqueId) ?: false

        if (identifier == Key.key("mwm:visitworld/input_cancel")) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.operation_cancelled"))
            return
        }

        val view = event.getDialogResponseView() ?: return
        val input = view.getText("visitworld_query").orEmpty()
        processQueryInput(player, input, showBackButton)
    }

    private fun openVisitWorldInputByPlatform(player: Player, showBackButton: Boolean) {
        pendingShowBackButton[player.uniqueId] = showBackButton

        if (!plugin.playerPlatformResolver.isBedrock(player)) {
            showVisitWorldInputDialog(player)
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
                    pendingShowBackButton.remove(player.uniqueId)
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

    private fun showVisitWorldInputDialog(player: Player) {
        val lang = plugin.languageManager
        val dialog = Dialog.create { builder ->
            builder.empty()
                .base(
                    DialogBase.builder(Component.text(lang.getMessage(player, "gui.visitworld.input.title"), NamedTextColor.YELLOW))
                        .body(
                            listOf(
                                DialogBody.plainMessage(Component.text(lang.getMessage(player, "messages.visitworld_query_input")))
                            )
                        )
                        .inputs(
                            listOf(
                                DialogInput.text("visitworld_query", Component.text(lang.getMessage(player, "gui.visitworld.input.label")))
                                    .maxLength(64)
                                    .build()
                            )
                        )
                        .build()
                )
                .type(
                    DialogType.confirmation(
                        ActionButton.create(
                            Component.text(lang.getMessage(player, "gui.common.confirm"), NamedTextColor.GREEN),
                            null,
                            100,
                            DialogAction.customClick(Key.key("mwm:visitworld/input_submit"), null)
                        ),
                        ActionButton.create(
                            Component.text(lang.getMessage(player, "gui.common.cancel"), NamedTextColor.RED),
                            null,
                            200,
                            DialogAction.customClick(Key.key("mwm:visitworld/input_cancel"), null)
                        )
                    )
                )
        }
        player.showDialog(dialog)
    }

    private fun processQueryInput(player: Player, rawQuery: String, showBackButton: Boolean = false) {
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.visitworld_query_empty"))
            return
        }

        if (!plugin.visitWorldGui.hasSearchResult(query)) {
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
            .filter { it.publishLevel == PublishLevel.PUBLIC && !it.isArchived }
            .map { it.name }
            .distinct()
            .filter { it.lowercase().contains(input) }
            .take(20)
            .toList()
    }
}
