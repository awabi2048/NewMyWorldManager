package me.awabi2048.myworldmanager.repository

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.service.ApiMigrationParticipant
import me.awabi2048.myworldmanager.api.service.ApiMigrationParticipantResult
import me.awabi2048.myworldmanager.api.service.ApiMigrationParticipantResultState
import me.awabi2048.myworldmanager.api.service.ApiMigrationParticipantState
import me.awabi2048.myworldmanager.api.service.ApiMigrationParticipantStatus
import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.model.PortalType
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/**
 * ポータル永続データを管理します。
 *
 * 旧形式はメモリ上でのみ読み取り、通常操作で portals.yml を再構築しません。
 * 隔離レコードは原本へ保持したまま、健全なレコードの更新だけを許可します。
 */
class PortalRepository(private val plugin: MyWorldManager) {
    private val file = File(plugin.dataFolder, "portals.yml")
    private val portals = ConcurrentHashMap<UUID, PortalData>()
    private val quarantined = linkedMapOf<String, String>()
    private val validIdsLoadedFromDisk = mutableSetOf<String>()
    private var fileFailure: String? = null
    private var loadedConfigVersion = CURRENT_SCHEMA_VERSION

    private val migrationExecutor = object : ApiMigrationParticipant {
        override fun getId(): String = PARTICIPANT_ID

        override fun status(): ApiMigrationParticipantStatus {
            val scan = scanDisk()
            return when {
                scan.fileFailure != null -> ApiMigrationParticipantStatus(
                    PARTICIPANT_ID,
                    ApiMigrationParticipantState.FAILED,
                    scan.fileFailure,
                )
                scan.quarantined > 0 -> ApiMigrationParticipantStatus(
                    PARTICIPANT_ID,
                    ApiMigrationParticipantState.FAILED,
                    "portals.yml contains quarantined records: ${scan.quarantined}",
                )
                scan.configVersion < CURRENT_SCHEMA_VERSION -> ApiMigrationParticipantStatus(
                    PARTICIPANT_ID,
                    ApiMigrationParticipantState.PENDING,
                    "portals.yml requires migration: ${scan.configVersion}->$CURRENT_SCHEMA_VERSION",
                )
                else -> ApiMigrationParticipantStatus(PARTICIPANT_ID, ApiMigrationParticipantState.CURRENT)
            }
        }

        fun executeMigration(): ApiMigrationParticipantResult {
            val before = status()
            if (before.state == ApiMigrationParticipantState.CURRENT) {
                return ApiMigrationParticipantResult(
                    ApiMigrationParticipantResultState.ALREADY_CURRENT,
                    "already current: $PARTICIPANT_ID",
                )
            }
            val scan = scanDisk()
            scan.fileFailure?.let {
                return ApiMigrationParticipantResult(ApiMigrationParticipantResultState.FAILED, it)
            }
            if (scan.configVersion > CURRENT_SCHEMA_VERSION) {
                return ApiMigrationParticipantResult(
                    ApiMigrationParticipantResultState.FAILED,
                    "portals.yml is newer than this plugin: ${scan.configVersion}",
                )
            }
            if (scan.configVersion == CURRENT_SCHEMA_VERSION) {
                return ApiMigrationParticipantResult(
                    ApiMigrationParticipantResultState.FAILED,
                    before.message ?: "portals.yml contains quarantined records",
                )
            }

            val legacyConfig = scan.config
                ?: return ApiMigrationParticipantResult(
                    ApiMigrationParticipantResultState.FAILED,
                    "portals.yml migration source is unavailable",
                )
            return runCatching {
                migrateLegacyFile(legacyConfig)
                loadAll()
                val after = status()
                if (after.state == ApiMigrationParticipantState.CURRENT) {
                    ApiMigrationParticipantResult(
                        ApiMigrationParticipantResultState.MIGRATED,
                        "migrated: $PARTICIPANT_ID",
                    )
                } else {
                    ApiMigrationParticipantResult(
                        ApiMigrationParticipantResultState.FAILED,
                        after.message ?: "portals.yml migration remains incomplete",
                    )
                }
            }.getOrElse { error ->
                ApiMigrationParticipantResult(
                    ApiMigrationParticipantResultState.FAILED,
                    "portals.yml migration failed: ${error.message ?: error.javaClass.simpleName}",
                )
            }
        }
    }

    val migrationParticipant: ApiMigrationParticipant = migrationExecutor

    init {
        MyWorldManagerApi.registerMigrationParticipant(migrationParticipant, migrationExecutor::executeMigration)
        loadAll()
    }

    @Synchronized
    fun loadAll() {
        portals.clear()
        quarantined.clear()
        validIdsLoadedFromDisk.clear()
        fileFailure = null
        loadedConfigVersion = CURRENT_SCHEMA_VERSION
        if (!file.exists()) return

        val config = runCatching { loadStrict(file) }.getOrElse { error ->
            fileFailure = "portals.yml is unreadable: ${error.message ?: error.javaClass.simpleName}"
            plugin.logger.log(Level.WARNING, "Portals were quarantined because portals.yml is unreadable", error)
            return
        }
        loadedConfigVersion = runCatching { readConfigVersion(config) }.getOrElse { error ->
            fileFailure = error.message ?: error.javaClass.simpleName
            return
        }
        if (loadedConfigVersion > CURRENT_SCHEMA_VERSION) {
            fileFailure = "portals.yml is newer than this plugin: $loadedConfigVersion"
            return
        }
        val section = config.getConfigurationSection("portals")
        if (config.contains("portals") && section == null) {
            fileFailure = "portals must be a section"
            return
        }
        section?.getKeys(false).orEmpty().forEach { rawId ->
            val record = runCatching {
                val id = UUID.fromString(rawId)
                val child = section?.getConfigurationSection(rawId)
                    ?: error("portal record is not a section: $rawId")
                id to parsePortal(id, child)
            }.getOrElse { error ->
                quarantined[rawId] = error.message ?: error.javaClass.simpleName
                return@forEach
            }
            validIdsLoadedFromDisk += rawId
            portals[record.first] = record.second
        }
    }

    fun findAll(): Collection<PortalData> = portals.values

    fun findById(id: UUID): PortalData? = portals[id]

    @Synchronized
    fun saveAll() {
        ensureWritable()
        val config = if (file.exists()) loadStrict(file) else YamlConfiguration()
        val section = config.getConfigurationSection("portals") ?: config.createSection("portals")
        validIdsLoadedFromDisk
            .filter { it !in portals.keys.map(UUID::toString) }
            .forEach { section.set(it, null) }
        portals.forEach { (id, data) ->
            if (quarantined.containsKey(id.toString())) {
                error("Cannot overwrite quarantined portal record: $id")
            }
            writePortal(section, id.toString(), data)
        }
        config.set("config_version", CURRENT_SCHEMA_VERSION)
        atomicSave(config)
        loadAll()
    }

    @Synchronized
    fun addPortal(portal: PortalData) {
        ensureWritable()
        check(!quarantined.containsKey(portal.id.toString())) {
            "Cannot overwrite quarantined portal record: ${portal.id}"
        }
        val previous = portals.put(portal.id, portal)
        try {
            saveAll()
        } catch (error: Exception) {
            if (previous == null) portals.remove(portal.id) else portals[portal.id] = previous
            throw error
        }
    }

    @Synchronized
    fun removePortal(id: UUID) {
        ensureWritable()
        val previous = portals.remove(id) ?: return
        try {
            saveAll()
        } catch (error: Exception) {
            portals[id] = previous
            throw error
        }
    }

    /**
     * ポータルに関する付随処理を始める前に、移行による書き込み拒否を確定させます。
     * removePortal() 内の検査だけに任せると、呼び出し側が先に返金やブロック変更を行い、
     * 移行保留時に部分更新を残すためです。
     */
    @Synchronized
    fun ensureWritableForOperation() {
        ensureWritable()
    }

    fun findByLocation(location: org.bukkit.Location): PortalData? = portals.values.find {
        it.worldKey == location.world?.key?.toString() &&
            it.x == location.blockX && it.y == location.blockY && it.z == location.blockZ
    }

    fun findByContainingLocation(location: org.bukkit.Location): PortalData? {
        val worldKey = location.world?.key?.toString() ?: return null
        findByLocation(location)?.let { return it }
        return portals.values.find {
            it.worldKey == worldKey && it.containsBlock(location.blockX, location.blockY, location.blockZ)
        }
    }

    private fun migrateLegacyFile(config: YamlConfiguration) {
        val backup = File(file.parentFile, "${file.name}.pre-migration-${System.currentTimeMillis()}.bak")
        val temporary = File(file.parentFile, "${file.name}.migration.tmp")
        try {
            Files.copy(file.toPath(), backup.toPath())
            config.getConfigurationSection("portals")?.getKeys(false).orEmpty().forEach { rawId ->
                runCatching {
                    val id = UUID.fromString(rawId)
                    val section = config.getConfigurationSection("portals.$rawId")
                        ?: error("portal record is not a section: $rawId")
                    writePortal(config.getConfigurationSection("portals")!!, rawId, parsePortal(id, section))
                }
                // Invalid records are intentionally retained in the backup/current file for diagnosis.
            }
            config.set("config_version", CURRENT_SCHEMA_VERSION)
            config.save(temporary)
            FileChannel.open(temporary.toPath(), StandardOpenOption.WRITE).use { it.force(true) }
            loadStrict(temporary)
            moveAtomically(temporary, file)
        } catch (error: Exception) {
            Files.deleteIfExists(temporary.toPath())
            runCatching { Files.copy(backup.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            throw error
        }
    }

    private fun scanDisk(): PortalScan {
        if (!file.exists()) return PortalScan(CURRENT_SCHEMA_VERSION, 0, null, null)
        val config = runCatching { loadStrict(file) }.getOrElse { error ->
            return PortalScan(0, 0, "portals.yml is unreadable: ${error.message ?: error.javaClass.simpleName}", null)
        }
        val version = runCatching { readConfigVersion(config) }.getOrElse { error ->
            return PortalScan(0, 0, error.message ?: error.javaClass.simpleName, config)
        }
        if (version > CURRENT_SCHEMA_VERSION) {
            return PortalScan(version, 0, "portals.yml is newer than this plugin: $version", config)
        }
        val section = config.getConfigurationSection("portals")
        if (config.contains("portals") && section == null) {
            return PortalScan(version, 0, "portals must be a section", config)
        }
        var invalid = 0
        val portalSection = section ?: return PortalScan(version, 0, null, config)
        portalSection.getKeys(false).forEach { rawId ->
            runCatching {
                val id = UUID.fromString(rawId)
                val child = portalSection.getConfigurationSection(rawId)
                    ?: error("portal record is not a section: $rawId")
                parsePortal(id, child)
            }.onFailure { invalid++ }
        }
        return PortalScan(version, invalid, null, config)
    }

    private fun parsePortal(id: UUID, section: ConfigurationSection): PortalData {
        val worldUuid = section.getString("world_uuid")?.takeUnless { it == "null" }?.let { parseUuid(it, "world_uuid") }
        val worldKey = section.getString("location.world_key")
            ?: error("location.world_key is missing")
        require(NamespacedKey.fromString(worldKey) != null) { "location.world_key is invalid: $worldKey" }
        val ownerUuid = parseUuid(section.getString("owner_uuid"), "owner_uuid")
        val type = PortalType.fromKey(section.getString("type", "portal"))
        val area = section.getConfigurationSection("area")
        val createdAt = parseCreatedAt(section.get("created_at"))
        val textDisplayUuid = section.getString("text_display_uuid")
            ?.takeUnless { it == "null" }
            ?.let { parseUuid(it, "text_display_uuid") }
        return PortalData(
            id = id,
            worldKey = worldKey,
            x = readInt(section, "location.x", 0),
            y = readInt(section, "location.y", 0),
            z = readInt(section, "location.z", 0),
            worldUuid = worldUuid,
            targetWorldKey = section.getString("target_world_key"),
            showText = readBoolean(section, "show_text", true),
            particleColor = Color.fromRGB(readInt(section, "color", Color.AQUA.asRGB())),
            ownerUuid = ownerUuid,
            createdAt = createdAt,
            textDisplayUuid = textDisplayUuid,
            type = type,
            minX = area?.let { readOptionalInt(it, "min_x") },
            minY = area?.let { readOptionalInt(it, "min_y") },
            minZ = area?.let { readOptionalInt(it, "min_z") },
            maxX = area?.let { readOptionalInt(it, "max_x") },
            maxY = area?.let { readOptionalInt(it, "max_y") },
            maxZ = area?.let { readOptionalInt(it, "max_z") },
        )
    }

    private fun writePortal(parent: ConfigurationSection, key: String, data: PortalData) {
        val section = parent.getConfigurationSection(key) ?: parent.createSection(key)
        section.set("type", data.type.key)
        section.set(
            "location",
            mapOf("world_key" to data.worldKey, "x" to data.x, "y" to data.y, "z" to data.z),
        )
        section.set(
            "area",
            if (data.isGate()) {
                mapOf(
                    "min_x" to data.getMinX(), "min_y" to data.getMinY(), "min_z" to data.getMinZ(),
                    "max_x" to data.getMaxX(), "max_y" to data.getMaxY(), "max_z" to data.getMaxZ(),
                )
            } else null,
        )
        section.set("world_uuid", data.worldUuid?.toString())
        section.set("target_world_key", data.targetWorldKey)
        section.set("show_text", data.showText)
        section.set("color", data.particleColor.asRGB())
        section.set("owner_uuid", data.ownerUuid.toString())
        section.set("created_at", data.createdAt)
        section.set("text_display_uuid", data.textDisplayUuid?.toString())
    }

    private fun ensureWritable() {
        val status = migrationParticipant.status()
        check(status.state == ApiMigrationParticipantState.CURRENT ||
            status.state == ApiMigrationParticipantState.FAILED && quarantined.isNotEmpty()) {
            status.message ?: "portals.yml requires /mwm migration"
        }
        check(fileFailure == null) { fileFailure ?: "portals.yml is quarantined" }
        check(loadedConfigVersion == CURRENT_SCHEMA_VERSION || !file.exists()) {
            "portals.yml requires /mwm migration"
        }
    }

    private fun atomicSave(config: YamlConfiguration) {
        val temporary = File(file.parentFile, "${file.name}.save.tmp")
        try {
            file.parentFile?.mkdirs()
            config.save(temporary)
            FileChannel.open(temporary.toPath(), StandardOpenOption.WRITE).use { it.force(true) }
            val verified = loadStrict(temporary)
            check(readConfigVersion(verified) == CURRENT_SCHEMA_VERSION) { "portals.yml save verification failed" }
            moveAtomically(temporary, file)
        } finally {
            Files.deleteIfExists(temporary.toPath())
        }
    }

    private fun readConfigVersion(config: YamlConfiguration): Int {
        val raw = config.get("config_version") ?: return 0
        require(raw is Number && raw.toDouble() % 1.0 == 0.0) { "config_version must be an integer" }
        return raw.toInt().also { require(it >= 0) { "config_version must not be negative" } }
    }

    private fun parseCreatedAt(raw: Any?): String = when (raw) {
        is String -> if (raw.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) "$raw 00:00:00" else raw
        is Number -> LocalDateTime.ofInstant(Instant.ofEpochMilli(raw.toLong()), ZoneId.systemDefault()).format(DATE_FORMAT)
        null -> LocalDateTime.now().format(DATE_FORMAT)
        else -> error("created_at must be a string or number")
    }

    private fun parseUuid(raw: String?, field: String): UUID {
        require(!raw.isNullOrBlank()) { "$field is missing" }
        return runCatching { UUID.fromString(raw) }.getOrElse { error("$field is invalid") }
    }

    private fun readInt(section: ConfigurationSection, path: String, default: Int): Int {
        val raw = section.get(path) ?: return default
        require(raw is Number && raw.toDouble() % 1.0 == 0.0) { "$path must be an integer" }
        return raw.toInt()
    }

    private fun readOptionalInt(section: ConfigurationSection, path: String): Int? {
        if (!section.contains(path)) return null
        return readInt(section, path, 0)
    }

    private fun readBoolean(section: ConfigurationSection, path: String, default: Boolean): Boolean {
        val raw = section.get(path) ?: return default
        require(raw is Boolean) { "$path must be boolean" }
        return raw
    }

    private fun loadStrict(target: File): YamlConfiguration = YamlConfiguration().also { it.load(target) }

    private fun moveAtomically(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private data class PortalScan(
        val configVersion: Int,
        val quarantined: Int,
        val fileFailure: String?,
        val config: YamlConfiguration?,
    )

    private companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val PARTICIPANT_ID = "myworld-portals"
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
