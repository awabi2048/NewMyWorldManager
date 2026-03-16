package me.awabi2048.myworldmanager.command

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.repository.*
import me.awabi2048.myworldmanager.util.ClickableInviteMessageFactory
import me.awabi2048.myworldmanager.util.PermissionManager
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.UUID

class InviteCommand(private val plugin: MyWorldManager) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!PermissionManager.checkPermission(sender, PermissionManager.COMMAND_INVITE)) {
            PermissionManager.sendNoPermissionMessage(sender)
            return true
        }
        val lang = plugin.languageManager
        if (sender !is Player) {
            sender.sendMessage(lang.getMessage("general.player_only"))
            return true
        }

        val player = sender
        val worldData = resolveCurrentWorldData(player) ?: run {
            player.sendMessage(lang.getMessage(player, "messages.invite_not_in_myworld"))
            return true
        }

        // ワールドメンバーチェック
        val isMember = worldData.owner == player.uniqueId ||
            worldData.moderators.contains(player.uniqueId) ||
            worldData.members.contains(player.uniqueId)
        if (!isMember) {
            player.sendMessage(lang.getMessage(player, "messages.invite_not_member"))
            return true
        }

        // 封鎖中チェック
        if (worldData.publishLevel == PublishLevel.LOCKED) {
            player.sendMessage(lang.getMessage(player, "error.invite_locked_error"))
            return true
        }

        if (args.isEmpty()) {
            plugin.inviteGui.open(player)
            return true
        }

        val targetName = args[0]
        val target = PlayerNameUtil.resolveOnlinePlayer(plugin, targetName)

        if (target == null) {
            player.sendMessage(lang.getMessage(player, "messages.invite_target_offline", mapOf("player" to targetName)))
            return true
        }

        if (target.uniqueId == player.uniqueId) {
            player.sendMessage(lang.getMessage(player, "messages.invite_self_error"))
            return true
        }

        // 招待の有効期限を設定
        val timeoutSeconds = plugin.config.getLong("invite.timeout_seconds", 60)

        if (plugin.playerPlatformResolver.isBedrock(target)) {
            target.sendMessage(
                lang.getMessage(
                    target,
                    "messages.invite_text",
                    mapOf("player" to player.name, "world" to worldData.name)
                )
            )
            val result = plugin.pendingDecisionManager.enqueueWorldInvite(
                target,
                worldData.uuid,
                player.uniqueId,
                timeoutSeconds
            )
            plugin.pendingDecisionManager.sendPendingHint(target, result.count)
        } else {
            val result = plugin.pendingDecisionManager.enqueueWorldInvite(
                target,
                worldData.uuid,
                player.uniqueId,
                timeoutSeconds
            )
            target.sendMessage(
                ClickableInviteMessageFactory.create(
                    plugin = plugin,
                    target = target,
                    messageKey = "messages.invite_text",
                    clickTextKey = "messages.invite_click_text",
                    hoverTextKey = "messages.invite_hover",
                    placeholders = mapOf("player" to player.name, "world" to worldData.name),
                    arguments = listOf("/myworld", "pending_open", result.id.toString())
                )
            )
        }

        // 実行者へのメッセージ送信
        player.sendMessage(lang.getMessage(player, "messages.invite_sent_success", mapOf("player" to target.name, "world" to worldData.name)))

        return true
    }

    private fun resolveCurrentWorldData(player: Player): WorldData? {
        val currentWorld = player.world
        plugin.worldConfigRepository.findByWorldName(currentWorld.name)?.let { return it }

        if (!currentWorld.name.startsWith("my_world.")) {
            return null
        }

        val uuidStr = currentWorld.name.removePrefix("my_world.")
        val worldUuid = try {
            UUID.fromString(uuidStr)
        } catch (e: Exception) {
            return null
        }

        return plugin.worldConfigRepository.findByUuid(worldUuid)
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
        if (!PermissionManager.checkPermission(sender, PermissionManager.COMMAND_INVITE)) return emptyList()
        if (args.size == 1) {
            val search = args[0].lowercase()
            return Bukkit.getOnlinePlayers()
                .filter {
                    it.name.lowercase().startsWith(search) &&
                        it.name != sender.name &&
                        plugin.playerStatsRepository.findByUuid(it.uniqueId).meetStatus != "BUSY"
                }
                .map { it.name }
        }
        return emptyList()
    }

    fun handleAccept(player: Player) {
        val latestInvite = plugin.pendingDecisionManager.getPendingEntries(player.uniqueId)
            .firstOrNull { it.type == me.awabi2048.myworldmanager.service.PendingDecisionManager.PendingType.WORLD_INVITE }
        if (latestInvite == null) {
            player.sendMessage(plugin.languageManager.getMessage(player, "error.invite_expired"))
            return
        }
        plugin.pendingInteractionGui.openDecision(player, latestInvite.id)
    }
}
