package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.BiomeResolver
import org.bukkit.GameMode
import org.bukkit.entity.EnderCrystal
import org.bukkit.entity.EnderDragon
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPlaceEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerToggleFlightEvent
import org.bukkit.event.world.ChunkLoadEvent

class EnvironmentLogicListener(private val plugin: MyWorldManager) : Listener {

    private val expansionInitialSizeConfigKey = listOf("expansion", "initial_size").joinToString(".")

    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        val player = event.player
        val previousAllow = plugin.worldEnvironmentService.computeFlightAllowed(player, event.from.name)
        plugin.worldEnvironmentService.resetAttributes(player)
        if (!plugin.worldEnvironmentService.applyAll(player.world)) {
            plugin.worldEnvironmentService.applyFlight(player, player.world.name)
        }
        val currentAllow = plugin.worldEnvironmentService.computeFlightAllowed(player, player.world.name)
        notifyFlightChange(player, previousAllow, currentAllow)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        plugin.worldEnvironmentService.resetAttributes(event.player)
        if (!plugin.worldEnvironmentService.applyAll(event.player.world)) {
            plugin.worldEnvironmentService.applyFlight(event.player, event.player.world.name)
        }
    }

    @EventHandler
    fun onEntitySpawn(event: org.bukkit.event.entity.EntitySpawnEvent) {
        if (isBlockedEndBossEntity(event.entity)) {
            event.isCancelled = true
            event.entity.remove()
            return
        }
        plugin.worldEnvironmentService.applyAttributes(event.entity, event.entity.world.name)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityPlace(event: EntityPlaceEvent) {
        if (isBlockedEndBossEntity(event.entity)) {
            event.isCancelled = true
            event.entity.remove()
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerToggleFlight(event: PlayerToggleFlightEvent) {
        val player = event.player
        val worldData = plugin.worldConfigRepository.findByWorldName(player.world.name) ?: return
        if (worldData.allowFlight) return
        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return

        // allowFlight が他経路で戻された場合も、MyWorld の飛行禁止設定を最後に優先する。
        event.isCancelled = true
        if (player.isFlying) player.isFlying = false
        player.allowFlight = false
    }

    private fun notifyFlightChange(
        player: org.bukkit.entity.Player,
        previousAllow: Boolean,
        currentAllow: Boolean
    ) {
        if (previousAllow == currentAllow) return
        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return
        val key = if (currentAllow) {
            "messages.env_flight_enabled"
        } else {
            "messages.env_flight_disabled"
        }
        player.sendMessage(plugin.languageManager.getMessage(player, key))
    }

    private fun isBlockedEndBossEntity(entity: org.bukkit.entity.Entity): Boolean {
        if (entity !is EnderDragon && entity !is EnderCrystal) return false
        val world = entity.world
        if (world.environment != org.bukkit.World.Environment.THE_END) return false
        return plugin.worldConfigRepository.findByWorldName(world.name) != null
    }

    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        val worldName = event.world.name
        val worldData = plugin.worldConfigRepository.findByWorldName(worldName) ?: return

        applyFixedBiomes(event, worldData)

        if (worldData.gravityValue != null || worldData.fixedScale != null) {
            event.chunk.entities.forEach { entity ->
                plugin.worldEnvironmentService.applyAttributes(entity, worldName)
            }
        }
    }

    private fun applyFixedBiomes(event: ChunkLoadEvent, worldData: WorldData) {
        val baseBiome = worldData.fixedBiome?.let(BiomeResolver::match)
        val chunk = event.chunk
        val world = event.world

        if (baseBiome != null) {
            val center = worldData.borderCenterPos ?: world.spawnLocation
            val expansion = worldData.borderExpansionLevel
            val initialSize = plugin.config.getDouble(expansionInitialSizeConfigKey, 100.0)
            val size = if (expansion == WorldData.EXPANSION_LEVEL_SPECIAL) {
                60000000.0
            } else {
                initialSize * Math.pow(2.0, expansion.toDouble())
            }
            val radius = size / 2.0
            val applyRadius = radius + 160.0

            val minX = (center.x - applyRadius).toInt()
            val maxX = (center.x + applyRadius).toInt()
            val minZ = (center.z - applyRadius).toInt()
            val maxZ = (center.z + applyRadius).toInt()
            val chunkX = chunk.x * 16
            val chunkZ = chunk.z * 16

            if (!(chunkX + 15 < minX || chunkX > maxX || chunkZ + 15 < minZ || chunkZ > maxZ)) {
                for (x in 0..15) {
                    val worldX = chunkX + x
                    for (z in 0..15) {
                        val worldZ = chunkZ + z
                        if (worldX in minX..maxX && worldZ in minZ..maxZ) {
                            for (y in world.minHeight until world.maxHeight step 16) {
                                world.setBiome(worldX, y, worldZ, baseBiome)
                            }
                        }
                    }
                }
            }
        }

        if (worldData.partialBiomes.isNotEmpty()) {
            val chunkMinX = chunk.x * 16
            val chunkMaxX = chunkMinX + 15
            val chunkMinZ = chunk.z * 16
            val chunkMaxZ = chunkMinZ + 15

            worldData.partialBiomes.forEach { partial ->
                val partialBiome = BiomeResolver.match(partial.biome) ?: return@forEach
                if (partial.x + partial.radius < chunkMinX || partial.x - partial.radius > chunkMaxX ||
                    partial.z + partial.radius < chunkMinZ || partial.z - partial.radius > chunkMaxZ
                ) {
                    return@forEach
                }

                for (x in 0..15) {
                    val worldX = chunkMinX + x
                    for (z in 0..15) {
                        val worldZ = chunkMinZ + z
                        if ((worldX - partial.x) * (worldX - partial.x) + (worldZ - partial.z) * (worldZ - partial.z) <= partial.radius * partial.radius) {
                            for (y in world.minHeight until world.maxHeight step 16) {
                                world.setBiome(worldX, y, worldZ, partialBiome)
                            }
                        }
                    }
                }
            }
        }
    }
}
