package me.awabi2048.myworldmanager.command

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.event.MwmMemberRemoveSource
import me.awabi2048.myworldmanager.api.event.MwmMemberRemovedEvent
import me.awabi2048.myworldmanager.api.event.MwmOwnerTransferSource
import me.awabi2048.myworldmanager.api.event.MwmOwnerTransferredEvent
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.PermissionManager
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.UUID

class PlayerWorldCommand(private val plugin: MyWorldManager) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (!PermissionManager.checkPermission(sender, PermissionManager.COMMAND_MYWORLD)) {
            PermissionManager.sendNoPermissionMessage(sender)
            return true
        }
        if (sender !is Player) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行可能です。")
            return true
        }

        if (args.isEmpty() || (args.size == 1 && args[0].equals("-menu", ignoreCase = true))) {
            val showBackButton = args.any { it.equals("-menu", ignoreCase = true) }
            plugin.menuEntryRouter.openPlayerWorld(sender, 0, showBackButton)
            return true
        }

        val sub = args[0].lowercase()
        if (sub == "pending_open") {
            if (args.size < 2) {
                sender.sendMessage(plugin.languageManager.getMessage(sender, "messages.myworld_pending_none"))
                return true
            }
            val decisionId = runCatching { UUID.fromString(args[1]) }.getOrNull()
            if (decisionId == null) {
                sender.sendMessage(plugin.languageManager.getMessage(sender, "messages.myworld_pending_none"))
                return true
            }
            plugin.soundManager.playChatClickSound(sender)
            plugin.pendingInteractionGui.openDecision(sender, decisionId)
            return true
        }

        if (!plugin.playerPlatformResolver.isBedrock(sender)) {
            sender.sendMessage(plugin.languageManager.getMessage(sender, "messages.myworld_command_bedrock_only"))
            return true
        }

        if (sub != "transfer" && sub != "remove_member" && sub != "accept" && sub != "deny") {
            sender.sendMessage(plugin.languageManager.getMessage(sender, "messages.myworld_command_usage"))
            return true
        }

        if (sub == "accept" || sub == "deny") {
            val singlePending = plugin.pendingDecisionManager.getSinglePendingCandidate(sender.uniqueId)
            if (singlePending == null) {
                val pendingCount = plugin.pendingDecisionManager.getPendingCount(sender.uniqueId)
                if (pendingCount < 1) {
                    sender.sendMessage(plugin.languageManager.getMessage(sender, "messages.myworld_pending_none"))
                } else {
                    sender.sendMessage(plugin.languageManager.getMessage(sender, "messages.myworld_pending_open_list"))
                    plugin.pendingInteractionGui.open(sender)
                }
                return true
            }

            plugin.pendingInteractionGui.openDecision(sender, singlePending.id, intendedAction = sub == "accept")
            return true
        }

        if (args.size < 2) {
            sender.sendMessage(plugin.languageManager.getMessage(sender, "messages.myworld_command_usage"))
            return true
        }

        val worldData = plugin.worldConfigRepository.findByWorldName(sender.world.name)
        if (worldData == null) {
            sender.sendMessage(plugin.languageManager.getMessage(sender, "messages.no_in_myworld"))
            return true
        }

        if (worldData.owner != sender.uniqueId) {
            sender.sendMessage(plugin.languageManager.getMessage(sender, "error.no_permission"))
            return true
        }

        val target = PlayerNameUtil.resolveOnlinePlayer(plugin, args[1])
        if (target == null || !target.isOnline) {
            sender.sendMessage(
                plugin.languageManager.getMessage(
                    sender,
                    "messages.invite_target_offline",
                    mapOf("player" to args[1])
                )
            )
            return true
        }

        return when (sub) {
            "transfer" -> {
                handleBedrockTransfer(sender, worldData, target)
                true
            }
            "remove_member" -> {
                handleBedrockRemoveMember(sender, worldData, target)
                true
            }
            else -> true
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (!PermissionManager.checkPermission(sender, PermissionManager.COMMAND_MYWORLD)) {
            return emptyList()
        }
        if (sender !is Player) {
            return emptyList()
        }
        if (!plugin.playerPlatformResolver.isBedrock(sender)) {
            return emptyList()
        }

        return when (args.size) {
            1 -> {
                val commands = mutableListOf("transfer", "remove_member")
                if (plugin.pendingDecisionManager.getSinglePendingCandidate(sender.uniqueId) != null) {
                    commands += listOf("accept", "deny")
                }
                commands.filter { it.startsWith(args[0].lowercase()) }
            }
            2 -> {
                val sub = args[0].lowercase()
                if (sub == "accept" || sub == "deny") {
                    return emptyList()
                }
                val currentWorld = plugin.worldConfigRepository.findByWorldName(sender.world.name) ?: return emptyList()
                if (currentWorld.owner != sender.uniqueId) {
                    return emptyList()
                }
                val names =
                    when (sub) {
                        "transfer" -> eligibleTransferTargets(currentWorld)
                        "remove_member" -> eligibleRemoveTargets(currentWorld)
                        else -> emptyList()
                    }
                names.filter { it.startsWith(args[1], ignoreCase = true) }
            }
            else -> emptyList()
        }
    }

    private fun handleBedrockTransfer(player: Player, worldData: WorldData, target: Player) {
        if (target.uniqueId == player.uniqueId) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.invite_self_error"))
            return
        }

        val targetId = target.uniqueId
        val isMember = worldData.members.contains(targetId) || worldData.moderators.contains(targetId)
        if (!isMember) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.myworld_target_not_member"))
            return
        }

        val defaultMax = plugin.config.getInt("creation.max_create_count_default", 3)
        val stats = plugin.playerStatsRepository.findByUuid(targetId)
        val maxCounts = defaultMax + stats.unlockedWorldSlot
        val currentCounts = plugin.worldConfigRepository.findAll().count { it.owner == targetId }
        if (!PermissionManager.canBypassWorldLimits(player) && currentCounts >= maxCounts) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.owner_transfer_failed_limit"))
            return
        }

        val oldOwnerId = worldData.owner
        val oldOwnerName = PlayerNameUtil.getNameOrDefault(oldOwnerId, "Unknown")
        val newOwnerName = PlayerNameUtil.getNameOrDefault(targetId, "Unknown")

        worldData.owner = targetId
        if (!worldData.moderators.contains(oldOwnerId)) {
            worldData.moderators.add(oldOwnerId)
        }
        worldData.moderators.remove(targetId)
        worldData.members.remove(targetId)
        plugin.worldConfigRepository.save(worldData)

        Bukkit.getPluginManager().callEvent(
            MwmOwnerTransferredEvent(
                worldUuid = worldData.uuid,
                oldOwnerUuid = oldOwnerId,
                oldOwnerName = oldOwnerName,
                newOwnerUuid = targetId,
                newOwnerName = newOwnerName,
                transferredByUuid = player.uniqueId,
                source = MwmOwnerTransferSource.MANUAL
            )
        )
        plugin.macroManager.execute(
            "on_owner_transfer",
            mapOf(
                "world_uuid" to worldData.uuid.toString(),
                "old_owner" to oldOwnerName,
                "new_owner" to newOwnerName
            )
        )

        player.sendMessage(
            plugin.languageManager.getMessage(
                player,
                "messages.owner_transferred",
                mapOf("old_owner" to newOwnerName)
            )
        )
    }

    private fun handleBedrockRemoveMember(player: Player, worldData: WorldData, target: Player) {
        val targetId = target.uniqueId
        if (targetId == worldData.owner) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.myworld_owner_remove_forbidden"))
            return
        }

        val isMember = worldData.members.contains(targetId) || worldData.moderators.contains(targetId)
        if (!isMember) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.myworld_target_not_member"))
            return
        }

        val targetName = PlayerNameUtil.getNameOrDefault(targetId, "Unknown")
        worldData.members.remove(targetId)
        worldData.moderators.remove(targetId)
        plugin.worldConfigRepository.save(worldData)

        Bukkit.getPluginManager().callEvent(
            MwmMemberRemovedEvent(
                worldUuid = worldData.uuid,
                memberUuid = targetId,
                memberName = targetName,
                removedByUuid = player.uniqueId,
                source = MwmMemberRemoveSource.MANUAL
            )
        )
        plugin.macroManager.execute(
            "on_member_remove",
            mapOf(
                "world_uuid" to worldData.uuid.toString(),
                "member" to targetName
            )
        )

        player.sendMessage(plugin.languageManager.getMessage(player, "messages.member_deleted"))
    }

    private fun eligibleTransferTargets(worldData: WorldData): List<String> {
        return (worldData.members + worldData.moderators)
            .mapNotNull { Bukkit.getPlayer(it)?.name }
            .distinct()
            .sorted()
    }

    private fun eligibleRemoveTargets(worldData: WorldData): List<String> {
        return eligibleTransferTargets(worldData)
    }
}
