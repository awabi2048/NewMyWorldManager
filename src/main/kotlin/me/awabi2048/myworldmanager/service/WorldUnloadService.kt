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
            val worldName = world.name
            if (excludes.contains(worldName)) {
                emptySince.remove(worldName)
                continue
            }

            // マイワールドのデータを確認
            val worldData = plugin.worldConfigRepository.findByWorldName(worldName)
            if (worldData == null) {
                // 非マイワールドは対象外
                emptySince.remove(worldName)
                continue
            }

            // Admin convert ワールドのチェック
            // sourceWorld == "CONVERT" かつ 名前の形式が my_world.<uuid> でない場合は除外
            if (worldData.sourceWorld == "CONVERT" && !worldName.startsWith("my_world.")) {
                emptySince.remove(worldName)
                continue
            }

            if (world.players.isEmpty()) {
                if (!emptySince.containsKey(worldName)) {
                    emptySince[worldName] = currentMillis
                } else {
                    val since = emptySince[worldName]!!
                    if (currentMillis - since >= thresholdMillis) {
                        unloadWorld(world)
                    }
                }
            } else {
                emptySince.remove(worldName)
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
