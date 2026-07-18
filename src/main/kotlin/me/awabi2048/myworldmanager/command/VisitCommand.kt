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
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.repository.*
import me.awabi2048.myworldmanager.util.PermissionManager
import me.awabi2048.myworldmanager.util.PlayerNameUtil
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
        if (!PermissionManager.checkPermission(sender, PermissionManager.COMMAND_VISIT)) {
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

        processVisitTargetInput(sender, args[0], args.getOrNull(1))
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

    private fun processVisitTargetInput(player: Player, rawInput: String, rawWorldInput: String? = null) {
        val targetName = rawInput.trim()
        if (targetName.isEmpty()) {
            player.sendMessage(plugin.languageManager.getMessage(player, "general.player_not_found"))
            return
        }

        val target = PlayerNameUtil.resolveOfflinePlayer(plugin, targetName)

        if (target == null) {
            player.sendMessage("§cプレイヤー「$targetName」が見つかりませんでした。")
            return
        }

        if (target.uniqueId == player.uniqueId) {
            player.sendMessage("§c自分自身のワールド一覧を表示することはできません。/myworld を使用してください。")
            return
        }

        val worldName = rawWorldInput?.trim().orEmpty()
        if (worldName.isNotEmpty()) {
            val worldData = resolveTargetWorld(target.uniqueId, worldName)
            if (worldData == null || worldData.isArchived) {
                player.sendMessage(plugin.languageManager.getMessage(player, "general.world_not_found"))
                return
            }
            handleDirectWorldVisit(player, target.name ?: targetName, worldData)
            return
        }

        val visitableWorlds = collectVisitableWorlds(player, target.uniqueId)

        if (visitableWorlds.isEmpty()) {
            player.sendMessage("§cプレイヤー「$targetName」の訪問可能なワールドが見つかりませんでした。")
            return
        }

        plugin.menuEntryRouter.openVisitMenu(player, target)
    }

    private fun resolveTargetWorld(ownerUuid: UUID, rawWorldInput: String): WorldData? {
        val query = rawWorldInput.trim()
        val queryUuid = runCatching { UUID.fromString(query) }.getOrNull()
        return plugin.worldConfigRepository.findAll()
            .filter { it.owner == ownerUuid }
            .firstOrNull { world ->
                world.uuid == queryUuid ||
                    world.name.equals(query, ignoreCase = true) ||
                    plugin.worldService.getWorldFolderName(world).equals(query, ignoreCase = true)
            }
    }

    private fun handleDirectWorldVisit(player: Player, ownerName: String, worldData: WorldData) {
        val isMember = worldData.owner == player.uniqueId ||
            worldData.moderators.contains(player.uniqueId) ||
            worldData.members.contains(player.uniqueId)
        val accessPolicy = MyWorldManagerApi.getWorldAccessPolicy()

        if (accessPolicy.canEnterWorld(player, worldData, isMember) &&
            accessPolicy.canUseVisitEntry(player, worldData, isMember)
        ) {
            plugin.worldService.teleportToWorld(player, worldData.uuid)
            return
        }

        requestVisitPermission(player, ownerName, worldData)
    }

    private fun requestVisitPermission(player: Player, ownerName: String, worldData: WorldData) {
        val respondent = visitRequestRespondents(worldData, player.uniqueId).firstOrNull()
        if (respondent == null) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.visit_request_no_respondent"))
            return
        }

        val timeoutSeconds = plugin.config.getLong("invite.timeout_seconds", 60L).coerceAtLeast(1L)
        val result = plugin.pendingDecisionManager.enqueueVisitRequest(
            target = respondent,
            requesterUuid = player.uniqueId,
            worldUuid = worldData.uuid,
            timeoutSeconds = timeoutSeconds
        )
        if (result == null) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.visit_request_already_pending"))
            return
        }

        player.sendMessage(
            plugin.languageManager.getMessage(
                player,
                "messages.visit_request_sent",
                mapOf("owner" to ownerName, "world" to worldData.name)
            )
        )
        plugin.pendingNotificationService.send(
            respondent,
            me.awabi2048.myworldmanager.service.PendingDecisionManager.PendingType.VISIT_REQUEST,
            result.actionCode,
            player.uniqueId,
            worldData.uuid
        )
    }

    private fun visitRequestRespondents(worldData: WorldData, requesterUuid: UUID): List<Player> {
        // 制作中ワールドへの一時訪問は、ワールドを管理できるオンラインプレイヤーにだけ承認させる。
        val respondentIds = buildList {
            add(worldData.owner)
            addAll(worldData.moderators)
            addAll(worldData.members)
        }
        return respondentIds
            .asSequence()
            .filter { it != requesterUuid }
            .distinct()
            .mapNotNull(Bukkit::getPlayer)
            .filter { it.isOnline }
            .toList()
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

            MyWorldManagerApi.getWorldAccessPolicy().canUseVisitEntry(player, world, isMember)
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (!PermissionManager.checkPermission(sender, PermissionManager.COMMAND_VISIT)) return emptyList()
        if (sender !is Player) return emptyList()
        
        if (args.size == 1) {
            val search = args[0].lowercase()
            return plugin.playerVisibilityService.getVisibleOnlinePlayers(sender)
                .filter { target ->
                    target != sender && collectVisitableWorlds(sender, target.uniqueId).isNotEmpty()
                }
                .map { it.name }
                .filter { it.lowercase().startsWith(search) }
        }
        if (args.size == 2) {
            val target = PlayerNameUtil.resolveOfflinePlayer(plugin, args[0]) ?: return emptyList()
            val search = args[1].lowercase()
            return plugin.worldConfigRepository.findAll()
                .filter { it.owner == target.uniqueId && !it.isArchived }
                .map { it.name }
                .filter { it.lowercase().startsWith(search) }
        }
        return emptyList()
    }
}
