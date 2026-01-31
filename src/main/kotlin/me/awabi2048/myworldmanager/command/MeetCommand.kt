package me.awabi2048.myworldmanager.command

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.MeetGui
import me.awabi2048.myworldmanager.util.PermissionManager
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class MeetCommand(private val plugin: MyWorldManager) : CommandExecutor, TabCompleter {
    private val meetGui = MeetGui(plugin)
    
    companion object {
        // Target UUID -> Requester UUID
        val pendingRequests = mutableMapOf<java.util.UUID, java.util.UUID>()
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!PermissionManager.checkPermission(sender, PermissionManager.CITIZEN)) {
            PermissionManager.sendNoPermissionMessage(sender)
            return true
        }
        if (sender !is Player) return true
        
        if (args.contains("-menu")) {
            meetGui.open(sender, true)
            return true
        }

        if (args.isEmpty()) {
            meetGui.open(sender)
            return true
        }
        
        // Handling /meet accept <requester_name/uuid>
        if (args[0].equals("accept", ignoreCase = true)) {
            if (args.size < 2) return true
            
            val targetName = args[1]
            val requesterUuid = try {
                 java.util.UUID.fromString(targetName)
            } catch (e: Exception) {
                Bukkit.getPlayer(targetName)?.uniqueId
            } ?: return true
            
            // Validate request
            if (pendingRequests[sender.uniqueId] == requesterUuid) {
                // Execute Warp
                val requester = Bukkit.getPlayer(requesterUuid)
                if (requester != null && requester.isOnline) {
                    val targetWorldData = plugin.worldConfigRepository.findByWorldName(sender.world.name)
                    
                    if (targetWorldData != null) {
                        plugin.worldService.teleportToWorld(requester, targetWorldData.uuid)
                        sender.sendMessage(plugin.languageManager.getMessage(sender, "messages.meet.request_accepted", mapOf("player" to requester.name)))
                        requester.sendMessage(plugin.languageManager.getMessage(requester, "messages.meet.request_accepted_by_target", mapOf("player" to sender.name)))
                        
                        // Visitor count? If not owner/member
                         val isMember = targetWorldData.owner == requester.uniqueId || 
                                       targetWorldData.moderators.contains(requester.uniqueId) || 
                                       targetWorldData.members.contains(requester.uniqueId)
                         if (!isMember) {
                             targetWorldData.recentVisitors[0]++
                             plugin.worldConfigRepository.save(targetWorldData)
                         }
                    } else {
                        sender.sendMessage(plugin.languageManager.getMessage(sender, "messages.meet.not_in_valid_world"))
                    }
                } else {
                    sender.sendMessage(plugin.languageManager.getMessage(sender, "messages.meet.requester_offline"))
                }
                pendingRequests.remove(sender.uniqueId)
            } else {
                sender.sendMessage(plugin.languageManager.getMessage(sender, "messages.meet.no_pending_request"))
            }
            return true
        }
        
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
        return emptyList()
    }
}
