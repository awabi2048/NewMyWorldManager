package me.awabi2048.myworldmanager.command

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.repository.*
import me.awabi2048.myworldmanager.util.PermissionManager
import me.awabi2048.myworldmanager.util.InviteTargetResolver
import me.awabi2048.myworldmanager.util.WorldAccessMessageResolver
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
            sender.sendMessage(lang.getMessage(CommonKeys.GENERAL_PLAYER_ONLY))
            return true
        }

        val player = sender
        val worldData = resolveCurrentWorldData(player) ?: run {
            player.sendMessage(lang.getMessage(player, MyworldMessagesKeys.MESSAGES_INVITE_NOT_IN_MYWORLD))
            return true
        }

        // ワールドメンバーチェック
        val isMember = worldData.owner == player.uniqueId ||
            worldData.moderators.contains(player.uniqueId) ||
            worldData.members.contains(player.uniqueId)
        if (!isMember) {
            player.sendMessage(lang.getMessage(player, MyworldMessagesKeys.MESSAGES_INVITE_NOT_MEMBER))
            return true
        }

        // 封鎖中チェック
        if (!MyWorldManagerApi.getWorldAccessPolicy().canInviteToWorld(player, worldData)) {
            player.sendMessage(WorldAccessMessageResolver.inviteToWorld(lang, player, worldData))
            return true
        }

        if (args.isEmpty()) {
            plugin.inviteGui.open(player)
            return true
        }

        val targetName = args[0]
        val target = plugin.playerVisibilityService.resolveVisibleOnlinePlayer(player, targetName)

        if (target == null) {
            player.sendMessage(lang.getMessage(player, MyworldMessagesKeys.MESSAGES_INVITE_TARGET_OFFLINE, mapOf("player" to targetName)))
            return true
        }

        when (val reason = InviteTargetResolver.getRejectionReason(plugin, player, worldData, target)) {
            null -> Unit
            else -> {
                val messageKey = InviteTargetResolver.getRejectionMessageKey(reason) ?: return true
                player.sendMessage(lang.getMessage(player, messageKey, mapOf("player" to target.name)))
                return true
            }
        }

        // 招待の有効期限を設定
        val timeoutSeconds = plugin.config.getLong("invite.timeout_seconds", 60)

        val result = plugin.pendingDecisionManager.enqueueWorldInvite(
            target,
            worldData.uuid,
            player.uniqueId,
            timeoutSeconds
        )
        plugin.pendingNotificationService.send(
            target,
            me.awabi2048.myworldmanager.service.PendingDecisionManager.PendingType.WORLD_INVITE,
            result.actionCode,
            player.uniqueId,
            worldData.uuid
        )

        // 実行者へのメッセージ送信
        player.sendMessage(lang.getMessage(player, MyworldMessagesKeys.MESSAGES_INVITE_SENT_SUCCESS, mapOf("player" to target.name, "world" to worldData.name)))

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
        if (sender !is Player) return emptyList()
        if (args.size == 1) {
            val worldData = resolveCurrentWorldData(sender) ?: return emptyList()
            if (!MyWorldManagerApi.getWorldAccessPolicy().canInviteToWorld(sender, worldData)) {
                return emptyList()
            }
            val isMember = worldData.owner == sender.uniqueId ||
                worldData.moderators.contains(sender.uniqueId) ||
                worldData.members.contains(sender.uniqueId)
            if (!isMember) {
                return emptyList()
            }
            val search = args[0].lowercase()
            return InviteTargetResolver.collectAvailableTargets(plugin, sender, worldData)
                .filter { it.name.lowercase().startsWith(search) }
                .map { it.name }
        }
        return emptyList()
    }

    fun handleAccept(player: Player) {
        val latestInvite = plugin.pendingDecisionManager.getPendingEntries(player.uniqueId)
            .firstOrNull { it.type == me.awabi2048.myworldmanager.service.PendingDecisionManager.PendingType.WORLD_INVITE }
        if (latestInvite == null) {
            player.sendMessage(plugin.languageManager.getMessage(player, CommonKeys.ERROR_INVITE_EXPIRED))
            return
        }
        plugin.pendingInteractionGui.openDecision(player, latestInvite.id)
    }
}
