package me.awabi2048.myworldmanager.command

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.VisitGui
import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.repository.*
import me.awabi2048.myworldmanager.util.PermissionManager
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class VisitCommand(private val plugin: MyWorldManager) : CommandExecutor, TabCompleter {

    private val visitGui = VisitGui(plugin)

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!PermissionManager.checkPermission(sender, PermissionManager.CITIZEN)) {
            PermissionManager.sendNoPermissionMessage(sender)
            return true
        }
        if (sender !is Player) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行可能です。")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("§c使用法: /visit <プレイヤー名>")
            return true
        }

        val targetName = args[0]
        var target: org.bukkit.OfflinePlayer? = Bukkit.getPlayerExact(targetName)

        if (target == null) {
            val offlinePlayer = Bukkit.getOfflinePlayer(targetName)
            if (offlinePlayer.hasPlayedBefore()) {
                target = offlinePlayer
            }
        }

        if (target == null) {
            sender.sendMessage("§cプレイヤー「$targetName」が見つかりませんでした。")
            return true
        }

        if (target == sender) {
            sender.sendMessage("§c自分自身のワールド一覧を表示することはできません。/myworld を使用してください。")
            return true
        }

        // 訪問可能なワールドの有無を確認
        val visitableWorlds = plugin.worldConfigRepository.findAll().filter { world ->
            if (world.owner != target.uniqueId || world.isArchived) return@filter false
            
            val isMember = world.owner == sender.uniqueId || 
                          world.moderators.contains(sender.uniqueId) || 
                          world.members.contains(sender.uniqueId)
            
            world.publishLevel == PublishLevel.PUBLIC || isMember
        }

        if (visitableWorlds.isEmpty()) {
            sender.sendMessage("§cプレイヤー「$targetName」の訪問可能なワールドが見つかりませんでした。")
            return true
        }

        visitGui.open(sender, target)
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (!PermissionManager.checkPermission(sender, PermissionManager.CITIZEN)) return emptyList()
        if (sender !is Player) return emptyList()
        
        if (args.size == 1) {
            val search = args[0].lowercase()
            return Bukkit.getOnlinePlayers()
                .filter { target ->
                    target != sender &&
                    // 訪問可能なワールドを1つ以上持っているプレイヤーのみ抽出
                    plugin.worldConfigRepository.findAll().any { world ->
                        if (world.owner != target.uniqueId || world.isArchived) return@any false
                        
                        val isMember = world.owner == sender.uniqueId || 
                                      world.moderators.contains(sender.uniqueId) || 
                                      world.members.contains(sender.uniqueId)
                        
                        world.publishLevel == PublishLevel.PUBLIC || isMember
                    }
                }
                .map { it.name }
                .filter { it.lowercase().startsWith(search) }
        }
        return emptyList()
    }
}
