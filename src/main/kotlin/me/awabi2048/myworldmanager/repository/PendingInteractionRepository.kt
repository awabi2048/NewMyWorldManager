package me.awabi2048.myworldmanager.repository

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PendingInteraction
import me.awabi2048.myworldmanager.model.PendingInteractionType
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PendingInteractionRepository(private val plugin: MyWorldManager) {

    private val file = File(plugin.dataFolder, "pending_interactions.yml")
    private val cache = ConcurrentHashMap<UUID, PendingInteraction>()

    init {
        load()
    }

    @Synchronized
    fun load() {
        cache.clear()
        if (!file.exists()) {
            return
        }

        val config = YamlConfiguration.loadConfiguration(file)
        val section = config.getConfigurationSection("entries") ?: return
        section.getKeys(false).forEach { idStr ->
            runCatching {
                val id = UUID.fromString(idStr)
                val path = "entries.$idStr"
                val type = PendingInteractionType.valueOf(config.getString("$path.type") ?: return@runCatching)
                val targetUuid = UUID.fromString(config.getString("$path.target_uuid") ?: return@runCatching)
                val worldUuid = UUID.fromString(config.getString("$path.world_uuid") ?: return@runCatching)
                val actorUuid = UUID.fromString(config.getString("$path.actor_uuid") ?: return@runCatching)
                val createdAt = config.getLong("$path.created_at")

                cache[id] = PendingInteraction(
                    id = id,
                    type = type,
                    targetUuid = targetUuid,
                    worldUuid = worldUuid,
                    actorUuid = actorUuid,
                    createdAt = createdAt
                )
            }.onFailure {
                plugin.logger.warning("[PendingInteraction] 無効なレコードをスキップしました: $idStr")
            }
        }
    }

    @Synchronized
    fun add(
        type: PendingInteractionType,
        targetUuid: UUID,
        worldUuid: UUID,
        actorUuid: UUID,
        createdAt: Long = System.currentTimeMillis()
    ): PendingInteraction {
        val interaction = PendingInteraction(
            id = UUID.randomUUID(),
            type = type,
            targetUuid = targetUuid,
            worldUuid = worldUuid,
            actorUuid = actorUuid,
            createdAt = createdAt
        )
        cache[interaction.id] = interaction
        save()
        return interaction
    }

    @Synchronized
    fun remove(id: UUID): PendingInteraction? {
        val removed = cache.remove(id)
        if (removed != null) {
            save()
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
    fun countByTarget(targetUuid: UUID): Int {
        return cache.values.count { it.targetUuid == targetUuid }
    }

    @Synchronized
    fun latestByTarget(targetUuid: UUID): PendingInteraction? {
        return findByTarget(targetUuid).firstOrNull()
    }

    @Synchronized
    private fun save() {
        val config = YamlConfiguration()
        cache.values.forEach { interaction ->
            val path = "entries.${interaction.id}"
            config.set("$path.type", interaction.type.name)
            config.set("$path.target_uuid", interaction.targetUuid.toString())
            config.set("$path.world_uuid", interaction.worldUuid.toString())
            config.set("$path.actor_uuid", interaction.actorUuid.toString())
            config.set("$path.created_at", interaction.createdAt)
        }

        runCatching {
            config.save(file)
        }.onFailure { e ->
            plugin.logger.warning("[PendingInteraction] 保存に失敗しました: ${e.message}")
        }
    }
}
