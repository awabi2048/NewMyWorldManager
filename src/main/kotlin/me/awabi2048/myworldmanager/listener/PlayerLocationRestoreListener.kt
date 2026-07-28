package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.repository.PlayerLocationSnapshot
import me.awabi2048.myworldmanager.repository.PlayerLocationSnapshotRepository
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class PlayerLocationRestoreListener(
    private val plugin: MyWorldManager,
    private val snapshots: PlayerLocationSnapshotRepository
) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        val originWorld = player.world
        val worldData = plugin.worldConfigRepository.findByWorldName(originWorld.name)
        saveCurrentLocation(player)
        if (worldData == null || worldData.isArchived) return

        val evacuationLocation = plugin.worldService.getEvacuationLocation()
        if (evacuationLocation.world == originWorld) return

        MyWorldManagerApi.beginLogoutRelocation(player, plugin, originWorld.name)
        if (!player.teleport(evacuationLocation)) {
            plugin.logger.warning(
                "Could not relocate ${player.uniqueId} before logout; " +
                    "Paper may restore the saved coordinates in the overworld if ${worldData.uuid} is unloaded"
            )
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val snapshot = runCatching { snapshots.find(player.uniqueId) }
            .onFailure {
                plugin.logger.log(
                    java.util.logging.Level.SEVERE,
                    "Could not read saved location for ${player.uniqueId}",
                    it
                )
            }
            .getOrNull() ?: return
        val worldData = plugin.worldConfigRepository.findByUuid(snapshot.worldUuid)
        if (worldData == null || worldData.isArchived) {
            snapshots.delete(player.uniqueId)
            return
        }
        if (!canEnter(player, worldData)) {
            snapshots.delete(player.uniqueId)
            return
        }
        if (!plugin.worldService.loadWorld(snapshot.worldUuid)) {
            plugin.logger.warning(
                "Could not load ${snapshot.worldUuid} while restoring ${player.uniqueId}; " +
                    "the saved location will be retained for the next login"
            )
            return
        }

        plugin.server.scheduler.runTask(plugin, Runnable {
            if (!player.isOnline) return@Runnable
            val world = Bukkit.getWorld(plugin.worldService.getWorldFolderName(worldData))
            if (world == null) {
                plugin.logger.warning(
                    "Loaded world ${snapshot.worldUuid} was unavailable while restoring ${player.uniqueId}"
                )
                return@Runnable
            }
            val restored = player.teleport(
                Location(world, snapshot.x, snapshot.y, snapshot.z, snapshot.yaw, snapshot.pitch)
            )
            if (restored) {
                snapshots.delete(player.uniqueId)
            } else {
                plugin.logger.warning(
                    "Teleport was rejected while restoring ${player.uniqueId} to ${snapshot.worldUuid}; " +
                        "the saved location will be retained"
                )
            }
        })
    }

    fun saveCurrentLocation(player: Player) {
        val worldData = plugin.worldConfigRepository.findByWorldName(player.world.name)
        if (worldData == null || worldData.isArchived) {
            snapshots.delete(player.uniqueId)
            return
        }
        val location = player.location
        runCatching {
            snapshots.save(
                player.uniqueId,
                PlayerLocationSnapshot(
                    worldUuid = worldData.uuid,
                    x = location.x,
                    y = location.y,
                    z = location.z,
                    yaw = location.yaw,
                    pitch = location.pitch
                )
            )
        }.onFailure {
            plugin.logger.log(
                java.util.logging.Level.SEVERE,
                "Could not save location for ${player.uniqueId}",
                it
            )
        }
    }

    private fun canEnter(player: Player, worldData: WorldData): Boolean {
        val isMember = worldData.owner == player.uniqueId ||
            worldData.members.contains(player.uniqueId) ||
            worldData.moderators.contains(player.uniqueId)
        return MyWorldManagerApi.getWorldAccessPolicy().canEnterWorld(player, worldData, isMember)
    }
}
