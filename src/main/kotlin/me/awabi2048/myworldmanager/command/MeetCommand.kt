package me.awabi2048.myworldmanager.command

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.util.PermissionManager
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import me.awabi2048.myworldmanager.service.PendingDecisionManager

class MeetCommand(private val plugin: MyWorldManager) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!PermissionManager.checkPermission(sender, PermissionManager.COMMAND_MEET)) {
            PermissionManager.sendNoPermissionMessage(sender)
            return true
        }
        if (sender !is Player) return true
        
        if (args.contains("-menu")) {
            plugin.menuEntryRouter.openMeet(sender, true)
            return true
        }

        if (args.isEmpty()) {
            plugin.menuEntryRouter.openMeet(sender)
            return true
        }
        
        // Handling /meet accept <requester_name/uuid>
        if (args[0].equals("accept", ignoreCase = true)) {
            if (args.size < 2) return true
            
            val targetName = args[1]
            val requesterUuid = try {
                 java.util.UUID.fromString(targetName)
            } catch (e: Exception) {
                PlayerNameUtil.resolveOnlinePlayer(plugin, targetName)?.uniqueId
            } ?: return true
            
            val pendingRequest = plugin.pendingDecisionManager.getPendingEntries(sender.uniqueId)
                .firstOrNull {
                    it.type == PendingDecisionManager.PendingType.MEET_REQUEST &&
                        it.actorUuid == requesterUuid
                }
            if (pendingRequest == null) {
                sender.sendMessage(plugin.languageManager.getMessage(sender, "messages.meet.no_pending_request"))
                return true
            }

            plugin.pendingInteractionGui.openDecision(sender, pendingRequest.id)
            return true
        }
        
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
        return emptyList()
    }
}
