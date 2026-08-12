package me.awabi2048.myworldmanager.repository

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.service.ApiMigrationParticipant
import me.awabi2048.myworldmanager.api.service.ApiMigrationParticipantResult
import me.awabi2048.myworldmanager.api.service.ApiMigrationParticipantResultState
import me.awabi2048.myworldmanager.api.service.ApiMigrationParticipantState
import me.awabi2048.myworldmanager.api.service.ApiMigrationParticipantStatus
import me.awabi2048.myworldmanager.api.service.ApiMigrationTargetKind
import me.awabi2048.myworldmanager.model.PlayerStats
import me.awabi2048.myworldmanager.model.TourNavigationMode
import me.awabi2048.myworldmanager.util.FavoriteRegistrationTimestamp
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/**
 * プレイヤー単位の永続データを読み書きします。
 *
 * 旧形式の読み取り互換はメモリ上に限定し、ファイルへの変換は /mwm migration の参加者だけが行います。
 * これにより、プレイヤーがログインしただけで旧データが上書きされることを防ぎます。
 */
class PlayerStatsRepository(private val plugin: MyWorldManager) {
    private val statsFolder = File(plugin.dataFolder, "playerdata")
    private val cache = ConcurrentHashMap<UUID, PlayerStats>()
    private val pendingFiles = mutableSetOf<String>()
    private val quarantinedFiles = mutableMapOf<String, String>()

    private val migrationExecutor = object : ApiMigrationParticipant {
        override fun getId(): String = PARTICIPANT_ID

        override fun targetKind(): ApiMigrationTargetKind = ApiMigrationTargetKind.PLAYER

        override fun statusFor(targetUuid: UUID): ApiMigrationParticipantStatus {
            val file = fileFor(targetUuid)
            if (!file.exists()) return ApiMigrationParticipantStatus(
                PARTICIPANT_ID,
                ApiMigrationParticipantState.CURRENT,
            )
            val inspection = runCatching { inspectFile(file) }.getOrElse { error ->
                return ApiMigrationParticipantStatus(
                    PARTICIPANT_ID,
                    ApiMigrationParticipantState.FAILED,
                    "${file.name}: ${error.message ?: error.javaClass.simpleName}",
                )
            }
            return when (inspection.status) {
                PlayerFileState.CURRENT -> ApiMigrationParticipantStatus(PARTICIPANT_ID, ApiMigrationParticipantState.CURRENT)
                PlayerFileState.PENDING -> ApiMigrationParticipantStatus(
                    PARTICIPANT_ID,
                    ApiMigrationParticipantState.PENDING,
                    file.name,
                )
                PlayerFileState.QUARANTINED -> ApiMigrationParticipantStatus(
                    PARTICIPANT_ID,
                    ApiMigrationParticipantState.FAILED,
                    "${file.name}: ${inspection.reason ?: "invalid player stats"}",
                )
            }
        }

        override fun status(): ApiMigrationParticipantStatus {
            val scan = scanFiles()
            return when {
                scan.quarantined > 0 -> ApiMigrationParticipantStatus(
                    PARTICIPANT_ID,
                    ApiMigrationParticipantState.FAILED,
                    "playerdata contains invalid files: ${scan.quarantined}",
                )
                scan.pending > 0 -> ApiMigrationParticipantStatus(
                    PARTICIPANT_ID,
                    ApiMigrationParticipantState.PENDING,
                    "playerdata requires migration: ${scan.pending}",
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
            if (before.state == ApiMigrationParticipantState.FAILED) {
                return ApiMigrationParticipantResult(
                    ApiMigrationParticipantResultState.FAILED,
                    before.message ?: "playerdata contains invalid files",
                )
            }

            var migrated = 0
            scanFiles().files
                .filter { it.status == PlayerFileState.PENDING }
                .forEach { file ->
                    runCatching {
                        val parsed = parseFile(file.file)
                        migrateFile(file.file, parsed.stats)
                        migrated++
                    }.onFailure { error ->
                        quarantinedFiles[file.file.name] =
                            "migration failed: ${error.message ?: error.javaClass.simpleName}"
                        plugin.logger.log(
                            Level.WARNING,
                            "Could not migrate player stats: ${file.file.name}",
                            error,
                        )
                    }
                }

            val after = status()
            if (after.state == ApiMigrationParticipantState.CURRENT) {
                // ログイン時の安全側フォールバックがキャッシュされていても、
                // 明示移行完了後は修復済みファイルの値を即時反映します。
                updateAllData()
            }
            return if (after.state == ApiMigrationParticipantState.CURRENT) {
                ApiMigrationParticipantResult(
                    if (migrated > 0) ApiMigrationParticipantResultState.MIGRATED
                    else ApiMigrationParticipantResultState.ALREADY_CURRENT,
                    "migrated: $PARTICIPANT_ID ($migrated files)",
                )
            } else {
                ApiMigrationParticipantResult(
                    ApiMigrationParticipantResultState.FAILED,
                    after.message ?: "playerdata migration remains incomplete",
                )
            }
        }
    }

    val migrationParticipant: ApiMigrationParticipant = migrationExecutor

    init {
        statsFolder.mkdirs()
        MyWorldManagerApi.registerMigrationParticipant(migrationParticipant, migrationExecutor::executeMigration)
    }

    fun findByUuid(uuid: UUID): PlayerStats {
        return cache.getOrPut(uuid) {
            val file = fileFor(uuid)
            if (!file.exists()) {
                createDefault(uuid, persist = true)
            } else {
                // 破損・隔離ファイルは既定値をメモリ上で提供するが、save() は必ず拒否する。
                // 破損・隔離ファイルの値を権限・通知・課金判定へ流用しないよう、
                // 読み取り継続時も安全側の一時値だけを返します。保存は ensureWritable で拒否します。
                loadFromFile(uuid) ?: createDefault(uuid, persist = false, safeFallback = true)
            }
        }
    }

    private fun createDefault(uuid: UUID, persist: Boolean, safeFallback: Boolean = false): PlayerStats {
        val config = plugin.config
        val stats = PlayerStats(
            playerUuid = uuid,
            worldPoint = if (safeFallback) 0 else config.getInt("default_player_stats.world_point", 0),
            unlockedWorldSlot = if (safeFallback) 0 else config.getInt("default_player_stats.unlocked_world_slot", 0),
            visitorNotificationEnabled = !safeFallback,
            criticalSettingsEnabled = !safeFallback,
            favoriteGroupInvitesEnabled = !safeFallback,
            meetStatus = if (safeFallback) "BUSY" else "JOIN_ME",
        )
        if (persist) save(stats)
        return stats
    }

    @Synchronized
    fun save(stats: PlayerStats) {
        ensureWritable(stats.playerUuid)
        try {
            saveToFile(stats)
            cache[stats.playerUuid] = stats
        } catch (error: Exception) {
            loadFromFile(stats.playerUuid)?.let { cache[stats.playerUuid] = it }
                ?: cache.remove(stats.playerUuid)
            throw error
        }
    }

    /**
     * 他のドメイン操作が返金などの付随更新を始める前に、対象プレイヤーの
     * 移行状態による書き込み拒否を確定させます。
     */
    @Synchronized
    fun ensureWritableForOperation(playerUuid: UUID) {
        ensureWritable(playerUuid)
    }

    @Synchronized
    fun adjustWorldPoints(uuid: UUID, delta: Int): PlayerStats {
        val stats = findByUuid(uuid)
        val previous = stats.worldPoint
        stats.worldPoint = Math.addExact(previous, delta)
        return try {
            save(stats)
            stats
        } catch (error: Exception) {
            stats.worldPoint = previous
            throw error
        }
    }

    private fun loadFromFile(uuid: UUID): PlayerStats? {
        val file = fileFor(uuid)
        return runCatching {
            val parsed = parseFile(file)
            if (parsed.schemaVersion != CURRENT_SCHEMA_VERSION || parsed.requiresMigration) {
                pendingFiles += file.name
                null
            } else {
                parsed.stats
            }
        }.getOrElse { error ->
            quarantinedFiles[file.name] = error.message ?: error.javaClass.simpleName
            plugin.logger.log(Level.WARNING, "Could not load player stats; file is quarantined: $file", error)
            null
        }
    }

    private fun parseFile(file: File): ParsedPlayerFile {
        require(file.isFile) { "player stats file is missing: ${file.name}" }
        val uuid = runCatching { UUID.fromString(file.nameWithoutExtension) }
            .getOrElse { error("player stats filename is not a UUID: ${file.name}") }
        return parseFile(file, uuid)
    }

    private fun parseFile(file: File, expectedUuid: UUID): ParsedPlayerFile {
        val uuid = expectedUuid
        val config = YamlConfiguration().also { it.load(file) }
        val schemaVersion = readSchemaVersion(config)
        require(schemaVersion <= CURRENT_SCHEMA_VERSION) {
            "player stats schema is newer than this plugin: ${file.name}=$schemaVersion"
        }

        val registeredWarp = readUuidList(config, "registered_warp")
        val favoriteWorlds = readUuidStringMap(config, "favorite_worlds", normalizeDate = true)
        val visitedWorlds = readUuidStringMap(config, "visited_worlds", normalizeDate = false)
        val worldDisplayOrder = readUuidList(config, "world_display_order")
        val tourSlotsByWorld = readUuidIntMap(config, "tour_slots_by_world")

        val meetStatus = if (config.contains("meet_status")) {
            require(config.get("meet_status") is String) { "meet_status must be a string" }
            config.getString("meet_status").orEmpty().also { require(it in MEET_STATUSES) }
        } else if (config.contains("meet_enabled")) {
            require(config.get("meet_enabled") is Boolean) { "meet_enabled must be boolean" }
            if (config.getBoolean("meet_enabled")) "JOIN_ME" else "BUSY"
        } else {
            "JOIN_ME"
        }

        val tourNavigationMode = if (config.contains("tour_navigation_mode")) {
            require(config.get("tour_navigation_mode") is String) { "tour_navigation_mode must be a string" }
            runCatching { TourNavigationMode.valueOf(config.getString("tour_navigation_mode").orEmpty()) }
                .getOrElse { error("tour_navigation_mode is invalid") }
        } else {
            TourNavigationMode.ALL
        }

        val stats = PlayerStats(
            playerUuid = uuid,
            worldPoint = readInt(config, "world_point", 0),
            unlockedWorldSlot = readInt(config, "unlocked_world_slot", 0),
            registeredWarp = registeredWarp.toMutableList(),
            favoriteWorlds = favoriteWorlds.values.toMutableMap(),
            visitedWorlds = visitedWorlds.values.toMutableMap(),
            lastOnline = readOptionalString(config, "last_online"),
            lastName = readOptionalString(config, "last_name"),
            language = readOptionalString(config, "language") ?: "ja_jp",
            visitorNotificationEnabled = readBoolean(config, "visitor_notification_enabled", true),
            criticalSettingsEnabled = readBoolean(config, "critical_settings_enabled", true),
            favoriteGroupInvitesEnabled = readBoolean(config, "favorite_group_invites_enabled", true),
            meetStatus = meetStatus,
            worldDisplayOrder = worldDisplayOrder.toMutableList(),
            lastArchiveActionAt = readOptionalString(config, "last_archive_action_at"),
            tourSlotsByWorld = tourSlotsByWorld.toMutableMap(),
            tourNavigationMode = tourNavigationMode,
        )
        val requiresMigration = schemaVersion < CURRENT_SCHEMA_VERSION ||
            config.contains("meet_enabled") ||
            config.contains("unlocked_warp_slot") ||
            config.contains("beta_features_enabled") ||
            favoriteWorlds.requiresMigration ||
            visitedWorlds.requiresMigration
        return ParsedPlayerFile(schemaVersion, stats, requiresMigration)
    }

    private fun readSchemaVersion(config: YamlConfiguration): Int {
        val raw = config.get("schema_version") ?: return 0
        require(raw is Number && raw.toDouble() % 1.0 == 0.0) { "schema_version must be an integer" }
        return raw.toInt().also { require(it >= 0) { "schema_version must not be negative" } }
    }

    private fun readUuidList(config: YamlConfiguration, path: String): List<UUID> {
        val raw = config.get(path) ?: return emptyList()
        require(raw is List<*>) { "$path must be a list" }
        return raw.map { value ->
            require(value is String) { "$path contains a non-string UUID" }
            runCatching { UUID.fromString(value) }.getOrElse { error("$path contains an invalid UUID") }
        }
    }

    private fun readUuidStringMap(
        config: YamlConfiguration,
        path: String,
        normalizeDate: Boolean,
    ): ParsedStringMap {
        if (!config.contains(path)) return ParsedStringMap(emptyMap(), false)
        val section = config.getConfigurationSection(path) ?: error("$path must be a section")
        var requiresMigration = false
        val values = section.getKeys(false).associate { key ->
            val uuid = runCatching { UUID.fromString(key) }.getOrElse { error("$path contains an invalid UUID") }
            val stored = section.get(key)
            require(stored is String && stored.isNotBlank()) { "$path.$key must be a non-empty string" }
            val value = if (normalizeDate) {
                val normalized = FavoriteRegistrationTimestamp.normalize(stored)
                requiresMigration = requiresMigration || normalized != stored
                require(FavoriteRegistrationTimestamp.sortValue(normalized) != LocalDateTime.MAX) {
                    "$path.$key contains an invalid timestamp"
                }
                normalized
            } else {
                stored
            }
            uuid to value
        }
        return ParsedStringMap(values, requiresMigration)
    }

    private fun readUuidIntMap(config: YamlConfiguration, path: String): Map<UUID, Int> {
        if (!config.contains(path)) return emptyMap()
        val section = config.getConfigurationSection(path) ?: error("$path must be a section")
        return section.getKeys(false).associate { key ->
            val uuid = runCatching { UUID.fromString(key) }.getOrElse { error("$path contains an invalid UUID") }
            val raw = section.get(key)
            require(raw is Number && raw.toDouble() % 1.0 == 0.0 && raw.toInt() >= 0) {
                "$path.$key must be a non-negative integer"
            }
            uuid to raw.toInt()
        }
    }

    private fun readInt(config: YamlConfiguration, path: String, default: Int): Int {
        val raw = config.get(path) ?: return default
        require(raw is Number && raw.toDouble() % 1.0 == 0.0) { "$path must be an integer" }
        return raw.toInt()
    }

    private fun readBoolean(config: YamlConfiguration, path: String, default: Boolean): Boolean {
        val raw = config.get(path) ?: return default
        require(raw is Boolean) { "$path must be boolean" }
        return raw
    }

    private fun readOptionalString(config: YamlConfiguration, path: String): String? {
        if (!config.contains(path)) return null
        val raw = config.get(path) ?: return null
        require(raw is String) { "$path must be a string" }
        return raw
    }

    private fun ensureWritable(uuid: UUID) {
        val file = fileFor(uuid)
        if (!file.exists()) return
        val inspection = runCatching { inspectFile(file) }.getOrElse { error ->
            quarantinedFiles[file.name] = error.message ?: error.javaClass.simpleName
            throw IllegalStateException("player stats file is quarantined: ${file.name}", error)
        }
        when (inspection.status) {
            PlayerFileState.CURRENT -> Unit
            PlayerFileState.PENDING -> {
                pendingFiles += file.name
                throw IllegalStateException("player stats requires /mwm migration: ${file.name}")
            }
            PlayerFileState.QUARANTINED -> {
                quarantinedFiles[file.name] = inspection.reason ?: "invalid player stats"
                throw IllegalStateException("player stats file is quarantined: ${file.name}")
            }
        }
    }

    private fun scanFiles(): PlayerFileScan {
        synchronized(this) {
            pendingFiles.clear()
            quarantinedFiles.clear()
            val files = statsFolder.listFiles { file -> file.extension == "yml" }.orEmpty().toList()
            val inspections = files.map { file ->
                val inspection = runCatching { inspectFile(file) }.getOrElse { error ->
                    FileInspection(file, PlayerFileState.QUARANTINED, error.message ?: error.javaClass.simpleName)
                }
                when (inspection.status) {
                    PlayerFileState.PENDING -> pendingFiles += file.name
                    PlayerFileState.QUARANTINED -> quarantinedFiles[file.name] = inspection.reason.orEmpty()
                    PlayerFileState.CURRENT -> Unit
                }
                inspection
            }
            return PlayerFileScan(
                files = inspections,
                pending = inspections.count { it.status == PlayerFileState.PENDING },
                quarantined = inspections.count { it.status == PlayerFileState.QUARANTINED },
            )
        }
    }

    private fun inspectFile(file: File): FileInspection {
        if (runCatching { UUID.fromString(file.nameWithoutExtension) }.isFailure) {
            return FileInspection(file, PlayerFileState.QUARANTINED, "filename is not a UUID")
        }
        val parsed = parseFile(file)
        return FileInspection(
            file = file,
            status = if (parsed.schemaVersion == CURRENT_SCHEMA_VERSION && !parsed.requiresMigration) {
                PlayerFileState.CURRENT
            } else {
                PlayerFileState.PENDING
            },
            reason = null,
        )
    }

    private fun migrateFile(file: File, stats: PlayerStats) {
        val backup = File(file.parentFile, "${file.name}.pre-migration-${System.currentTimeMillis()}.bak")
        val temporary = File(file.parentFile, "${file.name}.migration.tmp")
        try {
            Files.copy(file.toPath(), backup.toPath())
            writeConfig(temporary, stats)
            moveAtomically(temporary, file)
            check(inspectFile(file).status == PlayerFileState.CURRENT) {
                "migrated player stats remain non-current: ${file.name}"
            }
        } catch (error: Exception) {
            Files.deleteIfExists(temporary.toPath())
            runCatching { Files.copy(backup.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            throw error
        }
    }

    private fun saveToFile(stats: PlayerStats) {
        writeConfig(fileFor(stats.playerUuid), stats)
    }

    private fun writeConfig(target: File, stats: PlayerStats) {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        val config = YamlConfiguration()
        config.set("schema_version", CURRENT_SCHEMA_VERSION)
        config.set("world_point", stats.worldPoint)
        config.set("unlocked_world_slot", stats.unlockedWorldSlot)
        config.set("registered_warp", stats.registeredWarp.map { it.toString() })
        val favoriteSection = config.createSection("favorite_worlds")
        stats.favoriteWorlds.forEach { (uuid, date) -> favoriteSection.set(uuid.toString(), date) }
        val visitedSection = config.createSection("visited_worlds")
        stats.visitedWorlds.forEach { (uuid, date) -> visitedSection.set(uuid.toString(), date) }
        config.set("world_display_order", stats.worldDisplayOrder.map { it.toString() })
        config.set("last_online", stats.lastOnline)
        config.set("last_name", stats.lastName)
        config.set("language", stats.language)
        config.set("visitor_notification_enabled", stats.visitorNotificationEnabled)
        config.set("critical_settings_enabled", stats.criticalSettingsEnabled)
        config.set("favorite_group_invites_enabled", stats.favoriteGroupInvitesEnabled)
        config.set("meet_status", stats.meetStatus)
        config.set("last_archive_action_at", stats.lastArchiveActionAt)
        config.set("tour_navigation_mode", stats.tourNavigationMode.name)
        val tourSlotsSection = config.createSection("tour_slots_by_world")
        stats.tourSlotsByWorld.forEach { (uuid, count) -> tourSlotsSection.set(uuid.toString(), count) }

        try {
            target.parentFile?.mkdirs()
            config.save(temporary)
            FileChannel.open(temporary.toPath(), StandardOpenOption.WRITE).use { it.force(true) }
            val verified = parseFile(temporary, stats.playerUuid)
            check(verified.schemaVersion == CURRENT_SCHEMA_VERSION && verified.stats.playerUuid == stats.playerUuid) {
                "temporary player stats verification failed: ${target.name}"
            }
            moveAtomically(temporary, target)
        } catch (error: Exception) {
            Files.deleteIfExists(temporary.toPath())
            throw IllegalStateException("Could not save player stats for ${stats.playerUuid}", error)
        }
    }

    private fun moveAtomically(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun uncache(uuid: UUID) {
        cache.remove(uuid)
    }

    fun clearCache() {
        cache.clear()
    }

    /**
     * 管理画面の既存操作と互換性を保つための現行データ検査です。
     *
     * 旧形式を現行形式へ書き換える処理はここでは行いません。
     * 変換の唯一の実行元は /mwm migration とし、旧形式・破損形式は
     * そのまま保留または隔離して管理者へ返します。
     */
    @Synchronized
    fun updateAllData(): Int {
        val scan = scanFiles()
        var currentCount = 0
        scan.files
            .filter { it.status == PlayerFileState.CURRENT }
            .forEach { inspection ->
                runCatching { parseFile(inspection.file) }
                    .onSuccess { parsed ->
                        cache[parsed.stats.playerUuid] = parsed.stats
                        currentCount++
                    }
                    .onFailure { error ->
                        plugin.logger.warning(
                            "Could not refresh current player data for ${inspection.file.name}: ${error.message}"
                        )
                    }
            }
        return currentCount
    }

    /** プレイヤー名検索など、読み取り専用の管理処理が利用するファイル一覧です。 */
    fun findAllFiles(): Array<File> =
        statsFolder.listFiles { file -> file.extension == "yml" } ?: emptyArray()

    private fun fileFor(uuid: UUID): File = File(statsFolder, "$uuid.yml")

    private data class ParsedPlayerFile(
        val schemaVersion: Int,
        val stats: PlayerStats,
        val requiresMigration: Boolean,
    )

    private data class ParsedStringMap(
        val values: Map<UUID, String>,
        val requiresMigration: Boolean,
    )

    private enum class PlayerFileState { CURRENT, PENDING, QUARANTINED }

    private data class FileInspection(
        val file: File,
        val status: PlayerFileState,
        val reason: String?,
    )

    private data class PlayerFileScan(
        val files: List<FileInspection>,
        val pending: Int,
        val quarantined: Int,
    )

    private companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val PARTICIPANT_ID = "myworld-playerdata"
        val MEET_STATUSES = setOf("JOIN_ME", "ASK_ME", "BUSY")
    }
}
