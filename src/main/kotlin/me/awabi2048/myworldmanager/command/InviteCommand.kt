package me.awabi2048.myworldmanager.command

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.repository.*
import me.awabi2048.myworldmanager.util.PermissionManager
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InviteCommand(private val plugin: MyWorldManager) : CommandExecutor, TabCompleter {

    // 招待データを保持する。Key: 被招待者のUUID, Value: 招待情報
    data class InviteInfo(val worldUuid: UUID, val expiry: Long)
    private val pendingInvites = ConcurrentHashMap<UUID, InviteInfo>()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!PermissionManager.checkPermission(sender, PermissionManager.CITIZEN)) {
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
            val count = plugin.pendingDecisionManager.enqueueWorldInvite(
                target,
                worldData.uuid,
                player.uniqueId,
                timeoutSeconds
            )
            plugin.pendingDecisionManager.sendPendingHint(target, count)
        } else {
            val expiry = System.currentTimeMillis() + (timeoutSeconds * 1000)
            pendingInvites[target.uniqueId] = InviteInfo(worldData.uuid, expiry)

            // 対象へのメッセージ送信 (Adventure API)
            val inviteText = lang.getMessage(target, "messages.invite_text", mapOf("player" to player.name, "world" to worldData.name))
            val clickText = lang.getMessage(target, "messages.invite_click_text")
            val hoverText = lang.getMessage(target, "messages.invite_hover")

            val inviteMessage = Component.text()
                .append(Component.text(inviteText))
                .append(Component.newline())
                .append(Component.text(clickText, NamedTextColor.AQUA)
                    .decoration(TextDecoration.UNDERLINED, true)
                     .clickEvent(ClickEvent.runCommand("/mwm_internal inviteaccept"))
                    .hoverEvent(HoverEvent.showText(Component.text(hoverText))))
                .build()

            target.sendMessage(inviteMessage)
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
        if (!PermissionManager.checkPermission(sender, PermissionManager.CITIZEN)) return emptyList()
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

    /**
     * 内部的な招待受理コマンドの処理
     */
    fun handleAccept(player: Player) {
        val lang = plugin.languageManager
        val info = pendingInvites[player.uniqueId]
        
        if (info == null || System.currentTimeMillis() > info.expiry) {
            pendingInvites.remove(player.uniqueId)
            player.sendMessage(lang.getMessage(player, "error.invite_expired"))
            return
        }

        pendingInvites.remove(player.uniqueId)
        
        plugin.worldService.teleportToWorld(player, info.worldUuid)
        player.sendMessage(lang.getMessage(player, "messages.warp_invite_success"))
    }
}
