package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.scheduler.BukkitRunnable

class WorldUnloadService(private val plugin: MyWorldManager) {

    private val emptySince = mutableMapOf<String, Long>()
    private val nextRetryAt = mutableMapOf<String, Long>()
    private val failureCount = mutableMapOf<String, Int>()
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
        nextRetryAt.clear()
        failureCount.clear()
    }

    private fun checkWorlds() {
        val config = plugin.config
        val thresholdMinutes = config.getInt("world_unload.time_minutes", 5).coerceAtLeast(1)
        val thresholdMillis = thresholdMinutes.toLong() * 60_000L
        val maxPerCycle = config.getInt("world_unload.max_per_cycle", 1).coerceAtLeast(1)
        val retryBaseMinutes = config.getInt("world_unload.failure_retry_minutes", 5).coerceAtLeast(1)
        val retryMaxMinutes =
                config.getInt("world_unload.failure_retry_max_minutes", 30)
                        .coerceAtLeast(retryBaseMinutes)
        val excludes = config.getStringList("world_unload.exclude_worlds")

        val currentMillis = System.currentTimeMillis()
        val worlds = Bukkit.getWorlds()
        var unloadedCount = 0

        for (world in worlds) {
            val worldName = world.name
            if (excludes.contains(worldName)) {
                emptySince.remove(worldName)
                nextRetryAt.remove(worldName)
                failureCount.remove(worldName)
                continue
            }

            // マイワールドのデータを確認
            val worldData = plugin.worldConfigRepository.findByWorldName(worldName)
            if (worldData == null) {
                // 非マイワールドは対象外
                emptySince.remove(worldName)
                nextRetryAt.remove(worldName)
                failureCount.remove(worldName)
                continue
            }

            // Admin convert ワールドのチェック
            // sourceWorld == "CONVERT" かつ 名前の形式が my_world.<uuid> でない場合は除外
            if (worldData.sourceWorld == "CONVERT" && !worldName.startsWith("my_world.")) {
                emptySince.remove(worldName)
                nextRetryAt.remove(worldName)
                failureCount.remove(worldName)
                continue
            }

            if (world.players.isEmpty()) {
                if (!emptySince.containsKey(worldName)) {
                    emptySince[worldName] = currentMillis
                } else {
                    val since = emptySince[worldName]!!
                    if (currentMillis - since >= thresholdMillis && unloadedCount < maxPerCycle) {
                        val retryAt = nextRetryAt[worldName]
                        if (retryAt != null && currentMillis < retryAt) {
                            continue
                        }

                        when (unloadWorld(world, worldData.uuid)) {
                            true -> {
                                emptySince.remove(worldName)
                                nextRetryAt.remove(worldName)
                                failureCount.remove(worldName)
                                unloadedCount++
                            }
                            false -> {
                                registerUnloadFailure(
                                        worldName,
                                        currentMillis,
                                        retryBaseMinutes,
                                        retryMaxMinutes
                                )
                            }
                            null -> {
                                // 作業ロック中は次のサイクルで再試行する
                            }
                        }
                    }
                }
            } else {
                emptySince.remove(worldName)
                nextRetryAt.remove(worldName)
                failureCount.remove(worldName)
            }
        }

        // Cleanup map for unloaded worlds
        emptySince.keys.removeIf { worldName -> Bukkit.getWorld(worldName) == null }
        nextRetryAt.keys.removeIf { worldName -> Bukkit.getWorld(worldName) == null }
        failureCount.keys.removeIf { worldName -> Bukkit.getWorld(worldName) == null }
    }

    /**
     * アンロードを試行します。他作業のロック占有中は null を返し、
     * 失敗扱い（リトライ間隔の延長）にはしません。
     */
    private fun unloadWorld(world: World, worldUuid: java.util.UUID): Boolean? {
        // バックアップ等の作業ロック中のアンロードは、ディスク書き込みと並行して
        // 走るためスキップします。ロックは1ワールド1作業の排他なので、取得できた
        // 場合のみ実行して即解放します。
        val lease = me.awabi2048.myworldmanager.api.MyWorldManagerApi.tryAcquireWorldOperation(
            worldUuid,
            me.awabi2048.myworldmanager.api.service.WorldOperation.UNLOAD,
        ) ?: return null
        return try {
            plugin.logger.info("[MyWorldManager] Unloading world ${world.name} due to inactivity...")
            val success = Bukkit.unloadWorld(world, true)
            if (success) {
                emptySince.remove(world.name)
            } else {
                plugin.logger.warning("[MyWorldManager] Failed to unload world ${world.name}.")
            }
            success
        } finally {
            lease.close()
        }
    }

    private fun registerUnloadFailure(
            worldName: String,
            currentMillis: Long,
            retryBaseMinutes: Int,
            retryMaxMinutes: Int
    ) {
        val failures = (failureCount[worldName] ?: 0) + 1
        failureCount[worldName] = failures

        val shift = (failures - 1).coerceAtMost(10)
        val retryMinutes =
                minOf(
                        retryMaxMinutes.toLong(),
                        retryBaseMinutes.toLong() * (1L shl shift)
                )

        nextRetryAt[worldName] = currentMillis + retryMinutes * 60_000L
        plugin.logger.warning(
                "[MyWorldManager] Failed to unload world $worldName ($failures times). Retry in $retryMinutes minutes."
        )
    }
}
