package me.awabi2048.myworldmanager.listener

import io.papermc.paper.event.player.AsyncChatEvent
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.command.InviteCommand
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class InviteChatListener(
    private val plugin: MyWorldManager,
    private val inviteCommand: InviteCommand
) : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        // セッション存在確認のみを行い、変数は使用しない
        plugin.inviteSessionManager.getSession(player.uniqueId) ?: return

        event.isCancelled = true
        event.viewers().clear()
        val targetName = PlainTextComponentSerializer.plainText().serialize(event.originalMessage()).trim()
        val lang = plugin.languageManager

        // 判定はメインスレッドで実行
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!player.isOnline) {
                plugin.inviteSessionManager.endSession(player.uniqueId)
                return@Runnable
            }

            val target = Bukkit.getPlayerExact(targetName)
            if (target == null) {
                player.sendMessage(lang.getMessage(player, "messages.invite_target_offline", mapOf("player" to targetName)))
                plugin.inviteSessionManager.endSession(player.uniqueId)
                return@Runnable
            }

            if (target == player) {
                player.sendMessage(lang.getMessage(player, "messages.invite_self_error"))
                plugin.inviteSessionManager.endSession(player.uniqueId)
                return@Runnable
            }

            // コマンド実行と同様の挙動をさせる
            player.performCommand("invite $targetName")
            plugin.inviteSessionManager.endSession(player.uniqueId)
        })
    }
}
