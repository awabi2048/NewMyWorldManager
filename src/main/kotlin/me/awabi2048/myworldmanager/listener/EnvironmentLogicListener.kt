package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.util.BiomeResolver
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.GameRule
import org.bukkit.Difficulty
import org.bukkit.NamespacedKey
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.weather.WeatherChangeEvent
import org.bukkit.event.world.ChunkLoadEvent
import me.awabi2048.myworldmanager.model.WorldData

class EnvironmentLogicListener(private val plugin: MyWorldManager) : Listener {

    private val gravityKey = NamespacedKey("myworldmanager", "env_gravity")
    private val scaleKey = NamespacedKey("myworldmanager", "env_scale")

    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        removeAllModifiers(event.player)
        applyGravity(event.player, event.player.world.name)
        applyScale(event.player, event.player.world.name)
        applyTime(event.player.world)
        applyWorldSettings(event.player.world)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        removeAllModifiers(event.player)
        applyGravity(event.player, event.player.world.name)
        applyScale(event.player, event.player.world.name)
        applyTime(event.player.world)
        applyWorldSettings(event.player.world)
    }

    @EventHandler
    fun onEntitySpawn(event: org.bukkit.event.entity.EntitySpawnEvent) {
        if (event.entity is org.bukkit.entity.LivingEntity) {
            applyGravity(event.entity, event.entity.world.name)
            applyScale(event.entity, event.entity.world.name)
        }
    }

    private fun removeAllModifiers(entity: org.bukkit.entity.Entity) {
        if (entity !is org.bukkit.entity.LivingEntity) return
        entity.getAttribute(Attribute.GRAVITY)?.let { attr ->
            val existing = attr.modifiers.filter { it.key == gravityKey }
            existing.forEach { attr.removeModifier(it.key) }
            attr.baseValue = DEFAULT_GRAVITY
        }
        entity.getAttribute(Attribute.SCALE)?.let { attr ->
            val existing = attr.modifiers.filter { it.key == scaleKey }
            existing.forEach { attr.removeModifier(it.key) }
            attr.baseValue = DEFAULT_SCALE
        }
    }

    private fun applyGravity(entity: org.bukkit.entity.Entity, worldName: String) {
        if (entity !is org.bukkit.entity.LivingEntity) return
        val worldData = plugin.worldConfigRepository.findByWorldName(worldName) ?: return
        val attr = entity.getAttribute(Attribute.GRAVITY) ?: return
        val gravity = worldData.gravityValue

        val existing = attr.modifiers.filter { it.key == gravityKey }
        existing.forEach { attr.removeModifier(it.key) }
        attr.baseValue = DEFAULT_GRAVITY

        if (gravity != null && gravity != DEFAULT_GRAVITY) {
            val modifier = AttributeModifier(
                gravityKey,
                gravity - DEFAULT_GRAVITY,
                AttributeModifier.Operation.ADD_NUMBER
            )
            attr.addModifier(modifier)
        }
    }

    private fun applyScale(entity: org.bukkit.entity.Entity, worldName: String) {
        if (entity !is org.bukkit.entity.LivingEntity) return
        val worldData = plugin.worldConfigRepository.findByWorldName(worldName) ?: return
        val attr = entity.getAttribute(Attribute.SCALE) ?: return
        val scale = worldData.fixedScale

        val existing = attr.modifiers.filter { it.key == scaleKey }
        existing.forEach { attr.removeModifier(it.key) }
        attr.baseValue = DEFAULT_SCALE

        if (scale != null && scale != DEFAULT_SCALE) {
            val modifier = AttributeModifier(
                scaleKey,
                scale - DEFAULT_SCALE,
                AttributeModifier.Operation.ADD_NUMBER
            )
            attr.addModifier(modifier)
        }
    }

    private fun applyTime(world: org.bukkit.World) {
        val worldName = world.name
        val worldData = plugin.worldConfigRepository.findByWorldName(worldName) ?: return
        val fixedTime = worldData.fixedTime

        if (fixedTime != null) {
            world.time = fixedTime
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
        } else {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true)
        }
    }

    private fun applyWorldSettings(world: org.bukkit.World) {
        val worldName = world.name
        val worldData = plugin.worldConfigRepository.findByWorldName(worldName) ?: return

        if (worldData.sourceWorld == "CONVERT" && !worldName.startsWith("my_world.")) {
            return
        }

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
            baseBiome = BiomeResolver.match(biomeStr)
        }

        val chunk = event.chunk
        val world = event.world

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
                val pDataBiome = BiomeResolver.match(partial.biome) ?: return@forEach

                if (partial.x + partial.radius < chunkMinX || partial.x - partial.radius > chunkMaxX ||
                    partial.z + partial.radius < chunkMinZ || partial.z - partial.radius > chunkMaxZ) {
                    return@forEach
                }

                for (x in 0..15) {
                    val worldX = chunkMinX + x
                    for (z in 0..15) {
                        val worldZ = chunkMinZ + z

                        if ((worldX - partial.x) * (worldX - partial.x) + (worldZ - partial.z) * (worldZ - partial.z) <= partial.radius * partial.radius) {
                            for (y in world.minHeight until world.maxHeight step 16) {
                                 world.setBiome(worldX, y, worldZ, pDataBiome)
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_GRAVITY = 0.08
        private const val DEFAULT_SCALE = 1.0
    }
}
