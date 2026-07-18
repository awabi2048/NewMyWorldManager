package me.awabi2048.myworldmanager.repository

import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.Locale
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
    @Synchronized
    fun loadAll() {
        cache.clear()
        nameCache.clear()
        val files = worldsFolder.listFiles { f -> f.extension == "yml" } ?: return

        for (file in files) {
            try {
                val uuid = UUID.fromString(file.nameWithoutExtension)
                val worldData = loadWorldData(file)
                if (worldData != null) {
                    cache[uuid] = worldData
                    nameCache[toWorldFolderName(worldData)] = worldData
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
    @Synchronized
    fun save(worldData: WorldData) {
        val file = File(worldsFolder, "${worldData.uuid}.yml")
        val config = YamlConfiguration()

        // 階層を一段深くして保存（後で他の情報を入れる可能性を考慮）
        config.set("world_data", worldData)

        try {
            config.save(file)
        } catch (e: Exception) {
            plugin.logger.severe("Could not save world data for ${worldData.uuid}: ${e.message}")
            restoreCacheFromDisk(worldData.uuid)
            return
        }

        // 旧名のキャッシュを削除する必要がある場合があるが、基本的には customWorldName は不変か、
        // 変更時に save が呼ばれる。
        // 安全のため一旦全クリアして再構築するか、save 前のデータを取得して削除する。
        // ここでは簡易的に、現在の cache にある古い名前を削除してから新しい名前を追加する。
        cache[worldData.uuid]?.let { oldData ->
            nameCache.remove(toWorldFolderName(oldData))
        }

        cache[worldData.uuid] = worldData
        nameCache[toWorldFolderName(worldData)] = worldData
    }

    /**
     * UUIDを指定してキャッシュからワールドデータを取得する
     */
    @Synchronized
    fun findByUuid(uuid: UUID): WorldData? {
        return cache[uuid]
    }

    /**
     * すべてのキャッシュされたワールドデータを取得する
     */
    @Synchronized
    fun findAll(): List<WorldData> {
        return cache.values.toList()
    }

    /**
     * 所有者UUIDを指定してワールドデータを取得する
     */
    @Synchronized
    fun findByOwner(ownerUuid: UUID): List<WorldData> {
        return cache.values.filter { it.owner == ownerUuid }
    }

    /**
     * プレイヤーが所有するワールドデータを取得する
     */
    fun findByOwner(player: Player): List<WorldData> {
        return findByOwner(player.uniqueId)
    }

    @Synchronized
    fun findByOwnerAndDisplayName(ownerUuid: UUID, worldName: String, excludingUuid: UUID? = null): WorldData? {
        val normalized = normalizeDisplayName(worldName)
        // 表示名の重複制約はオーナー単位で扱う。フォルダ名ではなくプレイヤーに見える名前を基準にする。
        return cache.values.firstOrNull {
            it.owner == ownerUuid &&
                it.uuid != excludingUuid &&
                normalizeDisplayName(it.name) == normalized
        }
    }

    /**
     * 指定されたUUIDのワールドデータを削除する
     */
    @Synchronized
    fun delete(uuid: UUID) {
        cache[uuid]?.let { data ->
            nameCache.remove(toWorldFolderName(data))
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
    @Synchronized
    fun findByWorldName(worldName: String): WorldData? {
        return nameCache[worldName]
    }

    @Synchronized
    fun findByWorldKey(worldKey: String): WorldData? {
        return cache.values.firstOrNull { it.worldKey == worldKey }
    }

    private fun toWorldFolderName(worldData: WorldData): String {
        return org.bukkit.NamespacedKey.fromString(worldData.worldKey)?.key
            ?: throw IllegalStateException("Invalid world_key for ${worldData.uuid}: ${worldData.worldKey}")
    }

    private fun normalizeDisplayName(name: String): String {
        return name.trim().lowercase(Locale.ROOT)
    }

    private fun loadWorldData(file: File): WorldData? {
        val config = YamlConfiguration.loadConfiguration(file)
        // Bukkitの自動デシリアライズ機能を使用
        // ConfigurationSerialization.registerClass(WorldData::class.java) はメインクラスで行う必要がある
        val worldData = config.get("world_data") as? WorldData ?: return null
        return worldData
    }

    private fun restoreCacheFromDisk(uuid: UUID) {
        nameCache.entries.removeIf { (_, data) -> data.uuid == uuid }

        val file = File(worldsFolder, "$uuid.yml")
        if (!file.exists()) {
            cache.remove(uuid)
            plugin.logger.warning("world data cache rollback skipped because file not found: $uuid")
            return
        }

        try {
            val worldData = loadWorldData(file)
            if (worldData != null) {
                cache[uuid] = worldData
                nameCache[toWorldFolderName(worldData)] = worldData
            } else {
                cache.remove(uuid)
                plugin.logger.warning("world data cache rollback failed due to deserialization error: $uuid")
            }
        } catch (e: Exception) {
            cache.remove(uuid)
            plugin.logger.warning("world data cache rollback failed for $uuid: ${e.message}")
        }
    }
}
