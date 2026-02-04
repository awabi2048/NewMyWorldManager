package me.awabi2048.myworldmanager.repository

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PlayerStats
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlayerStatsRepository(private val plugin: MyWorldManager) {
    private val statsFolder = File(plugin.dataFolder, "playerdata")
    private val cache = ConcurrentHashMap<UUID, PlayerStats>()

    init {
        if (!statsFolder.exists()) {
            statsFolder.mkdirs()
        }
    }

    /**
     * プレイヤーの統計情報を取得する。キャッシュになければファイルから読み込む。
     */
    fun findByUuid(uuid: UUID): PlayerStats {
        return cache.getOrPut(uuid) {
            loadFromFile(uuid) ?: createDefault(uuid)
        }
    }

    private fun createDefault(uuid: UUID): PlayerStats {
        val config = plugin.config
        val stats = PlayerStats(
            playerUuid = uuid,
            worldPoint = config.getInt("default_player_stats.world_point", 0),
            // unlockedWarpSlot removed
            unlockedWorldSlot = config.getInt("default_player_stats.unlocked_world_slot", 0)
        )
        save(stats)
        return stats
    }

    /**
     * プレイヤーの統計情報を保存する。
     */
    fun save(stats: PlayerStats) {
        cache[stats.playerUuid] = stats
        saveToFile(stats)
    }

    private fun loadFromFile(uuid: UUID): PlayerStats? {
        val file = File(statsFolder, "$uuid.yml")
        if (!file.exists()) return null

        val config = YamlConfiguration.loadConfiguration(file)
        
        val loadedRegisteredWarp = config.getStringList("registered_warp")
            .mapNotNull { try { UUID.fromString(it) } catch (e: Exception) { null } }
        
        val favoriteWorldsSection = config.getConfigurationSection("favorite_worlds")
        val loadedFavoriteWorlds = favoriteWorldsSection?.let { section ->
            section.getKeys(false).associate { UUID.fromString(it) to section.getString(it)!! }.toMutableMap()
        } ?: mutableMapOf()

        val loadedWorldDisplayOrder = config.getStringList("world_display_order")
            .mapNotNull { try { UUID.fromString(it) } catch (e: Exception) { null } }

        // 存在しないワールドのUUIDを除去する（自動クリーンアップ）
        val existingRegisteredWarp = loadedRegisteredWarp.filter { uuid ->
            plugin.worldConfigRepository.findByUuid(uuid) != null
        }.toMutableList()
        
        val existingFavoriteWorlds = loadedFavoriteWorlds.filterKeys { uuid ->
            plugin.worldConfigRepository.findByUuid(uuid) != null
        }.toMutableMap()

        val existingWorldDisplayOrder = loadedWorldDisplayOrder.filter { uuid ->
            plugin.worldConfigRepository.findByUuid(uuid) != null
        }.toMutableList()

        val stats = PlayerStats(
            playerUuid = uuid,
            worldPoint = config.getInt("world_point", 0),
            // unlockedWarpSlot removed
            unlockedWorldSlot = config.getInt("unlocked_world_slot", 0),
            registeredWarp = existingRegisteredWarp,
            favoriteWorlds = existingFavoriteWorlds,
            lastOnline = config.getString("last_online"),
            lastName = config.getString("last_name"),
            language = config.getString("language", "ja_jp") ?: "ja_jp",
            visitorNotificationEnabled = config.getBoolean("visitor_notification_enabled", true),
            criticalSettingsEnabled = config.getBoolean("critical_settings_enabled", true),
            meetStatus = if (config.contains("meet_status")) {
                config.getString("meet_status", "JOIN_ME")!!
            } else {
                if (config.getBoolean("meet_enabled", true)) "JOIN_ME" else "BUSY"
            },
            betaFeaturesEnabled = config.getBoolean("beta_features_enabled", false),
            worldDisplayOrder = existingWorldDisplayOrder
        )

        // 変更があった場合は保存する
        if (loadedRegisteredWarp.size != existingRegisteredWarp.size || 
            loadedFavoriteWorlds.size != existingFavoriteWorlds.size ||
            loadedWorldDisplayOrder.size != existingWorldDisplayOrder.size) {
            plugin.logger.info("[PlayerStats] ${uuid} の存在しないワールドのUUIDをクリーンアップしました。")
            saveToFile(stats)
        }

        return stats
    }

    private fun saveToFile(stats: PlayerStats) {
        val file = File(statsFolder, "${stats.playerUuid}.yml")
        val config = YamlConfiguration()

        config.set("world_point", stats.worldPoint)
        // unlockedWarpSlot removed
        config.set("unlocked_world_slot", stats.unlockedWorldSlot)
        config.set("registered_warp", stats.registeredWarp.map { it.toString() })
        
        val favSection = config.createSection("favorite_worlds")
        stats.favoriteWorlds.forEach { (uuid, date) ->
            favSection.set(uuid.toString(), date)
        }
        
        config.set("world_display_order", stats.worldDisplayOrder.map { it.toString() })
        
        config.set("last_online", stats.lastOnline)
        config.set("last_name", stats.lastName)
        config.set("language", stats.language)
        config.set("visitor_notification_enabled", stats.visitorNotificationEnabled)
        config.set("critical_settings_enabled", stats.criticalSettingsEnabled)
        config.set("meet_status", stats.meetStatus)
        config.set("beta_features_enabled", stats.betaFeaturesEnabled)

        try {
            config.save(file)
        } catch (e: Exception) {
            plugin.logger.severe("Could not save player stats for ${stats.playerUuid}: ${e.message}")
        }
    }
    
    /**
     * キャッシュをクリアする（メモリ節約のため、プレイヤー退出時などに呼び出し可能）。
     */
    fun uncache(uuid: UUID) {
        cache.remove(uuid)
    }

    /**
     * 全キャッシュをクリアする（リロード時などに使用）。
     */
    fun clearCache() {
        cache.clear()
    }

    /**
     * すべてのプレイヤー統計ファイルのリストを取得する。
     */
    fun findAllFiles(): Array<File> {
        return statsFolder.listFiles { f -> f.extension == "yml" } ?: emptyArray()
    }

    /**
     * すべてのプレイヤーデータを読み込み直して保存する（データ形式の更新用）
     */
    fun updateAllData(): Int {
        val files = findAllFiles()
        var count = 0
        for (file in files) {
            try {
                val uuidStr = file.nameWithoutExtension
                val uuid = UUID.fromString(uuidStr)
                val stats = loadFromFile(uuid)
                if (stats != null) {
                    saveToFile(stats)
                    count++
                }
            } catch (e: Exception) {
                plugin.logger.warning("Failed to update data for file ${file.name}: ${e.message}")
            }
        }
        return count
    }
}
