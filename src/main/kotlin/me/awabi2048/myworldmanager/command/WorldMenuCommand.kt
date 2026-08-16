package me.awabi2048.myworldmanager.command

import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.PermissionManager
import com.awabi2048.ccsystem.CCSystem
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class WorldMenuCommand(private val plugin: MyWorldManager) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!PermissionManager.checkPermission(sender, PermissionManager.COMMAND_WORLDMENU)) {
            PermissionManager.sendNoPermissionMessage(sender)
            return true
        }
        if (sender !is Player) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行可能です。")
            return true
        }
        return CCSystem.getAPI().getMenuCommandService().open(
            sender,
            sender,
            "myworld:world-settings",
            if (args.contains("-menu")) mapOf("back" to "true") else emptyMap()
        )
    }

    fun openCurrent(player: Player, showBackButton: Boolean): Boolean {
        val currentWorld = player.world

        // ワールド名からマイワールドデータを取得
        val worldData = plugin.worldConfigRepository.findByWorldName(currentWorld.name)
        if (worldData == null) {
            player.sendMessage(plugin.languageManager.getMessage(player, MyworldMessagesKeys.MESSAGES_WORLDMENU_NOT_IN_MYWORLD))
            return false
        }

        // 権限チェック (メンバー以上)
        val isOwner = worldData.owner == player.uniqueId
        val isModerator = worldData.moderators.contains(player.uniqueId)
        val isMember = worldData.members.contains(player.uniqueId)
        val isAdminFlow = PermissionManager.checkPermission(player, PermissionManager.ADMIN)
        if (!isOwner && !isModerator && !isMember && !isAdminFlow) {
            val challenge = MyWorldManagerApi.getWorldMenuAccessChallenge(player, worldData)
            if (challenge != null &&
                CCSystem.getAPI().getMenuCommandService().open(
                    player,
                    player,
                    challenge.publicMenuRouteId,
                    challenge.arguments,
                )
            ) return true
            player.sendMessage(plugin.languageManager.getMessage(player, MyworldMessagesKeys.MESSAGES_WORLDMENU_MEMBERS_ONLY))
            return true
        }

        // ブロック入力中のアクションをキャンセル
        cancelBlockInputAction(player)

        // GUIを開く
        // 管理者が現地確認で開く /worldmenu は所有者権限の代替として扱う。
        // 後続の設定画面は SettingsSession.isAdminFlow を参照して所有者限定操作を許可する。
        plugin.settingsSessionManager.updateSessionAction(
            player,
            worldData.uuid,
            SettingsAction.VIEW_SETTINGS,
            isGui = true,
            isAdminFlow = isAdminFlow
        )
        plugin.menuEntryRouter.openWorldSettings(player, worldData, showBackButton)
        return true
    }

    private fun cancelBlockInputAction(player: Player) {
        val session = plugin.settingsSessionManager.getSession(player) ?: return
        val blockInputActions = setOf(
                SettingsAction.SET_SPAWN_GUEST,
                SettingsAction.SET_SPAWN_MEMBER,
                SettingsAction.EXPAND_DIRECTION_WAIT,
                SettingsAction.EXPAND_DIRECTION_CONFIRM
        )
        if (session.action in blockInputActions) {
            player.sendMessage("§c設定をキャンセルしました")
        }
    }
}
