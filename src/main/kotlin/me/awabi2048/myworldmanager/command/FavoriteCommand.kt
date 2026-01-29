package me.awabi2048.myworldmanager.command

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.FavoriteGui
import me.awabi2048.myworldmanager.util.PermissionManager
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class FavoriteCommand(private val plugin: MyWorldManager) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!PermissionManager.checkPermission(sender, PermissionManager.CITIZEN)) {
            PermissionManager.sendNoPermissionMessage(sender)
            return true
        }
        if (sender !is Player) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行可能です。")
            return true
        }

        if (args.contains("-menu")) {
            plugin.favoriteGui.open(sender, 0, null, true)
            return true
        }

        val worldName = sender.world.name
        if (worldName.startsWith("my_world.")) {
            val uuidStr = worldName.removePrefix("my_world.")
            val worldUuid = try { java.util.UUID.fromString(uuidStr) } catch (e: Exception) { null }
            if (worldUuid != null) {
                val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
                if (worldData != null) {
                    plugin.favoriteMenuGui.open(sender, worldData)
                    return true
                }
            }
        }

        // 管理外ワールドでもメニューは開く
        plugin.favoriteMenuGui.open(sender, null)
        return true
    }
}
