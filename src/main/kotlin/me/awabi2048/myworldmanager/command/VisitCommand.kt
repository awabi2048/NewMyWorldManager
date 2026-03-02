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
import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.repository.*
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

class VisitCommand(private val plugin: MyWorldManager) : CommandExecutor, TabCompleter, Listener {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val lang = plugin.languageManager
        if (!PermissionManager.checkPermission(sender, PermissionManager.CITIZEN)) {
            PermissionManager.sendNoPermissionMessage(sender)
            return true
        }
        if (sender !is Player) {
            sender.sendMessage(lang.getMessage("general.player_only"))
            return true
        }

        if (args.isEmpty()) {
            openVisitInputByPlatform(sender)
            return true
        }

        processVisitTargetInput(sender, args[0])
        return true
    }

    @EventHandler
    fun onVisitDialogResponse(event: PlayerCustomClickEvent) {
        val identifier = event.identifier
        if (identifier != Key.key("mwm:visit/input_submit") && identifier != Key.key("mwm:visit/input_cancel")) {
            return
        }

        val conn = event.commonConnection as? PlayerGameConnection ?: return
        val player = conn.player

        if (identifier == Key.key("mwm:visit/input_cancel")) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.operation_cancelled"))
            return
        }

        val view = event.getDialogResponseView() ?: return
        val input = view.getText("visit_player").orEmpty()
        processVisitTargetInput(player, input)
    }

    private fun openVisitInputByPlatform(player: Player) {
        if (!plugin.playerPlatformResolver.isBedrock(player)) {
            showVisitInputDialog(player)
            return
        }

        if (!plugin.floodgateFormBridge.isAvailable(player)) {
            plugin.floodgateFormBridge.notifyFallbackCancelled(player)
            return
        }

        val lang = plugin.languageManager
        val opened = plugin.floodgateFormBridge.sendCustomInputForm(
            player = player,
            title = lang.getMessage(player, "gui.visit.input.title"),
            label = lang.getMessage(player, "gui.visit.input.label"),
            placeholder = lang.getMessage(player, "gui.visit.input.placeholder"),
            defaultValue = "",
            onSubmit = { value ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    processVisitTargetInput(player, value)
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

    private fun showVisitInputDialog(player: Player) {
        val lang = plugin.languageManager
        val dialog = Dialog.create { builder ->
            builder.empty()
                .base(
                    DialogBase.builder(Component.text(lang.getMessage(player, "gui.visit.input.title"), NamedTextColor.YELLOW))
                        .body(
                            listOf(
                                DialogBody.plainMessage(Component.text(lang.getMessage(player, "messages.visit_target_input")))
                            )
                        )
                        .inputs(
                            listOf(
                                DialogInput.text("visit_player", Component.text(lang.getMessage(player, "gui.visit.input.label")))
                                    .maxLength(16)
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
                            DialogAction.customClick(Key.key("mwm:visit/input_submit"), null)
                        ),
                        ActionButton.create(
                            Component.text(lang.getMessage(player, "gui.common.cancel"), NamedTextColor.RED),
                            null,
                            200,
                            DialogAction.customClick(Key.key("mwm:visit/input_cancel"), null)
                        )
                    )
                )
        }
        player.showDialog(dialog)
    }

    private fun processVisitTargetInput(player: Player, rawInput: String) {
        val targetName = rawInput.trim()
        if (targetName.isEmpty()) {
            player.sendMessage(plugin.languageManager.getMessage(player, "general.player_not_found"))
            return
        }

        val target = resolveTargetPlayer(targetName)

        if (target == null) {
            player.sendMessage("§cプレイヤー「$targetName」が見つかりませんでした。")
            return
        }

        if (target.uniqueId == player.uniqueId) {
            player.sendMessage("§c自分自身のワールド一覧を表示することはできません。/myworld を使用してください。")
            return
        }

        val visitableWorlds = collectVisitableWorlds(player, target.uniqueId)

        if (visitableWorlds.isEmpty()) {
            player.sendMessage("§cプレイヤー「$targetName」の訪問可能なワールドが見つかりませんでした。")
            return
        }

        plugin.menuEntryRouter.openVisitMenu(player, target)
    }

    private fun resolveTargetPlayer(targetName: String): org.bukkit.OfflinePlayer? {
        Bukkit.getPlayerExact(targetName)?.let { return it }

        val offlinePlayer = Bukkit.getOfflinePlayer(targetName)
        return if (offlinePlayer.hasPlayedBefore()) offlinePlayer else null
    }

    private fun collectVisitableWorlds(player: Player, targetUuid: UUID): List<WorldData> {
        return plugin.worldConfigRepository.findAll().filter { world ->
            if (world.owner != targetUuid || world.isArchived) {
                return@filter false
            }

            val isMember =
                world.owner == player.uniqueId ||
                    world.moderators.contains(player.uniqueId) ||
                    world.members.contains(player.uniqueId)

            world.publishLevel == PublishLevel.PUBLIC || isMember
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (!PermissionManager.checkPermission(sender, PermissionManager.CITIZEN)) return emptyList()
        if (sender !is Player) return emptyList()
        
        if (args.size == 1) {
            val search = args[0].lowercase()
            return Bukkit.getOnlinePlayers()
                .filter { target ->
                    target != sender && collectVisitableWorlds(sender, target.uniqueId).isNotEmpty()
                }
                .map { it.name }
                .filter { it.lowercase().startsWith(search) }
        }
        return emptyList()
    }
}
