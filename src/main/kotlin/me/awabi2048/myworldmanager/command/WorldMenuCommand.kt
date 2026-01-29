package me.awabi2048.myworldmanager.command

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.util.PermissionManager
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.UUID

class WorldMenuCommand(private val plugin: MyWorldManager) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!PermissionManager.checkPermission(sender, PermissionManager.CITIZEN)) {
            PermissionManager.sendNoPermissionMessage(sender)
            return true
        }
        if (sender !is Player) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行可能です。")
            return true
        }

        val player = sender
        val currentWorld = player.world
        
        // ワールド名からマイワールドデータを取得
        val worldData = plugin.worldConfigRepository.findByWorldName(currentWorld.name)
        if (worldData == null) {
            player.sendMessage("§cここはマイワールドではありません。")
            return true
        }

        // 権限チェック (メンバー以上)
        val isOwner = worldData.owner == player.uniqueId
        val isModerator = worldData.moderators.contains(player.uniqueId)
        val isMember = worldData.members.contains(player.uniqueId)

        if (!isOwner && !isModerator && !isMember) {
            player.sendMessage("§cこのコマンドを実行する権限がありません。（メンバー以上が必要）")
            return true
        }

        // GUIを開く
        plugin.worldSettingsGui.open(player, worldData)
        return true
    }
}
