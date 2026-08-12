package me.awabi2048.myworldmanager.repository

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PendingInteraction
import me.awabi2048.myworldmanager.model.PendingInteractionType
import me.awabi2048.myworldmanager.service.PendingActionCodeAllocator
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
import java.util.concurrent.ConcurrentHashMap

class PendingInteractionRepository(private val plugin: MyWorldManager) {

    private val file = File(plugin.dataFolder, "pending_interactions.yml")
    private val cache = ConcurrentHashMap<UUID, PendingInteraction>()
    private val quarantinedEntryIds = mutableSetOf<String>()
    private var quarantinedFileReason: String? = null
    private var migrationRequired = false

    private val migrationExecutor = object : ApiMigrationParticipant {
        override fun getId(): String = "myworld-pending-interactions"

        override fun status(): ApiMigrationParticipantStatus {
            if (!file.exists()) return ApiMigrationParticipantStatus(getId(), ApiMigrationParticipantState.CURRENT)
            return runCatching {
                val config = loadStrict()
                var needsMigration = false
                var invalid = false
                val usedCodes = mutableMapOf<UUID, MutableSet<String>>()
                val section = config.getConfigurationSection("entries")
                if (config.contains("entries") && section == null) {
                    invalid = true
                }
                section?.getKeys(false)?.forEach { id ->
                    val entryNeedsMigration = runCatching {
                        val path = "entries.$id"
                        UUID.fromString(id)
                        PendingInteractionType.valueOf(
                            config.getString("$path.type") ?: error("type is missing"),
                        )
                        val targetUuid = UUID.fromString(
                            config.getString("$path.target_uuid") ?: error("target_uuid is missing"),
                        )
                        UUID.fromString(config.getString("$path.world_uuid") ?: error("world_uuid is missing"))
                        UUID.fromString(config.getString("$path.actor_uuid") ?: error("actor_uuid is missing"))
                        val createdAt = config.get("$path.created_at")
                        require(createdAt is Number) { "created_at must be numeric" }
                        if (config.contains("$path.$TARGET_ONLINE_AT_CREATION_KEY")) {
                            require(config.get("$path.$TARGET_ONLINE_AT_CREATION_KEY") is Boolean) {
                                "$TARGET_ONLINE_AT_CREATION_KEY must be boolean"
                            }
                        }
                        if (config.contains("$path.action_code")) {
                            require(config.get("$path.action_code") is String) {
                                "action_code must be a string"
                            }
                        }
                        val storedCode = config.get("$path.action_code") as? String
                        val invalidCode = !PendingActionCodeAllocator.CODE_PATTERN.matches(storedCode.orEmpty())
                        val duplicateCode = !usedCodes
                            .getOrPut(targetUuid) { mutableSetOf() }
                            .add(storedCode.orEmpty())
                        invalidCode || duplicateCode ||
                            !config.contains("$path.action_code") ||
                            !config.contains("$path.$TARGET_ONLINE_AT_CREATION_KEY")
                    }.getOrElse {
                        invalid = true
                        false
                    }
                    needsMigration = needsMigration || entryNeedsMigration
                }
                ApiMigrationParticipantStatus(
                    getId(),
                    when {
                        invalid -> ApiMigrationParticipantState.FAILED
                        needsMigration -> ApiMigrationParticipantState.PENDING
                        else -> ApiMigrationParticipantState.CURRENT
                    },
                    when {
                        invalid -> "pending interactions contain invalid records"
                        needsMigration -> "pending interactions require migration"
                        else -> null
                    },
                )
            }.getOrElse { error ->
                ApiMigrationParticipantStatus(
                    getId(),
                    ApiMigrationParticipantState.FAILED,
                    error.message ?: error.javaClass.simpleName,
                )
            }
        }

        fun executeMigration(): ApiMigrationParticipantResult {
            if (status().state == ApiMigrationParticipantState.CURRENT) {
                return ApiMigrationParticipantResult(
                    ApiMigrationParticipantResultState.ALREADY_CURRENT,
                    "already current: ${getId()}",
                )
            }
            return runCatching {
                load(allowMigrationWrite = true)
                quarantinedFileReason?.let { reason ->
                    return@runCatching ApiMigrationParticipantResult(
                        ApiMigrationParticipantResultState.FAILED,
                        "pending_interactions.yml remains quarantined: $reason",
                    )
                }
                if (quarantinedEntryIds.isNotEmpty()) {
                    return@runCatching ApiMigrationParticipantResult(
                        ApiMigrationParticipantResultState.FAILED,
                        "invalid pending records remain quarantined: ${quarantinedEntryIds.joinToString()}",
                    )
                }
                ApiMigrationParticipantResult(
                    ApiMigrationParticipantResultState.MIGRATED,
                    "migrated: ${getId()}",
                )
            }.getOrElse { error ->
                ApiMigrationParticipantResult(
                    ApiMigrationParticipantResultState.FAILED,
                    "migration failed: ${error.message ?: error.javaClass.simpleName}",
                )
            }
        }
    }

    val migrationParticipant: ApiMigrationParticipant = migrationExecutor

    init {
        MyWorldManagerApi.registerMigrationParticipant(migrationParticipant, migrationExecutor::executeMigration)
        load()
    }

    @Synchronized
    fun load(allowMigrationWrite: Boolean = false) {
        cache.clear()
        quarantinedEntryIds.clear()
        quarantinedFileReason = null
        migrationRequired = false
        if (!file.exists()) {
            return
        }

        val config = runCatching { loadStrict() }.getOrElse { error ->
            quarantinedFileReason = error.message ?: error.javaClass.simpleName
            plugin.logger.warning("[PendingInteraction] pending_interactions.yml を隔離しました: $quarantinedFileReason")
            return
        }
        val section = config.getConfigurationSection("entries")
        if (config.contains("entries") && section == null) {
            quarantinedFileReason = "entries must be a section"
            plugin.logger.warning("[PendingInteraction] pending_interactions.yml を隔離しました: $quarantinedFileReason")
            return
        }
        section ?: return
        val usedCodes = mutableMapOf<UUID, MutableSet<String>>()
        var migrated = false
        section.getKeys(false).forEach { idStr ->
            runCatching {
                val id = UUID.fromString(idStr)
                val path = "entries.$idStr"
                val type = PendingInteractionType.valueOf(
                    config.getString("$path.type") ?: error("type is missing"),
                )
                val targetUuid = UUID.fromString(
                    config.getString("$path.target_uuid") ?: error("target_uuid is missing"),
                )
                val worldUuid = UUID.fromString(
                    config.getString("$path.world_uuid") ?: error("world_uuid is missing"),
                )
                val actorUuid = UUID.fromString(
                    config.getString("$path.actor_uuid") ?: error("actor_uuid is missing"),
                )
                val createdAt = config.get("$path.created_at")
                    ?.let { value ->
                        require(value is Number) { "created_at must be numeric" }
                        value.toLong()
                    }
                    ?: error("created_at is missing")
                // 旧レコードは作成時状態を復元できないため、既存招待を新機能の対象にしない安全側へ倒します。
                val targetOnlineAtCreation = readTargetOnlineAtCreation(config, path)
                if (config.contains("$path.$TARGET_ONLINE_AT_CREATION_KEY")) {
                    require(config.get("$path.$TARGET_ONLINE_AT_CREATION_KEY") is Boolean) {
                        "$TARGET_ONLINE_AT_CREATION_KEY must be boolean"
                    }
                }
                val targetCodes = usedCodes.getOrPut(targetUuid) { mutableSetOf() }
                if (config.contains("$path.action_code")) {
                    require(config.get("$path.action_code") is String) {
                        "action_code must be a string"
                    }
                }
                val storedCode = config.getString("$path.action_code")
                val actionCode = storedCode
                    ?.takeIf(PendingActionCodeAllocator.CODE_PATTERN::matches)
                    ?.takeIf(targetCodes::add)
                    ?: PendingActionCodeAllocator().allocate(targetCodes)
                    ?: error("action code space exhausted for $targetUuid")
                if (actionCode != storedCode) {
                    targetCodes += actionCode
                    migrated = true
                }
                if (!config.contains("$path.$TARGET_ONLINE_AT_CREATION_KEY")) {
                    migrated = true
                }

                cache[id] = PendingInteraction(
                    id = id,
                    type = type,
                    targetUuid = targetUuid,
                    worldUuid = worldUuid,
                    actorUuid = actorUuid,
                    createdAt = createdAt,
                    actionCode = actionCode,
                    targetOnlineAtCreation = targetOnlineAtCreation,
                )
            }.onFailure {
                quarantinedEntryIds += idStr
                plugin.logger.warning("[PendingInteraction] 無効なレコードをスキップしました: $idStr")
            }
        }
        migrationRequired = migrated
        if (migrated && allowMigrationWrite) {
            save(allowMigrationWrite = true)
            migrationRequired = false
            plugin.logger.info("[PendingInteraction] 既存通知へ4桁操作コードを割り当てました")
        } else if (migrationRequired) {
            plugin.logger.warning("[PendingInteraction] Legacy records detected; no data was written. Run /mwm migration.")
        }
    }

    @Synchronized
    fun add(
        type: PendingInteractionType,
        targetUuid: UUID,
        worldUuid: UUID,
        actorUuid: UUID,
        actionCode: String,
        createdAt: Long = System.currentTimeMillis(),
        targetOnlineAtCreation: Boolean = true,
    ): PendingInteraction {
        ensureWritable()
        require(PendingActionCodeAllocator.CODE_PATTERN.matches(actionCode)) {
            "actionCode must be a four-digit decimal string"
        }
        require(cache.values.none { it.targetUuid == targetUuid && it.actionCode == actionCode }) {
            "duplicate actionCode for target"
        }
        val interaction = PendingInteraction(
            id = UUID.randomUUID(),
            type = type,
            targetUuid = targetUuid,
            worldUuid = worldUuid,
            actorUuid = actorUuid,
            createdAt = createdAt,
            actionCode = actionCode,
            targetOnlineAtCreation = targetOnlineAtCreation,
        )
        cache[interaction.id] = interaction
        try {
            save()
        } catch (error: Exception) {
            cache.remove(interaction.id)
            throw error
        }
        return interaction
    }

    @Synchronized
    fun remove(id: UUID): PendingInteraction? {
        ensureWritable()
        val removed = cache.remove(id)
        if (removed != null) {
            try {
                save()
            } catch (error: Exception) {
                cache[id] = removed
                throw error
            }
        }
        return removed
    }

    @Synchronized
    fun findById(id: UUID): PendingInteraction? {
        return cache[id]
    }

    @Synchronized
    fun findByTarget(targetUuid: UUID): List<PendingInteraction> {
        return cache.values
            .filter { it.targetUuid == targetUuid }
            .sortedByDescending { it.createdAt }
    }

    @Synchronized
    fun findByTargetAndActionCode(targetUuid: UUID, actionCode: String): PendingInteraction? {
        return cache.values.firstOrNull {
            it.targetUuid == targetUuid && it.actionCode == actionCode
        }
    }

    @Synchronized
    fun countByTarget(targetUuid: UUID): Int {
        return cache.values.count { it.targetUuid == targetUuid }
    }

    @Synchronized
    fun latestByTarget(targetUuid: UUID): PendingInteraction? {
        return findByTarget(targetUuid).firstOrNull()
    }

    @Synchronized
    fun findByWorldAndType(worldUuid: UUID, type: PendingInteractionType): List<PendingInteraction> {
        return cache.values
            .filter { it.worldUuid == worldUuid && it.type == type }
            .sortedBy { it.createdAt }
    }

    @Synchronized
    fun existsByTargetWorldAndType(targetUuid: UUID, worldUuid: UUID, type: PendingInteractionType): Boolean {
        return cache.values.any {
            it.targetUuid == targetUuid &&
                it.worldUuid == worldUuid &&
                it.type == type
        }
    }

    @Synchronized
    private fun save(allowMigrationWrite: Boolean = false) {
        check(!migrationRequired || allowMigrationWrite) {
            "pending_interactions.yml requires /mwm migration before it can be modified"
        }
        val config = if (file.exists()) loadStrict() else YamlConfiguration()
        val retainedIds = quarantinedEntryIds + cache.keys.map(UUID::toString)
        config.getConfigurationSection("entries")?.getKeys(false)?.forEach { id ->
            if (id !in retainedIds) config.set("entries.$id", null)
        }
        cache.values.forEach { interaction ->
            val path = "entries.${interaction.id}"
            config.set("$path.type", interaction.type.name)
            config.set("$path.target_uuid", interaction.targetUuid.toString())
            config.set("$path.world_uuid", interaction.worldUuid.toString())
            config.set("$path.actor_uuid", interaction.actorUuid.toString())
            config.set("$path.created_at", interaction.createdAt)
            config.set("$path.action_code", interaction.actionCode)
            writeTargetOnlineAtCreation(config, path, interaction.targetOnlineAtCreation)
        }

        val temporary = File(file.parentFile, "${file.name}.migration.tmp")
        val backup = File(file.parentFile, "${file.name}.pre-migration-${System.currentTimeMillis()}.bak")
        try {
            if (allowMigrationWrite && file.exists()) Files.copy(file.toPath(), backup.toPath())
            config.save(temporary)
            FileChannel.open(temporary.toPath(), StandardOpenOption.WRITE).use { it.force(true) }
            val verified = YamlConfiguration().also { it.load(temporary) }
            check(verified.getConfigurationSection("entries")?.getKeys(false).orEmpty().toSet() == retainedIds.toSet()) {
                "Pending interaction temporary file verification failed"
            }
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            Files.deleteIfExists(temporary.toPath())
            if (allowMigrationWrite && backup.exists()) {
                runCatching {
                    Files.copy(backup.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }
            plugin.logger.warning("[PendingInteraction] 保存に失敗しました: ${e.message}")
            throw e
        }
    }

    private fun ensureWritable() {
        check(quarantinedFileReason == null) {
            "pending_interactions.yml is quarantined and must be repaired before it can be modified"
        }
        check(!migrationRequired) {
            "pending_interactions.yml requires /mwm migration before it can be modified"
        }
        check(quarantinedEntryIds.isEmpty()) {
            "pending_interactions.yml contains quarantined records and must be repaired before it can be modified"
        }
    }

    private fun loadStrict(): YamlConfiguration = YamlConfiguration().also { it.load(file) }
}

private const val TARGET_ONLINE_AT_CREATION_KEY = "target_online_at_creation"

/** 新フィールドがない旧データを、既存招待の再通知対象にしない値へ補完します。 */
internal fun readTargetOnlineAtCreation(config: ConfigurationSection, path: String): Boolean =
    config.getBoolean("$path.$TARGET_ONLINE_AT_CREATION_KEY", true)

/** オンライン状態の保存形式をRepository本体と単体テストで共有します。 */
internal fun writeTargetOnlineAtCreation(
    config: ConfigurationSection,
    path: String,
    value: Boolean,
) {
    config.set("$path.$TARGET_ONLINE_AT_CREATION_KEY", value)
}
