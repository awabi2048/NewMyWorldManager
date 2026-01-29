package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.scheduler.BukkitRunnable

class WorldUnloadService(private val plugin: MyWorldManager) {

    private val emptySince = mutableMapOf<String, Long>()
    private var task: BukkitRunnable? = null

    fun start() {
        stop() // Ensure no duplicates
        if (!plugin.config.getBoolean("world_unload.enabled", true)) return

        val interval = 20L * 60 // Run check every 1 minute
        
        task = object : BukkitRunnable() {
            override fun run() {
                checkWorlds()
            }
        }
        task?.runTaskTimer(plugin, interval, interval)
    }

    fun stop() {
        task?.cancel()
        task = null
        emptySince.clear()
    }

    private fun checkWorlds() {
        val config = plugin.config
        val thresholdMinutes = config.getInt("world_unload.time_minutes", 5)
        val thresholdMillis = thresholdMinutes * 60 * 1000L
        val excludes = config.getStringList("world_unload.exclude_worlds")

        val currentMillis = System.currentTimeMillis()
        val worlds = Bukkit.getWorlds()

        for (world in worlds) {
            if (excludes.contains(world.name)) {
                emptySince.remove(world.name)
                continue
            }

            if (world.players.isEmpty()) {
                if (!emptySince.containsKey(world.name)) {
                    emptySince[world.name] = currentMillis
                } else {
                    val since = emptySince[world.name]!!
                    if (currentMillis - since >= thresholdMillis) {
                        unloadWorld(world)
                    }
                }
            } else {
                emptySince.remove(world.name)
            }
        }
        
        // Cleanup map for unloaded worlds
        emptySince.keys.removeIf { worldName -> Bukkit.getWorld(worldName) == null }
    }

    private fun unloadWorld(world: World) {
        plugin.logger.info("[MyWorldManager] Unloading world ${world.name} due to inactivity...")
        val success = Bukkit.unloadWorld(world, true)
        if (success) {
            emptySince.remove(world.name)
        } else {
            plugin.logger.warning("[MyWorldManager] Failed to unload world ${world.name}.")
        }
    }
}
