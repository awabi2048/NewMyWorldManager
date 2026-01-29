package me.awabi2048.myworldmanager.repository

import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID

class WorldConfigRepository(private val plugin: JavaPlugin) {

    private val worldsFolder = File(plugin.dataFolder, "my_worlds")
    private val cache = mutableMapOf<UUID, WorldData>()
    private val nameCache = mutableMapOf<String, WorldData>()

    init {
        if (!worldsFolder.exists()) {
            worldsFolder.mkdirs()
        }
        loadAll()
    }

    /**
     * ファイルからすべてのワールドデータをキャッシュに再読み込みする
     */
    fun loadAll() {
        cache.clear()
        nameCache.clear()
        val files = worldsFolder.listFiles { f -> f.extension == "yml" } ?: return

        for (file in files) {
            try {
                val uuid = UUID.fromString(file.nameWithoutExtension)
                val config = YamlConfiguration.loadConfiguration(file)
                
                // Bukkitの自動デシリアライズ機能を使用
                // ConfigurationSerialization.registerClass(WorldData::class.java) はメインクラスで行う必要がある
                val worldData = config.get("world_data") as? WorldData
                if (worldData != null) {
                    cache[uuid] = worldData
                    nameCache[worldData.customWorldName ?: "my_world.${worldData.uuid}"] = worldData
                } else {
                    // 旧互換用、またはデシリアライズ失敗時のフォールバック
                    plugin.logger.warning("ファイル ${file.name} のワールドデータのデシリアライズに失敗しました。")
                }
            } catch (e: Exception) {
                plugin.logger.warning("ファイル ${file.name} のワールドデータの読み込みに失敗しました: ${e.message}")
            }
        }
    }

    /**
     * ワールドデータを保存しキャッシュを更新する
     */
    fun save(worldData: WorldData) {
        val file = File(worldsFolder, "${worldData.uuid}.yml")
        val config = YamlConfiguration()

        // 階層を一段深くして保存（後で他の情報を入れる可能性を考慮）
        config.set("world_data", worldData)
        
        try {
            config.save(file)
        } catch (e: Exception) {
            plugin.logger.severe("Could not save world data for ${worldData.uuid}: ${e.message}")
        }
        
        // 旧名のキャッシュを削除する必要がある場合があるが、基本的には customWorldName は不変か、
        // 変更時に save が呼ばれる。
        // 安全のため一旦全クリアして再構築するか、save 前のデータを取得して削除する。
        // ここでは簡易的に、現在の cache にある古い名前を削除してから新しい名前を追加する。
        cache[worldData.uuid]?.let { oldData ->
            nameCache.remove(oldData.customWorldName ?: "my_world.${oldData.uuid}")
        }

        cache[worldData.uuid] = worldData
        nameCache[worldData.customWorldName ?: "my_world.${worldData.uuid}"] = worldData
    }

    /**
     * UUIDを指定してキャッシュからワールドデータを取得する
     */
    fun findByUuid(uuid: UUID): WorldData? {
        return cache[uuid]
    }

    /**
     * すべてのキャッシュされたワールドデータを取得する
     */
    fun findAll(): List<WorldData> {
        return cache.values.toList()
    }

    /**
     * 指定されたUUIDのワールドデータを削除する
     */
    fun delete(uuid: UUID) {
        cache[uuid]?.let { data ->
            nameCache.remove(data.customWorldName ?: "my_world.${data.uuid}")
        }
        cache.remove(uuid)
        val file = File(worldsFolder, "$uuid.yml")
        if (file.exists()) {
            file.delete()
        }
    }

    /**
     * ディレクトリ名を指定してマイワールドを検索する
     */
    fun findByWorldName(worldName: String): WorldData? {
        return nameCache[worldName]
    }
}
