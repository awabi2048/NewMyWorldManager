package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.util.BiomeResolver
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.GameRule
import org.bukkit.GameMode
import org.bukkit.Difficulty
import org.bukkit.NamespacedKey
import org.bukkit.entity.EnderCrystal
import org.bukkit.entity.EnderDragon
import org.bukkit.event.entity.EntityPlaceEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerToggleFlightEvent
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
        applyFlight(event.player, event.player.world.name)
        applyWeather(event.player.world)
        applyTime(event.player.world)
        applyWorldSettings(event.player.world)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        removeAllModifiers(event.player)
        applyGravity(event.player, event.player.world.name)
        applyScale(event.player, event.player.world.name)
        applyFlight(event.player, event.player.world.name)
        applyWeather(event.player.world)
        applyTime(event.player.world)
        applyWorldSettings(event.player.world)
    }

    @EventHandler
    fun onEntitySpawn(event: org.bukkit.event.entity.EntitySpawnEvent) {
        if (isBlockedEndBossEntity(event.entity)) {
            event.isCancelled = true
            event.entity.remove()
            return
        }
        if (event.entity is org.bukkit.entity.LivingEntity) {
            applyGravity(event.entity, event.entity.world.name)
            applyScale(event.entity, event.entity.world.name)
        }
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

        // 設定OFFのワールドでは、他プラグイン等でallowFlightが戻っても非クリエイティブの飛行を確実に止める。
        event.isCancelled = true
        if (player.isFlying) {
            player.isFlying = false
        }
        player.allowFlight = false
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

    private fun applyFlight(player: org.bukkit.entity.Player, worldName: String) {
        val worldData = plugin.worldConfigRepository.findByWorldName(worldName)
        val shouldAllow = worldData?.allowFlight == true
        val gameModeAllowsFlight = player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR

        // ワールド環境設定のフライ許可は、通常ゲームモードだけを対象にして管理者/観戦者の飛行状態を壊さない。
        if (shouldAllow || gameModeAllowsFlight) {
            player.allowFlight = true
            return
        }

        if (player.isFlying) {
            player.isFlying = false
        }
        player.allowFlight = false
    }

    private fun applyWeather(world: org.bukkit.World) {
        val worldName = world.name
        val worldData = plugin.worldConfigRepository.findByWorldName(worldName) ?: return
        val weather = worldData.fixedWeather

        if (weather == null) {
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, true)
            world.weatherDuration = 0
            world.thunderDuration = 0
            return
        }
        // 天候を固定するため、バニラの天候サイクルを停止する。
        // DO_WEATHER_CYCLE を false にしないと再起動/アンロード後に天候がリセットされる。
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false)
        when (weather) {
            "CLEAR" -> {
                world.setStorm(false)
                world.setThundering(false)
                world.weatherDuration = Int.MAX_VALUE
            }
            "RAIN" -> {
                world.setStorm(true)
                world.setThundering(false)
                world.weatherDuration = Int.MAX_VALUE
                world.thunderDuration = 0
            }
            "THUNDER" -> {
                world.setStorm(true)
                world.setThundering(true)
                world.weatherDuration = Int.MAX_VALUE
                world.thunderDuration = Int.MAX_VALUE
            }
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

    private fun isBlockedEndBossEntity(entity: org.bukkit.entity.Entity): Boolean {
        if (entity !is EnderDragon && entity !is EnderCrystal) return false
        val world = entity.world
        if (world.environment != org.bukkit.World.Environment.THE_END) return false
        return plugin.worldConfigRepository.findByWorldName(world.name) != null
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

        // チャンクロード時に既存エンティティ（サーバー起動前から存在するモブ等）の重力・スケールを再適用する。
        // onEntitySpawn は新規スポーンのみを拾うため、永続化されたエンティティにはここで反映する。
        val gravity = worldData.gravityValue
        val scale = worldData.fixedScale
        if (gravity != null || scale != null) {
            chunk.entities.forEach { entity ->
                if (entity is org.bukkit.entity.LivingEntity) {
                    if (gravity != null) applyGravity(entity, worldName)
                    if (scale != null) applyScale(entity, worldName)
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_GRAVITY = 0.08
        private const val DEFAULT_SCALE = 1.0
    }
}
