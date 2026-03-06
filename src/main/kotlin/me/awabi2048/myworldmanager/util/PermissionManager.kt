package me.awabi2048.myworldmanager.util

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object PermissionManager {
    const val ADMIN = "myworldmanager.admin"
    const val ADMIN_WORLD_LIST = "myworldmanager.admin.world_list"
    const val ADMIN_BYPASS_WORLD_LIMITS = "myworldmanager.admin.bypass_world_limits"
    const val FORCE_ADD_MEMBER = "myworldmanager.force_add_member"
    const val CITIZEN = "myworldmanager.citizen"
    const val COMMAND_DISCOVERY = "myworldmanager.command.discovery"
    const val COMMAND_FAVORITE = "myworldmanager.command.favorite"
    const val COMMAND_FINDWORLD = "myworldmanager.command.findworld"
    const val COMMAND_INVITE = "myworldmanager.command.invite"
    const val COMMAND_MEET = "myworldmanager.command.meet"
    const val COMMAND_MYWORLD = "myworldmanager.command.myworld"
    const val COMMAND_SETTINGS = "myworldmanager.command.settings"
    const val COMMAND_VISIT = "myworldmanager.command.visit"
    const val COMMAND_WORLDMENU = "myworldmanager.command.worldmenu"
    const val COMMAND_MWM = "myworldmanager.command.mwm"
    const val COMMAND_MWM_CREATE = "myworldmanager.command.mwm.create"
    const val COMMAND_MWM_RELOAD = "myworldmanager.command.mwm.reload"
    const val COMMAND_MWM_STATS = "myworldmanager.command.mwm.stats"
    const val COMMAND_MWM_GIVE = "myworldmanager.command.mwm.give"
    const val COMMAND_MWM_LIST = "myworldmanager.command.mwm.list"
    const val COMMAND_MWM_INTERNAL = "myworldmanager.command.mwm_internal"
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

    fun canBypassWorldLimits(sender: CommandSender): Boolean {
        return sender.hasPermission(ADMIN_BYPASS_WORLD_LIMITS)
    }

    fun canForceAddMember(sender: CommandSender): Boolean {
        return checkPermission(sender, FORCE_ADD_MEMBER)
    }

    fun checkAnyPermission(sender: CommandSender, vararg permissions: String): Boolean {
        return permissions.any { checkPermission(sender, it) }
    }
}
