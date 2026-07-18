package me.awabi2048.myworldmanager.migration

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/** 旧構造を移動せず、管理者承認後にBukkitへロードを委譲する。 */
class WorldMigrationService(
    private val plugin: MyWorldManager,
    private val resolver: WorldDirectoryResolver
) {
    private val approved = ConcurrentHashMap.newKeySet<UUID>()
    private val rejected = ConcurrentHashMap.newKeySet<UUID>()
    private val stateFile = File(plugin.dataFolder, "data/world-migration-state.yml")
    private val state = YamlConfiguration.loadConfiguration(stateFile)

    init {
        state.getStringList("approved").mapNotNullTo(approved) { runCatching { UUID.fromString(it) }.getOrNull() }
        state.getStringList("rejected").mapNotNullTo(rejected) { runCatching { UUID.fromString(it) }.getOrNull() }
    }

    fun pendingWorlds(): List<LegacyWorldDirectory> =
        resolver.findLegacyWorlds().filterNot { it.uuid in rejected || it.uuid in approved }

    fun isPending(uuid: UUID): Boolean =
        resolver.findLegacyWorld(uuid) != null && uuid !in approved && uuid !in rejected

    fun reportPending(sender: CommandSender? = null): List<LegacyWorldDirectory> {
        val pending = pendingWorlds()
        pending.forEach { candidate ->
            val line = plugin.languageManager.getMessage("messages.migration.approval_required", mapOf("world" to candidate.folderName, "uuid" to candidate.uuid))
            if (sender != null) sender.sendMessage(line) else plugin.server.consoleSender.sendMessage(line)
        }
        resolver.findConflictingWorlds().forEach { candidate ->
            val line = plugin.languageManager.getMessage(
                "messages.migration.conflict",
                mapOf("world" to candidate.folderName)
            )
            if (sender != null) sender.sendMessage(line) else plugin.server.consoleSender.sendMessage(line)
        }
        return pending
    }

    fun approveAndLoad(uuid: UUID, sender: CommandSender? = null): Boolean {
        val expectedFolderName = "my_world.$uuid"
        if (resolver.inspect(expectedFolderName)?.state == WorldDirectoryState.CONFLICT) {
            val message = plugin.languageManager.getMessage(
                "messages.migration.conflict",
                mapOf("world" to expectedFolderName)
            )
            plugin.logger.severe(message)
            sender?.sendMessage(message)
            return false
        }
        val candidate = resolver.findLegacyWorld(uuid) ?: return false
        val worldData = plugin.worldConfigRepository.findByUuid(uuid)
        if (worldData == null) {
            sender?.sendMessage(plugin.languageManager.getMessage("messages.migration.world_data_missing", mapOf("world" to candidate.folderName)))
            return false
        }
        rejected.remove(uuid)
        approved += uuid
        persistState(uuid, "APPROVED")
        val loaded = loadWithBukkit(worldData)
        if (!loaded) {
            approved.remove(uuid)
            persistState(uuid, "FAILED")
        } else {
            persistState(uuid, "COMMITTED")
        }
        sender?.sendMessage(plugin.languageManager.getMessage(if (loaded) "messages.migration.approved" else "messages.migration.load_failed", mapOf("world" to candidate.folderName)))
        return loaded
    }

    fun reject(uuid: UUID): Boolean {
        if (resolver.findLegacyWorld(uuid) == null) return false
        approved.remove(uuid)
        rejected += uuid
        persistState(uuid, "REJECTED")
        return true
    }

    @Synchronized
    private fun persistState(uuid: UUID, status: String) {
        state.set("approved", approved.map(UUID::toString).sorted())
        state.set("rejected", rejected.map(UUID::toString).sorted())
        state.set("worlds.$uuid.status", status)
        state.set("worlds.$uuid.updated_at", java.time.Instant.now().toString())
        stateFile.parentFile.mkdirs()
        state.save(stateFile)
    }

    private fun loadWithBukkit(worldData: WorldData): Boolean {
        val folderName = plugin.worldService.getWorldFolderName(worldData)
        val resolution = resolver.inspect(folderName) ?: return false
        if (resolution.state != WorldDirectoryState.LEGACY) return false
        Bukkit.getWorld(folderName)?.let {
            plugin.worldEnvironmentService.applyAll(it, worldData)
            return true
        }
        return try {
            val world = plugin.server.createWorld(WorldCreator(folderName)) ?: return false
            plugin.worldEnvironmentService.applyAll(world, worldData)
            true
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "旧ワールドのBukkitロードに失敗しました: $folderName", failure)
            false
        }
    }
}
