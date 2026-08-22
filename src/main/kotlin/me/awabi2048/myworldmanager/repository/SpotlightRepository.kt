package me.awabi2048.myworldmanager.repository

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.service.ApiMigrationParticipant
import me.awabi2048.myworldmanager.api.service.ApiMigrationParticipantResult
import me.awabi2048.myworldmanager.api.service.ApiMigrationParticipantResultState
import me.awabi2048.myworldmanager.api.service.ApiMigrationParticipantState
import me.awabi2048.myworldmanager.api.service.ApiMigrationParticipantStatus
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID

/**
 * Spotlightの参照リストを管理します。
 * 参照先が不明なUUIDは読み込み時に削除せず、隔離レコードとして原本へ保持します。
 */
class SpotlightRepository(private val plugin: MyWorldManager) {
    private val file = File(plugin.dataFolder, "spotlight.yml")
    private var spotlightUuids = mutableListOf<UUID>()
    private var spotlightDescription: String? = null
    private var quarantinedEntries = linkedMapOf<String, String>()
    /** 元の worlds リストに含まれていた不正値を、通常保存時にも失わないための退避領域です。 */
    private var quarantinedRawEntries = mutableListOf<Any?>()
    private var configVersion = CURRENT_SCHEMA_VERSION
    private var fileFailure: String? = null
    private val limit = 10

    private val migrationExecutor = object : ApiMigrationParticipant {
        override fun getId(): String = PARTICIPANT_ID

        override fun status(): ApiMigrationParticipantStatus {
            val scan = scan()
            return when {
                scan.failure != null -> ApiMigrationParticipantStatus(PARTICIPANT_ID, ApiMigrationParticipantState.FAILED, scan.failure)
                scan.quarantined > 0 -> ApiMigrationParticipantStatus(
                    PARTICIPANT_ID,
                    ApiMigrationParticipantState.FAILED,
                    "spotlight.yml contains quarantined references: ${scan.quarantined}",
                )
                scan.version < CURRENT_SCHEMA_VERSION -> ApiMigrationParticipantStatus(
                    PARTICIPANT_ID,
                    ApiMigrationParticipantState.PENDING,
                    "spotlight.yml requires migration: ${scan.version}->$CURRENT_SCHEMA_VERSION",
                )
                else -> ApiMigrationParticipantStatus(PARTICIPANT_ID, ApiMigrationParticipantState.CURRENT)
            }
        }

        fun executeMigration(): ApiMigrationParticipantResult {
            val before = status()
            if (before.state == ApiMigrationParticipantState.CURRENT) {
                return ApiMigrationParticipantResult(ApiMigrationParticipantResultState.ALREADY_CURRENT, "already current: $PARTICIPANT_ID")
            }
            val scan = scan()
            if (scan.failure != null) {
                return ApiMigrationParticipantResult(ApiMigrationParticipantResultState.FAILED, scan.failure)
            }
            if (scan.version > CURRENT_SCHEMA_VERSION) {
                return ApiMigrationParticipantResult(
                    ApiMigrationParticipantResultState.FAILED,
                    "spotlight.yml is newer than this plugin: ${scan.version}",
                )
            }
            return runCatching {
                writeMigrated(scan.config ?: YamlConfiguration(), scan.rawQuarantine)
                load()
                val after = status()
                if (after.state == ApiMigrationParticipantState.CURRENT) {
                    ApiMigrationParticipantResult(ApiMigrationParticipantResultState.MIGRATED, "migrated: $PARTICIPANT_ID")
                } else {
                    ApiMigrationParticipantResult(
                        ApiMigrationParticipantResultState.FAILED,
                        after.message ?: "spotlight migration remains incomplete",
                    )
                }
            }.getOrElse { error ->
                ApiMigrationParticipantResult(
                    ApiMigrationParticipantResultState.FAILED,
                    "spotlight.yml migration failed: ${error.message ?: error.javaClass.simpleName}",
                )
            }
        }
    }

    val migrationParticipant: ApiMigrationParticipant = migrationExecutor

    init {
        MyWorldManagerApi.registerMigrationParticipant(migrationParticipant, migrationExecutor::executeMigration)
    }

    @Synchronized
    fun load() {
        spotlightUuids.clear()
        quarantinedEntries.clear()
        quarantinedRawEntries.clear()
        spotlightDescription = null
        configVersion = CURRENT_SCHEMA_VERSION
        fileFailure = null
        if (!file.exists()) return

        val config = runCatching { loadStrict(file) }.getOrElse { error ->
            fileFailure = "spotlight.yml is unreadable: ${error.message ?: error.javaClass.simpleName}"
            return
        }
        configVersion = runCatching { readVersion(config) }.getOrElse { error ->
            fileFailure = error.message ?: error.javaClass.simpleName
            return
        }
        if (configVersion > CURRENT_SCHEMA_VERSION) {
            fileFailure = "spotlight.yml is newer than this plugin: $configVersion"
            return
        }
        try {
            spotlightDescription = config.getString("description")?.trim()?.takeIf { it.isNotEmpty() }
            val list = config.get("worlds")
            if (list != null) {
                require(list is List<*>) { "spotlight worlds must be a list" }
                list.forEachIndexed { index, raw ->
                    if (raw !is String) {
                        quarantinedEntries["index:$index"] = "worlds entry is not a string"
                        quarantinedRawEntries += raw
                        return@forEachIndexed
                    }
                    val uuid = runCatching { UUID.fromString(raw) }.getOrNull()
                    if (uuid == null) {
                        quarantinedEntries[raw] = "worlds entry is not a UUID"
                        quarantinedRawEntries += raw
                    } else if (plugin.worldConfigRepository.findByUuid(uuid) != null) {
                        if (spotlightUuids.size < limit && uuid !in spotlightUuids) spotlightUuids += uuid
                    } else {
                        quarantinedEntries[raw] = "world metadata is unavailable"
                        quarantinedRawEntries += raw
                    }
                }
            }
            val quarantine = config.getConfigurationSection("quarantine")
            quarantine?.getKeys(false).orEmpty().forEach { key ->
                quarantinedEntries.putIfAbsent(key, quarantine?.getString(key) ?: "quarantined reference")
            }
        } catch (error: Exception) {
            // トップレベル型の破損は個別参照として復元できないため、原本を変更せずファイル全体を隔離します。
            spotlightUuids.clear()
            quarantinedEntries.clear()
            quarantinedRawEntries.clear()
            fileFailure = "spotlight.yml contains invalid data: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    fun findAll(): List<UUID> = spotlightUuids.toList()

    fun getDescription(): String? = spotlightDescription

    @Synchronized
    fun setDescription(description: String?) {
        ensureWritable()
        val normalized = description?.trim()?.takeIf { it.isNotEmpty() }
        if (spotlightDescription == normalized) return
        val previous = spotlightDescription
        spotlightDescription = normalized
        try {
            save()
        } catch (error: Exception) {
            spotlightDescription = previous
            throw error
        }
    }

    fun isSpotlight(uuid: UUID): Boolean = spotlightUuids.contains(uuid)

    @Synchronized
    fun add(uuid: UUID): Boolean {
        ensureWritable()
        if (plugin.worldConfigRepository.findByUuid(uuid) == null) return false
        if (spotlightUuids.size >= limit) return false
        if (spotlightUuids.contains(uuid)) return true
        spotlightUuids.add(uuid)
        return try {
            save()
            true
        } catch (error: Exception) {
            spotlightUuids.remove(uuid)
            throw error
        }
    }

    @Synchronized
    fun remove(uuid: UUID) {
        ensureWritable()
        if (!spotlightUuids.remove(uuid)) return
        try {
            save()
        } catch (error: Exception) {
            spotlightUuids.add(uuid)
            throw error
        }
    }

    private fun save() {
        val config = if (file.exists()) loadStrict(file) else YamlConfiguration()
        config.set("config_version", CURRENT_SCHEMA_VERSION)
        // 隔離済みの不正値も元の型・値のまま残し、健全な項目の更新で
        // 原本が静かに失われないようにします。
        config.set("worlds", spotlightUuids.map(UUID::toString) + quarantinedRawEntries)
        config.set("description", spotlightDescription)
        val quarantine = config.getConfigurationSection("quarantine") ?: config.createSection("quarantine")
        quarantine.getKeys(false).forEach { quarantine.set(it, null) }
        quarantinedEntries.forEach { (raw, reason) -> quarantine.set(raw, reason) }
        atomicSave(config)
    }

    private fun writeMigrated(source: YamlConfiguration, rawQuarantine: Map<String, String>) {
        val config = source
        config.set("config_version", CURRENT_SCHEMA_VERSION)
        config.set("quarantine", rawQuarantine)
        val backup = File(file.parentFile, "${file.name}.pre-migration-${System.currentTimeMillis()}.bak")
        val temporary = File(file.parentFile, "${file.name}.migration.tmp")
        try {
            if (file.exists()) Files.copy(file.toPath(), backup.toPath())
            config.save(temporary)
            FileChannel.open(temporary.toPath(), StandardOpenOption.WRITE).use { it.force(true) }
            loadStrict(temporary)
            moveAtomically(temporary, file)
            // 移行成功後のバックアップは復元に不要なため削除する。失敗時は従来どおりリストア用に残す。
            runCatching { backup.delete() }
        } catch (error: Exception) {
            Files.deleteIfExists(temporary.toPath())
            if (backup.isFile) Files.copy(backup.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            throw error
        } finally {
            Files.deleteIfExists(temporary.toPath())
        }
    }

    private fun scan(): Scan {
        if (!file.exists()) return Scan(CURRENT_SCHEMA_VERSION, 0, null, YamlConfiguration(), emptyMap())
        val config = runCatching { loadStrict(file) }.getOrElse { error ->
            return Scan(0, 0, "spotlight.yml is unreadable: ${error.message ?: error.javaClass.simpleName}", null, emptyMap())
        }
        val version = runCatching { readVersion(config) }.getOrElse { error ->
            return Scan(0, 0, error.message ?: error.javaClass.simpleName, config, emptyMap())
        }
        if (version > CURRENT_SCHEMA_VERSION) {
            return Scan(version, 0, "spotlight.yml is newer than this plugin: $version", config, emptyMap())
        }
        val raw = config.get("worlds")
        if (raw != null && raw !is List<*>) {
            return Scan(version, 0, "spotlight worlds must be a list", config, emptyMap())
        }
        val quarantine = linkedMapOf<String, String>()
        if (raw is List<*>) raw.forEachIndexed { index, value ->
            if (value !is String) {
                quarantine["index:$index"] = "worlds entry is not a string"
            } else {
                val uuid = runCatching { UUID.fromString(value) }.getOrNull()
                if (uuid == null) quarantine[value] = "worlds entry is not a UUID"
                else if (plugin.worldConfigRepository.findByUuid(uuid) == null) quarantine[value] = "world metadata is unavailable"
            }
        }
        config.getConfigurationSection("quarantine")?.let { section ->
            section.getKeys(false).forEach { key -> quarantine.putIfAbsent(key, section.getString(key) ?: "quarantined reference") }
        }
        return Scan(version, quarantine.size, null, config, quarantine)
    }

    private fun ensureWritable() {
        check(fileFailure == null) { fileFailure ?: "spotlight.yml is quarantined" }
        check(configVersion <= CURRENT_SCHEMA_VERSION) { "spotlight.yml requires /mwm migration" }
        check(configVersion == CURRENT_SCHEMA_VERSION || !file.exists()) { "spotlight.yml requires /mwm migration" }
    }

    private fun readVersion(config: YamlConfiguration): Int {
        val raw = config.get("config_version") ?: return 0
        require(raw is Number && raw.toDouble() % 1.0 == 0.0 && raw.toInt() >= 0) {
            "config_version must be a non-negative integer"
        }
        return raw.toInt()
    }

    private fun loadStrict(target: File): YamlConfiguration = YamlConfiguration().also { it.load(target) }

    private fun atomicSave(config: YamlConfiguration) {
        val temporary = File(file.parentFile, "${file.name}.save.tmp")
        try {
            file.parentFile?.mkdirs()
            config.save(temporary)
            FileChannel.open(temporary.toPath(), StandardOpenOption.WRITE).use { it.force(true) }
            loadStrict(temporary)
            moveAtomically(temporary, file)
        } finally {
            Files.deleteIfExists(temporary.toPath())
        }
    }

    private fun moveAtomically(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private data class Scan(
        val version: Int,
        val quarantined: Int,
        val failure: String?,
        val config: YamlConfiguration?,
        val rawQuarantine: Map<String, String>,
    )

    private companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val PARTICIPANT_ID = "myworld-spotlight"
    }
}
