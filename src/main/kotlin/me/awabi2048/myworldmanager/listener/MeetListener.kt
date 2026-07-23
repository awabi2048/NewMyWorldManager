package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.ui.ManagedMenuPresenter

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.extension.MeetTargetAction
import me.awabi2048.myworldmanager.util.ItemTag

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import me.awabi2048.myworldmanager.util.cancelWithDebug
import java.util.UUID

class MeetListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler(ignoreCancelled = false)
    fun onInventoryClick(event: InventoryClickEvent) {
        val view = event.view
        val title = PlainTextComponentSerializer.plainText().serialize(view.title())
        val player = event.whoClicked as? Player ?: return
        val lang = plugin.languageManager
        val currentItem = event.currentItem ?: return
        val type = ItemTag.getType(currentItem)

        if (view.topInventory.holder is me.awabi2048.myworldmanager.gui.MeetGui.MeetGuiHolder) {
            event.cancelWithDebug("MeetListener.onInventoryClick: meet GUI click")
            if (event.clickedInventory != view.topInventory) return
            if (currentItem.type == Material.AIR || type == ItemTag.TYPE_GUI_DECORATION) return

            if (type == ItemTag.TYPE_GUI_NAV_NEXT || type == ItemTag.TYPE_GUI_NAV_PREV) {
                val targetPage = ItemTag.getTargetPage(currentItem) ?: 0
                plugin.meetSessionManager.getSession(player.uniqueId).currentPage = targetPage
                plugin.soundManager.playClickSound(player, currentItem, "meet")
                plugin.menuEntryRouter.openMeet(player)
                return
            }

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
                plugin.meetSessionManager.getSession(player.uniqueId).currentPage = 0
                
                // Refresh
                plugin.menuEntryRouter.openMeet(player)
                return
            }

            if (type == ItemTag.TYPE_GUI_RETURN) {
                me.awabi2048.myworldmanager.util.GuiHelper.handleReturnClick(plugin, player, currentItem)
                return
            }

            if (type == "gui_meet_target_head") {
                val skullMeta = currentItem.itemMeta as? org.bukkit.inventory.meta.SkullMeta ?: return
                val target = skullMeta.owningPlayer?.player ?: run {
                    player.sendMessage(lang.getMessage(player, "error.target_offline"))
                    ManagedMenuPresenter.close(player)
                    return
                }

                if (!plugin.playerVisibilityService.isVisibleTo(player, target)) {
                    player.sendMessage(lang.getMessage(player, "error.target_offline"))
                    ManagedMenuPresenter.close(player)
                    return
                }

                val stats = plugin.playerStatsRepository.findByUuid(target.uniqueId)

                // Check Status
                if (stats.meetStatus == "BUSY") {
                     // Should not show up usually, but if updated
                     player.sendMessage(lang.getMessage(player, "messages.meet.busy"))
                     return
                }

                if (target.world.uid == player.world.uid) {
                    return
                }
                
                if (stats.meetStatus == "ASK_ME") {
                    // Send Request
                    plugin.soundManager.playClickSound(player, currentItem, "meet")
                    sendMeetRequest(player, target)
                    return
                }

                // JOIN_ME logic (Standard)
                val world = target.world
                val worldName = world.name

                // ワールド名からワールドデータを取得（変換されたワールドも考慮）
                val worldData = plugin.worldConfigRepository.findByWorldName(worldName) ?: run {
                    player.sendMessage(lang.getMessage(player, "error.target_not_in_myworld"))
                    plugin.soundManager.playActionSound(player, "meet", "access_denied")
                    return
                }

                val isMember = worldData.owner == player.uniqueId || 
                               worldData.moderators.contains(player.uniqueId) || 
                               worldData.members.contains(player.uniqueId)

                when (MyWorldManagerApi.getWorldAccessPolicy().getMeetTargetAction(player, target, worldData, isMember)) {
                    MeetTargetAction.DIRECT -> {
                        plugin.soundManager.playClickSound(player, currentItem, "meet")
                        ManagedMenuPresenter.close(player)
                        plugin.worldService.teleportToWorld(player, worldData.uuid) {
                            player.sendMessage(lang.getMessage(player, "messages.warp_success", mapOf("world" to worldData.name)))

                            if (worldData.notificationEnabled && plugin.playerVisibilityService.isVisibleTo(target, player)) {
                                target.sendMessage(lang.getMessage(target, "messages.visitor_notified", mapOf("player" to player.name, "world" to worldData.name)))
                            }
                        }
                    }
                    MeetTargetAction.REQUEST -> {
                        plugin.soundManager.playClickSound(player, currentItem, "meet")
                        sendMeetRequest(player, target)
                    }
                    MeetTargetAction.DENY -> {
                        player.sendMessage(lang.getMessage(player, "error.world_not_public"))
                        plugin.soundManager.playActionSound(player, "meet", "access_denied")
                    }
                }
            }
        }
         
        }

    private fun sendMeetRequest(player: Player, target: Player) {
        val lang = plugin.languageManager
        ManagedMenuPresenter.close(player)
        player.sendMessage(lang.getMessage(player, "general.meet_request.sent", mapOf("player" to target.name)))
        val result = plugin.pendingDecisionManager.enqueueMeetRequest(target, player.uniqueId, target.world.uid, 60)
        plugin.pendingNotificationService.send(
            target,
            me.awabi2048.myworldmanager.service.PendingDecisionManager.PendingType.MEET_REQUEST,
            result.actionCode,
            player.uniqueId,
            null
        )
    }
}
