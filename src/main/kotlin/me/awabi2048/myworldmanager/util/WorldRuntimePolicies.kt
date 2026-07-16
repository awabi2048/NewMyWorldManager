package me.awabi2048.myworldmanager.util

import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.extension.WorldCreationType as ApiWorldCreationType
import me.awabi2048.myworldmanager.session.WorldCreationType
import org.bukkit.configuration.file.FileConfiguration
import kotlin.math.pow

object WorldRuntimePolicies {
    fun creationCost(config: FileConfiguration, type: WorldCreationType): Int {
        val configured = when (type) {
            WorldCreationType.TEMPLATE -> config.getInt("creation_cost.template", 0)
            WorldCreationType.SEED -> config.getInt("creation_cost.seed", 100)
            WorldCreationType.RANDOM -> config.getInt("creation_cost.random", 50)
        }
        return MyWorldManagerApi.getWorldRuntimePolicy().getCreationCost(type.toApiType(), configured)
    }

    private fun me.awabi2048.myworldmanager.session.WorldCreationType.toApiType(): ApiWorldCreationType = when (this) {
        me.awabi2048.myworldmanager.session.WorldCreationType.TEMPLATE -> ApiWorldCreationType.TEMPLATE
        me.awabi2048.myworldmanager.session.WorldCreationType.SEED -> ApiWorldCreationType.SEED
        me.awabi2048.myworldmanager.session.WorldCreationType.RANDOM -> ApiWorldCreationType.RANDOM
    }

    fun maxCreateCountDefault(config: FileConfiguration): Int {
        val configured = config.getInt("creation.max_create_count_default", 3)
        return MyWorldManagerApi.getWorldRuntimePolicy().getMaxCreateCountDefault(configured)
    }

    fun maxWorldSlotLimit(config: FileConfiguration): Int {
        val configured = config.getInt("creation.max_world_slots_limit", 10)
        return MyWorldManagerApi.getWorldRuntimePolicy().getMaxWorldSlotLimit(configured)
    }

    fun reduceOwnerSlotOnDelete(config: FileConfiguration): Boolean {
        val configured = config.getBoolean("deletion.reduce_owner_slot", false)
        return MyWorldManagerApi.getWorldRuntimePolicy().shouldReduceOwnerSlotOnDelete(configured)
    }

    fun expansionCost(config: FileConfiguration, targetLevel: Int): Int {
        val configured = if (config.contains("expansion.costs.$targetLevel")) {
            config.getInt("expansion.costs.$targetLevel")
        } else {
            val baseCost = config.getInt("expansion.base_cost", 100)
            val multiplier = config.getDouble("expansion.cost_multiplier", 2.0)
            (baseCost * multiplier.pow((targetLevel - 1).toDouble())).toInt()
        }
        return MyWorldManagerApi.getWorldRuntimePolicy().getExpansionCost(targetLevel, configured)
    }

    fun totalExpansionCost(config: FileConfiguration, targetLevel: Int): Int {
        return (1..targetLevel).sumOf { expansionCost(config, it) }
    }

    fun environmentCost(config: FileConfiguration, type: String): Int {
        val configured = when (type) {
            "gravity" -> config.getInt("environment.gravity.cost", 100)
            "weather" -> config.getInt("environment.weather.cost", 50)
            "biome" -> config.getInt("environment.biome.cost", 500)
            else -> 0
        }
        return MyWorldManagerApi.getWorldRuntimePolicy().getEnvironmentCost(type, configured)
    }

    fun portalWorldGatePointCostPerBlock(config: FileConfiguration): Int {
        val configured = config.getInt("portal.world_gate.point_cost_per_block", 1).coerceAtLeast(0)
        return MyWorldManagerApi.getWorldRuntimePolicy().getPortalWorldGatePointCostPerBlock(configured)
    }
}
