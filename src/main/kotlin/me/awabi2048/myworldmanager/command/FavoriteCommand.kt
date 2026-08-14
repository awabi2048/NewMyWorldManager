package me.awabi2048.myworldmanager.command

import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.util.PermissionManager
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.UUID

class FavoriteCommand(private val plugin: MyWorldManager) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!PermissionManager.checkPermission(sender, PermissionManager.COMMAND_FAVORITE)) {
            PermissionManager.sendNoPermissionMessage(sender)
            return true
        }
        if (sender !is Player) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行可能です。")
            return true
        }

        if (args.firstOrNull().equals("cancel_batch", ignoreCase = true)) {
            val batchId = args.getOrNull(1)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            val result = batchId?.let { plugin.favoriteGroupInviteService.cancel(sender, it) }
            when (result) {
                is me.awabi2048.myworldmanager.service.FavoriteGroupInviteService.CancelResult.Cancelled ->
                    sender.sendMessage(plugin.languageManager.getMessage(
                        sender,
                        MyworldMessagesKeys.MESSAGES_FAVORITE_GROUP_INVITE_SENDER_CANCELLED,
                        mapOf("count" to result.count),
                    ))
                else -> sender.sendMessage(
                    plugin.languageManager.getMessage(sender, MyworldMessagesKeys.MESSAGES_FAVORITE_GROUP_INVITE_SENDER_CANCEL_NOT_FOUND),
                )
            }
            return true
        }

        // 前段メニューは廃止し、現在ワールドの文脈を持った一覧へ直接入ります。
        plugin.menuEntryRouter.openFavoriteList(
            sender,
            0,
            showBackButton = args.contains("-menu"),
        )
        return true
    }
}
