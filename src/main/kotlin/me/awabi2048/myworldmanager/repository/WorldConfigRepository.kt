package me.awabi2048.myworldmanager.repository

import me.awabi2048.myworldmanager.model.ManagedDimension
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.time.Instant
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardCopyOption
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.Locale
import java.util.UUID

class WorldConfigRepository(private val plugin: JavaPlugin) {

    private val worldsFolder = File(plugin.dataFolder, "my_worlds")
    private val cache = mutableMapOf<UUID, WorldData>()
    private val nameCache = mutableMapOf<String, WorldData>()
    private val quarantined = mutableMapOf<String, QuarantinedWorldData>()

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
        quarantined.clear()
        val files = worldsFolder.listFiles { f -> f.extension == "yml" } ?: return

        for (file in files) {
            try {
                val uuid = UUID.fromString(file.nameWithoutExtension)
                val worldData = loadWorldData(file)
                if (worldData != null) {
                    check(worldData.uuid == uuid) {
                        "world data UUID does not match file name: ${file.name}"
                    }
                    check(org.bukkit.NamespacedKey.fromString(worldData.worldKey) != null) {
                        "invalid world_key: ${worldData.worldKey}"
                    }
                    cache[uuid] = worldData
                    nameCache[toWorldFolderName(worldData)] = worldData
                } else {
                    registerQuarantine(file, uuid, "world_data is missing")
                    // 旧互換用、またはデシリアライズ失敗時のフォールバック
                    plugin.logger.warning("ファイル ${file.name} のワールドデータのデシリアライズに失敗しました。")
                }
            } catch (e: Exception) {
                registerQuarantine(file, file.nameWithoutExtension.toUuidOrNull(), e.message ?: e.javaClass.simpleName)
                plugin.logger.warning("ファイル ${file.name} のワールドデータの読み込みに失敗しました: ${e.message}")
            }
        }
    }

    /**
     * ワールドデータを保存しキャッシュを更新する
     */
    @Synchronized
    fun save(worldData: WorldData) {
        check(!isQuarantined(worldData.uuid)) {
            "Cannot save quarantined world data: ${worldData.uuid}"
        }
        val file = File(worldsFolder, "${worldData.uuid}.yml")
        val temporary = File(worldsFolder, "${worldData.uuid}.yml.tmp")
        val config = YamlConfiguration()

        // 階層を一段深くして保存（後で他の情報を入れる可能性を考慮）
        config.set("world_data", worldData)

        try {
            config.save(temporary)
            FileChannel.open(temporary.toPath(), StandardOpenOption.WRITE).use { it.force(true) }
            val verified = loadWorldData(temporary)
            check(verified?.uuid == worldData.uuid) {
                "Temporary world data verification failed for ${worldData.uuid}"
            }
            atomicReplace(temporary, file)
        } catch (e: Exception) {
            plugin.logger.severe("Could not save world data for ${worldData.uuid}: ${e.message}")
            temporary.delete()
            restoreCacheFromDisk(worldData.uuid)
            throw IllegalStateException("Could not save world data for ${worldData.uuid}", e)
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

    private fun atomicReplace(temporary: File, target: File) {
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
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

    /** 隔離データを未登録と混同しないための状態参照です。 */
    @Synchronized
    fun quarantinedWorlds(): List<QuarantinedWorldData> = quarantined.values.sortedBy { it.fileName }

    @Synchronized
    fun isQuarantined(uuid: UUID): Boolean = quarantined.values.any { it.uuid == uuid }

    /** 隔離済みデータを含めて、表示名の予約状況を確認します。 */
    @Synchronized
    fun hasDisplayNameConflict(ownerUuid: UUID, worldName: String, excludingUuid: UUID? = null): Boolean {
        if (findByOwnerAndDisplayName(ownerUuid, worldName, excludingUuid) != null) return true
        val normalized = normalizeDisplayName(worldName)
        return quarantined.values.any {
            it.uuid != excludingUuid &&
                it.owner == ownerUuid &&
                it.worldName?.let(::normalizeDisplayName) == normalized
        }
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
        check(!isQuarantined(uuid)) {
            "Cannot delete quarantined world data: $uuid"
        }
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

    /**
     * /mwm migration からだけ呼び出される生YAML移行です。
     * dimensionを確定できない場合はファイルを変更せず、管理者入力を要求します。
     */
    @Synchronized
    internal fun migrateWorldData(
        uuid: UUID,
        dimension: ManagedDimension? = null,
        forceDimension: Boolean = false,
    ): MetadataMigrationResult {
        val quarantinedData = quarantined.values.firstOrNull { it.uuid == uuid }
        val file = quarantinedData?.let { File(worldsFolder, it.fileName) }
            ?: File(worldsFolder, "$uuid.yml")
        if (!file.isFile) {
            return MetadataMigrationResult(MetadataMigrationStatus.FAILED, "world data file not found: $uuid")
        }

        val original = runCatching {
            Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)
        }.getOrElse {
            return MetadataMigrationResult(MetadataMigrationStatus.FAILED, "cannot read $file: ${it.message}")
        }

        if (forceDimension && quarantinedData == null) {
            return MetadataMigrationResult(
                MetadataMigrationStatus.FAILED,
                "world data is not quarantined: $uuid"
            )
        }
        val currentHash = MigrationFileFingerprint.sha256(file)
        if (quarantinedData?.contentHash != null && quarantinedData.contentHash != currentHash) {
            return MetadataMigrationResult(
                MetadataMigrationStatus.FAILED,
                "world data changed after quarantine: $uuid"
            )
        }

        // 次元はワールド生成方式を決めるため、推測値で部分移行しません。
        val rawDimension = WorldDataYamlMigration.readField(original, "dimension")
        if (dimension == null && (rawDimension == null || WorldDataYamlMigration.normalizeDimension(rawDimension) == null)) {
            return MetadataMigrationResult(MetadataMigrationStatus.NEEDS_INPUT, "dimension is required: $uuid")
        }

        val migrationDimension = if (
            !forceDimension && rawDimension != null && WorldDataYamlMigration.normalizeDimension(rawDimension) != null
        ) {
            null
        } else {
            dimension
        }
        val migrated = WorldDataYamlMigration.migrate(original, uuid, migrationDimension?.name)
        if (migrated == null) {
            val current = runCatching { loadWorldData(file)?.takeIf(::isCurrentWorldData) }.getOrNull()
            return if (current != null) {
                MetadataMigrationResult(MetadataMigrationStatus.ALREADY_CURRENT, "already current: $uuid")
            } else {
                MetadataMigrationResult(MetadataMigrationStatus.FAILED, "no supported migration applies: $uuid")
            }
        }

        val canonicalFile = File(worldsFolder, "$uuid.yml")
        if (file.absoluteFile != canonicalFile.absoluteFile && canonicalFile.exists()) {
            return MetadataMigrationResult(
                MetadataMigrationStatus.FAILED,
                "canonical world data file already exists: ${canonicalFile.name}"
            )
        }
        val backup = File(file.parentFile, "${file.name}.pre-migration-${System.currentTimeMillis()}.bak")
        val temporary = File(worldsFolder, "${file.name}.migration.tmp")
        try {
            Files.copy(file.toPath(), backup.toPath())
            Files.write(
                temporary.toPath(),
                (migrated.joinToString(System.lineSeparator()) + System.lineSeparator())
                    .toByteArray(StandardCharsets.UTF_8)
            )
            atomicReplace(temporary, file)
            check(loadWorldData(file)?.let(::isCurrentWorldData) == true) {
                "migrated world data could not be deserialized: $uuid"
            }
            if (file.absoluteFile != canonicalFile.absoluteFile) {
                try {
                    Files.move(file.toPath(), canonicalFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(file.toPath(), canonicalFile.toPath())
                }
            }
            return MetadataMigrationResult(MetadataMigrationStatus.MIGRATED, "migrated: $uuid")
        } catch (e: Exception) {
            temporary.delete()
            runCatching { Files.copy(backup.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            return MetadataMigrationResult(
                MetadataMigrationStatus.FAILED,
                "migration failed for $uuid: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    private fun loadWorldData(file: File): WorldData? {
        val config = YamlConfiguration.loadConfiguration(file)
        // Bukkitの自動デシリアライズ機能を使用
        // ConfigurationSerialization.registerClass(WorldData::class.java) はメインクラスで行う必要がある
        val worldData = config.get("world_data") as? WorldData ?: return null
        return worldData
    }

    private fun registerQuarantine(file: File, uuid: UUID?, reason: String) {
        val lines = runCatching { Files.readAllLines(file.toPath(), StandardCharsets.UTF_8) }.getOrDefault(emptyList())
        val contentUuid = WorldDataYamlMigration.readField(lines, "uuid")?.toUuidOrNull()
        quarantined[file.name] = QuarantinedWorldData(
            uuid = uuid ?: contentUuid,
            fileName = file.name,
            reason = reason,
            detectedAt = Instant.now(),
            worldName = WorldDataYamlMigration.readField(lines, "name"),
            owner = WorldDataYamlMigration.readField(lines, "owner")?.toUuidOrNull(),
            worldKey = WorldDataYamlMigration.readField(lines, "world_key"),
            customWorldName = WorldDataYamlMigration.readField(lines, "custom_world_name"),
            contentHash = MigrationFileFingerprint.sha256(file),
        )
        plugin.logger.warning("ファイル ${file.name} のワールドデータを隔離しました: $reason")
    }

    private fun isCurrentWorldData(worldData: WorldData): Boolean =
        org.bukkit.NamespacedKey.fromString(worldData.worldKey) != null

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

    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
}
