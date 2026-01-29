package me.awabi2048.myworldmanager.command

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.PlayerWorldGui
import me.awabi2048.myworldmanager.util.PermissionManager
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class PlayerWorldCommand(private val plugin: MyWorldManager) : CommandExecutor {

    private val gui = PlayerWorldGui(plugin)

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!PermissionManager.checkPermission(sender, PermissionManager.CITIZEN)) {
            PermissionManager.sendNoPermissionMessage(sender)
            return true
        }
        if (sender !is Player) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行可能です。")
            return true
        }

        gui.open(sender)
        return true
    }
}
