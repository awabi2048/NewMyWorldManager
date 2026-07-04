package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.event.MwmVisitSource
import me.awabi2048.myworldmanager.api.event.MwmWorldVisitedEvent
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.PlayerTag
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerTeleportEvent

class AccessControlListener(private val plugin: MyWorldManager) : Listener {
    private val repository = plugin.worldConfigRepository

    @EventHandler
    fun onWorldTeleport(event: PlayerTeleportEvent) {
        val player = event.player
        val toWorld = event.to.world ?: return
        val worldData = repository.findByWorldName(toWorld.name) ?: return
        val lang = plugin.languageManager

        if (plugin.previewSessionManager.isInPreview(player)) return

        if (worldData.isArchived) {
            player.sendMessage(lang.getMessage(player, "messages.archive_access_denied"))
            event.isCancelled = true
            return
        }

        val isMember = isMember(player, worldData)
        if (!MyWorldManagerApi.getWorldAccessPolicy().canEnterWorld(player, worldData, isMember)) {
            player.sendMessage(lang.getMessage(player, "error.portal_dest_locked"))
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        val player = event.player
        if (plugin.previewSessionManager.isInPreview(player)) return
        val worldData = repository.findByWorldName(player.world.name) ?: return
        handleWorldEntry(player, worldData, MwmVisitSource.WORLD_CHANGE)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        if (plugin.previewSessionManager.isInPreview(player)) return
        val worldData = repository.findByWorldName(player.world.name) ?: return
        val lang = plugin.languageManager

        if (worldData.isArchived && !player.hasPermission("myworldmanager.admin")) {
            player.teleport(plugin.worldService.getEvacuationLocation())
            player.sendMessage(lang.getMessage(player, "messages.archive_access_denied"))
            return
        }

        handleWorldEntry(player, worldData, MwmVisitSource.JOIN)
    }

    private fun handleWorldEntry(player: Player, worldData: WorldData, source: MwmVisitSource) {
        recordVisitedWorld(player, worldData)
        if (isMember(player, worldData)) return

        val owner = Bukkit.getPlayer(worldData.owner)
        val shouldExposeVisit = owner == null ||
            !owner.isOnline ||
            plugin.playerVisibilityService.isVisibleTo(owner, player)

        val firstVisitToday = shouldExposeVisit && PlayerTag.shouldCountVisit(player, worldData.uuid)
        if (firstVisitToday) {
            worldData.recentVisitors[0]++
            plugin.worldConfigRepository.save(worldData)
        }

        plugin.server.pluginManager.callEvent(
            MwmWorldVisitedEvent(
                worldUuid = worldData.uuid,
                worldName = worldData.name,
                visitorUuid = player.uniqueId,
                visitorName = player.name,
                ownerUuid = worldData.owner,
                firstVisitToday = firstVisitToday,
                source = source
            )
        )

        plugin.worldService.sendAnnouncementMessage(player, worldData)
        if (plugin.tourManager.hasValidTour(worldData)) {
            plugin.tourManager.sendArrivalMessage(player, worldData)
        }

        if (worldData.notificationEnabled &&
            owner != null &&
            owner.isOnline &&
            plugin.playerVisibilityService.isVisibleTo(owner, player)
        ) {
            val lang = plugin.languageManager
            owner.sendMessage(
                lang.getMessage(
                    owner,
                    "messages.visitor_notified",
                    mapOf("player" to player.name, "world" to worldData.name)
                )
            )
        }
    }

    private fun isMember(player: Player, worldData: WorldData): Boolean =
        worldData.owner == player.uniqueId ||
            worldData.members.contains(player.uniqueId) ||
            worldData.moderators.contains(player.uniqueId)

    private fun recordVisitedWorld(player: Player, worldData: WorldData) {
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        if (stats.visitedWorlds.containsKey(worldData.uuid)) return
        stats.visitedWorlds[worldData.uuid] = java.time.LocalDate.now().toString()
        plugin.playerStatsRepository.save(stats)
    }
}
