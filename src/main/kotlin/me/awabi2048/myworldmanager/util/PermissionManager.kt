package me.awabi2048.myworldmanager.util

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object PermissionManager {
    const val ADMIN = "myworldmanager.admin"
    const val CITIZEN = "myworldmanager.citizen"
    const val TEST = "craftercrossing.test"

    /**
     * 指定された権限、または上位の権限を持っているかチェックします。
     * craftercrossing.test は常に admin と同等に扱われます。
     */
    fun checkPermission(sender: CommandSender, permission: String): Boolean {
        if (sender !is Player) return true // コンソールは常に全権限
        
        // テスト権限または管理者権限を持っている場合、すべての mwm 権限をパス
        if (sender.hasPermission(TEST) || sender.hasPermission(ADMIN)) {
            return true
        }

        // 市民権限を要求している場合
        if (permission == CITIZEN) {
            return sender.hasPermission(CITIZEN)
        }

        // それ以外の権限（将来用）
        return sender.hasPermission(permission)
    }

    /**
     * 権限エラー時のメッセージを送信します。
     * IDEAS.mdの要件に基づき、"Unknown command" 形式で送信します。
     */
    fun sendNoPermissionMessage(sender: CommandSender) {
        sender.sendMessage("§cUnknown command. Type \"/help\" for help.")
    }
}
