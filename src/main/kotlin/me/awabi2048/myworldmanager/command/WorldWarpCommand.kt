package me.awabi2048.myworldmanager.command

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.PermissionManager
import me.awabi2048.myworldmanager.util.WorldWarpId
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.UUID

class WorldWarpCommand(private val plugin: MyWorldManager) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!PermissionManager.checkPermission(sender, PermissionManager.COMMAND_WORLDWARP)) {
            PermissionManager.sendNoPermissionMessage(sender)
            return true
        }
        if (sender !is Player) {
            sender.sendMessage(plugin.languageManager.getMessage("general.player_only"))
            return true
        }
        if (args.size != 1) {
            sender.sendMessage(plugin.languageManager.getMessage(sender, "messages.worldwarp_usage"))
            return true
        }

        val input = args[0].trim()
        val resolved = resolveWorld(input)
        when (resolved) {
            ResolveResult.NotFound -> sender.sendMessage(plugin.languageManager.getMessage(sender, "messages.worldwarp_not_found"))
            ResolveResult.Ambiguous -> sender.sendMessage(plugin.languageManager.getMessage(sender, "messages.worldwarp_ambiguous"))
            is ResolveResult.Found -> warp(sender, resolved.worldData)
        }
        return true
    }

    private fun warp(player: Player, worldData: WorldData) {
        val isMember = worldData.owner == player.uniqueId ||
            worldData.moderators.contains(player.uniqueId) ||
            worldData.members.contains(player.uniqueId)
        val accessPolicy = MyWorldManagerApi.getWorldAccessPolicy()
        if (accessPolicy.canDirectWorldWarp(player, worldData, isMember)) {
            plugin.worldService.teleportToWorld(player, worldData.uuid) {
                player.sendMessage(plugin.languageManager.getMessage(player, "messages.warp_success", mapOf("world" to worldData.name)))
            }
            return
        }

        if (accessPolicy.canRequestWorldWarp(player, worldData, isMember)) {
            requestVisitPermission(player, worldData)
            return
        }

        if (!accessPolicy.canEnterWorld(player, worldData, isMember)) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.worldwarp_access_denied"))
            return
        }
        player.sendMessage(plugin.languageManager.getMessage(player, "messages.worldwarp_access_denied"))
    }

    private fun requestVisitPermission(player: Player, worldData: WorldData) {
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
                mapOf("owner" to respondent.name, "world" to worldData.name)
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
        return buildList {
            add(worldData.owner)
            addAll(worldData.moderators)
            addAll(worldData.members)
        }
            .asSequence()
            .filter { it != requesterUuid }
            .distinct()
            .mapNotNull(Bukkit::getPlayer)
            .filter { it.isOnline }
            .toList()
    }

    private fun resolveWorld(rawId: String): ResolveResult {
        val normalized = rawId.trim()
        if (!normalized.matches(ID_PATTERN)) {
            return ResolveResult.NotFound
        }
        val matches = plugin.worldConfigRepository.findAll()
            .filter { WorldWarpId.of(it.uuid) == normalized }
        return when (matches.size) {
            0 -> ResolveResult.NotFound
            1 -> ResolveResult.Found(matches.first())
            else -> ResolveResult.Ambiguous
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (!PermissionManager.checkPermission(sender, PermissionManager.COMMAND_WORLDWARP)) return emptyList()
        if (args.size != 1) return emptyList()
        val input = args[0]
        return plugin.worldConfigRepository.findAll()
            .asSequence()
            .map { WorldWarpId.of(it.uuid) }
            .filter { it.startsWith(input) }
            .take(20)
            .toList()
    }

    private sealed interface ResolveResult {
        data object NotFound : ResolveResult
        data object Ambiguous : ResolveResult
        data class Found(val worldData: WorldData) : ResolveResult
    }

    private companion object {
        val ID_PATTERN = Regex("\\d{6}")
    }
}
