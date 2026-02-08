package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.GameRule
import org.bukkit.Difficulty
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.weather.WeatherChangeEvent
import org.bukkit.event.world.ChunkLoadEvent
import me.awabi2048.myworldmanager.model.WorldData

class EnvironmentLogicListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        applyGravity(event.player)
        applyWorldSettings(event.player.world)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        applyGravity(event.player)
        applyWorldSettings(event.player.world)
    }

    @EventHandler
    fun onEntitySpawn(event: org.bukkit.event.entity.EntitySpawnEvent) {
        applyGravity(event.entity)
    }

    private fun applyGravity(entity: org.bukkit.entity.Entity) {
        if (entity is org.bukkit.entity.LivingEntity) {
            val worldName = entity.world.name
            val worldData = plugin.worldConfigRepository.findByWorldName(worldName)
            
            if (worldData == null) {
                // Reset to default
                entity.getAttribute(Attribute.GRAVITY)?.baseValue = 0.08
                return
            }
            
            entity.getAttribute(Attribute.GRAVITY)?.baseValue = 0.08 * worldData.gravityMultiplier
        }
    }

    private fun applyWorldSettings(world: org.bukkit.World) {
        val worldName = world.name
        val worldData = plugin.worldConfigRepository.findByWorldName(worldName) ?: return

        // Admin convert ワールドのチェック (WorldUnloadService と同様の基準)
        if (worldData.sourceWorld == "CONVERT" && !worldName.startsWith("my_world.")) {
            return
        }

        // 難易度を PEACEFUL に、モブスポーンを false に設定
        world.difficulty = Difficulty.PEACEFUL
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onWeatherChange(event: WeatherChangeEvent) {
        val worldName = event.world.name
        val worldData = plugin.worldConfigRepository.findByWorldName(worldName) ?: return

        if (worldData.fixedWeather != null) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        val worldName = event.world.name
        val worldData = plugin.worldConfigRepository.findByWorldName(worldName) ?: return
        
        val biomeStr = worldData.fixedBiome
        var baseBiome: org.bukkit.block.Biome? = null
        if (biomeStr != null) {
            baseBiome = try { org.bukkit.block.Biome.valueOf(biomeStr) } catch (e: Exception) { null }
        }
        
        val chunk = event.chunk
        val world = event.world
        
        // 1. Apply Global Biome (if set)
        if (baseBiome != null) {
            val center = worldData.borderCenterPos ?: world.spawnLocation
            val expansion = worldData.borderExpansionLevel
            val initialSize = plugin.config.getDouble("expansion.initial_size", 100.0)
            val size = if (expansion == WorldData.EXPANSION_LEVEL_SPECIAL) 60000000.0 else initialSize * Math.pow(2.0, expansion.toDouble())
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
                                // Optimization: Only set if different? Bukkit handles this? 
                                // To be safe and fast, just set.
                                world.setBiome(worldX, y, worldZ, baseBiome)
                            }
                        }
                    }
                }
            }
        }
        
        // 2. Apply Partial Biomes
        if (worldData.partialBiomes.isNotEmpty()) {
            val chunkMinX = chunk.x * 16
            val chunkMaxX = chunkMinX + 15
            val chunkMinZ = chunk.z * 16
            val chunkMaxZ = chunkMinZ + 15
            
            worldData.partialBiomes.forEach { partial ->
                val pDataBiome = try { org.bukkit.block.Biome.valueOf(partial.biome) } catch (e: Exception) { return@forEach }
                
                // Simple bounding box check to optimize
                if (partial.x + partial.radius < chunkMinX || partial.x - partial.radius > chunkMaxX ||
                    partial.z + partial.radius < chunkMinZ || partial.z - partial.radius > chunkMaxZ) {
                    return@forEach
                }
                
                for (x in 0..15) {
                    val worldX = chunkMinX + x
                    for (z in 0..15) {
                        val worldZ = chunkMinZ + z
                        
                        // Check distance
                        if ((worldX - partial.x) * (worldX - partial.x) + (worldZ - partial.z) * (worldZ - partial.z) <= partial.radius * partial.radius) {
                            for (y in world.minHeight until world.maxHeight step 16) { // Optimization
                                 world.setBiome(worldX, y, worldZ, pDataBiome)
                            }
                        }
                    }
                }
            }
        }
    }
}
