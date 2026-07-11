package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.service.ApiWorldEnvironmentService
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.Bukkit
import org.bukkit.Difficulty
import org.bukkit.GameMode
import org.bukkit.GameRules
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import java.util.UUID

class WorldEnvironmentService(private val plugin: MyWorldManager) : ApiWorldEnvironmentService {

    private val gravityKey = NamespacedKey("myworldmanager", "env_gravity")
    private val scaleKey = NamespacedKey("myworldmanager", "env_scale")

    override fun applyAll(worldUuid: UUID): Boolean {
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return false
        val world = resolveWorld(worldData) ?: return false
        return applyAll(world, worldData)
    }

    fun applyAll(world: World): Boolean {
        val worldData = plugin.worldConfigRepository.findByWorldName(world.name) ?: return false
        return applyAll(world, worldData)
    }

    fun applyAll(world: World, worldData: WorldData): Boolean {
        applyWeather(world, worldData)
        applyTime(world, worldData)
        applyAttributes(world, worldData)
        applyFlight(world, worldData)
        applyWorldSettings(world, worldData)
        return true
    }

    override fun applyWeather(worldUuid: UUID): Boolean {
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return false
        val world = resolveWorld(worldData) ?: return false
        applyWeather(world, worldData)
        return true
    }

    fun applyWeather(world: World): Boolean {
        val worldData = plugin.worldConfigRepository.findByWorldName(world.name) ?: return false
        applyWeather(world, worldData)
        return true
    }

    override fun applyTime(worldUuid: UUID): Boolean {
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return false
        val world = resolveWorld(worldData) ?: return false
        applyTime(world, worldData)
        return true
    }

    fun applyTime(world: World): Boolean {
        val worldData = plugin.worldConfigRepository.findByWorldName(world.name) ?: return false
        applyTime(world, worldData)
        return true
    }

    override fun applyAttributes(worldUuid: UUID): Boolean {
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return false
        val world = resolveWorld(worldData) ?: return false
        applyAttributes(world, worldData)
        return true
    }

    override fun applyFlight(worldUuid: UUID): Boolean {
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return false
        val world = resolveWorld(worldData) ?: return false
        applyFlight(world, worldData)
        return true
    }

    fun resetAttributes(entity: Entity) {
        if (entity !is LivingEntity) return
        entity.getAttribute(Attribute.GRAVITY)?.let { attr ->
            attr.modifiers.filter { it.key == gravityKey }.forEach { attr.removeModifier(it.key) }
            attr.baseValue = DEFAULT_GRAVITY
        }
        entity.getAttribute(Attribute.SCALE)?.let { attr ->
            attr.modifiers.filter { it.key == scaleKey }.forEach { attr.removeModifier(it.key) }
            attr.baseValue = DEFAULT_SCALE
        }
    }

    fun applyAttributes(entity: Entity, worldName: String) {
        if (entity !is LivingEntity) return
        val worldData = plugin.worldConfigRepository.findByWorldName(worldName) ?: return
        applyGravity(entity, worldData.gravityValue)
        applyScale(entity, worldData.fixedScale)
    }

    fun applyFlight(player: Player, worldName: String) {
        val worldData = plugin.worldConfigRepository.findByWorldName(worldName)
        applyFlight(player, worldData?.allowFlight ?: true)
    }

    fun computeFlightAllowed(player: Player, worldName: String): Boolean {
        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return true
        return plugin.worldConfigRepository.findByWorldName(worldName)?.allowFlight ?: true
    }

    private fun applyWeather(world: World, worldData: WorldData) {
        val weather = worldData.fixedWeather
        if (weather == null) {
            world.setGameRule(GameRules.ADVANCE_WEATHER, true)
            world.weatherDuration = 0
            world.thunderDuration = 0
            return
        }

        // 天候固定はイベントキャンセルではなく、バニラの天候進行停止と明示適用で成立させる。
        world.setGameRule(GameRules.ADVANCE_WEATHER, false)
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

    private fun applyTime(world: World, worldData: WorldData) {
        val fixedTime = worldData.fixedTime
        if (fixedTime != null) {
            world.time = fixedTime
            world.setGameRule(GameRules.ADVANCE_TIME, false)
        } else {
            world.setGameRule(GameRules.ADVANCE_TIME, true)
        }
    }

    private fun applyAttributes(world: World, worldData: WorldData) {
        world.entities.forEach { entity ->
            if (entity is LivingEntity) {
                applyGravity(entity, worldData.gravityValue)
                applyScale(entity, worldData.fixedScale)
            }
        }
    }

    private fun applyFlight(world: World, worldData: WorldData) {
        world.players.forEach { player -> applyFlight(player, worldData.allowFlight) }
    }

    private fun applyWorldSettings(world: World, worldData: WorldData) {
        if (worldData.sourceWorld == "CONVERT" && !world.name.startsWith("my_world.")) return
        world.difficulty = Difficulty.PEACEFUL
        world.setGameRule(GameRules.SPAWN_MOBS, false)
    }

    private fun applyGravity(entity: LivingEntity, gravity: Double?) {
        val attr = entity.getAttribute(Attribute.GRAVITY) ?: return
        attr.modifiers.filter { it.key == gravityKey }.forEach { attr.removeModifier(it.key) }
        attr.baseValue = DEFAULT_GRAVITY
        if (gravity != null && gravity != DEFAULT_GRAVITY) {
            attr.addModifier(AttributeModifier(gravityKey, gravity - DEFAULT_GRAVITY, AttributeModifier.Operation.ADD_NUMBER))
        }
    }

    private fun applyScale(entity: LivingEntity, scale: Double?) {
        val attr = entity.getAttribute(Attribute.SCALE) ?: return
        attr.modifiers.filter { it.key == scaleKey }.forEach { attr.removeModifier(it.key) }
        attr.baseValue = DEFAULT_SCALE
        if (scale != null && scale != DEFAULT_SCALE) {
            attr.addModifier(AttributeModifier(scaleKey, scale - DEFAULT_SCALE, AttributeModifier.Operation.ADD_NUMBER))
        }
    }

    private fun applyFlight(player: Player, allowFlight: Boolean) {
        val gameModeAllowsFlight = player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR
        if (allowFlight || gameModeAllowsFlight) {
            player.allowFlight = true
            return
        }
        if (player.isFlying) player.isFlying = false
        player.allowFlight = false
    }

    private fun resolveWorld(worldData: WorldData): World? {
        return Bukkit.getWorld(plugin.worldService.getWorldFolderName(worldData))
    }

    companion object {
        private const val DEFAULT_GRAVITY = 0.08
        private const val DEFAULT_SCALE = 1.0
    }
}
