package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import java.util.UUID

class MeetListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val view = event.view
        val title = PlainTextComponentSerializer.plainText().serialize(view.title())
        val player = event.whoClicked as? Player ?: return
        val lang = plugin.languageManager
        val currentItem = event.currentItem ?: return
        val type = ItemTag.getType(currentItem)

        if (view.topInventory.holder is me.awabi2048.myworldmanager.gui.MeetGui.MeetGuiHolder) {
            event.isCancelled = true
            if (currentItem.type == Material.AIR || type == ItemTag.TYPE_GUI_DECORATION) return

            if (type == ItemTag.TYPE_GUI_MEET_STATUS_TOGGLE) {
                plugin.soundManager.playClickSound(player, currentItem, "meet")
                // Cycle status: JOIN_ME -> ASK_ME -> BUSY
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                val newStatus = when (stats.meetStatus) {
                    "JOIN_ME" -> "ASK_ME"
                    "ASK_ME" -> "BUSY"
                    else -> "JOIN_ME"
                }
                stats.meetStatus = newStatus
                plugin.playerStatsRepository.save(stats)
                
                // Refresh
                plugin.meetGui.open(player)
                return
            }

            if (type == ItemTag.TYPE_GUI_RETURN) {
                me.awabi2048.myworldmanager.util.GuiHelper.handleReturnClick(plugin, player, currentItem)
                return
            }

            if (type == "gui_meet_target_head") {
                val skullMeta = currentItem.itemMeta as? org.bukkit.inventory.meta.SkullMeta ?: return
                val target = skullMeta.owningPlayer?.player ?: run {
                    player.sendMessage(lang.getMessage(player, "messages.target_offline"))
                    player.closeInventory()
                    return
                }

                val stats = plugin.playerStatsRepository.findByUuid(target.uniqueId)

                // Check Status
                if (stats.meetStatus == "BUSY") {
                     // Should not show up usually, but if updated
                     player.sendMessage(lang.getMessage(player, "messages.meet.busy"))
                     return
                }
                
                if (stats.meetStatus == "ASK_ME") {
                    // Send Request
                    plugin.soundManager.playClickSound(player, currentItem, "meet")
                    player.closeInventory()
                    
                    me.awabi2048.myworldmanager.command.MeetCommand.pendingRequests[target.uniqueId] = player.uniqueId
                    
                    player.sendMessage(lang.getMessage(player, "general.meet_request.sent", mapOf("player" to target.name)))
                    
                    target.sendMessage(lang.getMessage(target, "general.meet_request.received", mapOf("player" to player.name)))
                    val acceptText = net.kyori.adventure.text.Component.text(lang.getMessage(target, "general.meet_request.accept_button"))
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/meet accept ${player.uniqueId}"))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text(lang.getMessage(target, "general.meet_request.hover_accept"))))
                    target.sendMessage(acceptText)
                    return
                }

                // JOIN_ME logic (Standard)
                val world = target.world
                val worldName = world.name

                // ワールド名からワールドデータを取得（変換されたワールドも考慮）
                val worldData = plugin.worldConfigRepository.findByWorldName(worldName) ?: run {
                    player.sendMessage(lang.getMessage(player, "messages.target_not_in_myworld"))
                    plugin.soundManager.playActionSound(player, "meet", "access_denied")
                    return
                }

                val isMember = worldData.owner == player.uniqueId || 
                               worldData.moderators.contains(player.uniqueId) || 
                               worldData.members.contains(player.uniqueId)

                if (worldData.publishLevel == PublishLevel.PUBLIC || isMember) {
                    plugin.soundManager.playClickSound(player, currentItem, "meet")
                    plugin.worldService.teleportToWorld(player, worldData.uuid)
                    player.sendMessage(lang.getMessage(player, "messages.warp_success", mapOf("world" to worldData.name)))
                    plugin.worldService.sendAnnouncementMessage(player, worldData)
                    
                    target.sendMessage(lang.getMessage(target, "messages.visitor_notified", mapOf("player" to player.name, "world" to worldData.name)))

                    if (!isMember) {
                        worldData.recentVisitors[0]++
                        plugin.worldConfigRepository.save(worldData)
                    }
                    player.closeInventory()
                } else {
                    player.sendMessage(lang.getMessage(player, "messages.world_not_public"))
                    plugin.soundManager.playActionSound(player, "meet", "access_denied")
                }
            }
        }
        
        }
}
