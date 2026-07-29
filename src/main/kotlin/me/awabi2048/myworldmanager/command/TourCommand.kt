package me.awabi2048.myworldmanager.command

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.util.PermissionManager
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class TourCommand(private val plugin: MyWorldManager) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!PermissionManager.checkPermission(sender, PermissionManager.COMMAND_TOUR)) {
            PermissionManager.sendNoPermissionMessage(sender)
            return true
        }
        val player = sender as? Player ?: return true
        val worldData = plugin.worldConfigRepository.findByWorldName(player.world.name)
        if (worldData == null) {
            player.sendMessage(plugin.languageManager.getMessage(player, "error.tour.not_managed_world"))
            return true
        }
        if (!plugin.tourManager.hasValidTour(worldData)) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.tour.none_available"))
            return true
        }
        plugin.tourGui.openVisitorMenu(player, worldData)
        return true
    }
}
