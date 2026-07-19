package me.awabi2048.myworldmanager.migration

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.service.WorldOperation
import me.awabi2048.myworldmanager.util.GuiHelper
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.WorldCreator
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

enum class MigrationWorldStatus {
    WAITING,
    RUNNING,
    RETRY,
    COMPLETED,
    FAILED
}

data class MigrationWorldState(
    val uuid: UUID,
    var folderName: String,
    var status: MigrationWorldStatus,
    var attempts: Int = 0,
    var lastError: String? = null,
    var updatedAt: Instant = Instant.now()
)

data class MigrationStatusSnapshot(
    val running: Boolean,
    val worlds: List<MigrationWorldState>,
    val currentWorld: UUID?
)

/**
 * 旧ワールドの物理移行は、管理者が確認したexecuteだけを開始経路とする。
 * 状態を毎段階で永続化し、1ワールドずつ直列に処理する。
 */
class WorldMigrationService(
    private val plugin: MyWorldManager,
    private val resolver: WorldDirectoryResolver
) : Listener {
    private val stateFile = File(plugin.dataFolder, "data/world-migration-state.yml")
    private val states = ConcurrentHashMap<UUID, MigrationWorldState>()
    @Volatile private var running = false
    @Volatile private var currentWorld: UUID? = null

    init {
        loadState()
        states.values.filter { it.status == MigrationWorldStatus.RUNNING }.forEach {
            it.status = MigrationWorldStatus.RETRY
            it.lastError = "server_stopped_during_migration"
            it.updatedAt = Instant.now()
        }
        persistState()
    }

    fun pendingWorlds(): List<LegacyWorldDirectory> = resolver.findLegacyWorlds()

    fun isPending(uuid: UUID): Boolean = resolver.findLegacyWorld(uuid) != null

    fun reportPending(sender: CommandSender? = null): List<LegacyWorldDirectory> {
        val pending = pendingWorlds()
        pending.forEach { candidate ->
            send(
                sender,
                "messages.migration.pending",
                mapOf("world" to candidate.folderName, "uuid" to candidate.uuid)
            )
        }
        resolver.findConflictingWorlds().forEach {
            send(sender, "messages.migration.conflict", mapOf("world" to it.folderName))
        }
        return pending
    }

    fun requestExecute(sender: CommandSender, confirmed: Boolean = false) {
        if (running) {
            send(sender, "messages.migration.already_running")
            return
        }
        if (sender is Player && !confirmed) {
            openConfirmation(sender)
            return
        }
        if (!confirmed) {
            send(sender, "messages.migration.console_confirm")
            return
        }
        execute(sender)
    }

    fun status(sender: CommandSender) {
        val snapshot = snapshot()
        val completed = snapshot.worlds.count { it.status == MigrationWorldStatus.COMPLETED }
        val waiting = snapshot.worlds.count {
            it.status == MigrationWorldStatus.WAITING || it.status == MigrationWorldStatus.RETRY
        }
        val retries = snapshot.worlds.count { it.status == MigrationWorldStatus.RETRY }
        val failed = snapshot.worlds.count { it.status == MigrationWorldStatus.FAILED }
        send(
            sender,
            "messages.migration.status_summary",
            mapOf(
                "state" to if (snapshot.running) "RUNNING" else "IDLE",
                "total" to snapshot.worlds.size,
                "completed" to completed,
                "waiting" to waiting,
                "retries" to retries,
                "failed" to failed,
                "current" to (snapshot.currentWorld?.toString() ?: "-")
            )
        )
        snapshot.worlds.sortedBy { it.folderName }.forEach {
            send(
                sender,
                "messages.migration.status_world",
                mapOf(
                    "world" to it.folderName,
                    "status" to it.status.name,
                    "attempts" to it.attempts,
                    "error" to (it.lastError ?: "-"),
                    "updated" to it.updatedAt
                )
            )
        }
    }

    fun snapshot(): MigrationStatusSnapshot =
        MigrationStatusSnapshot(running, states.values.map(MigrationWorldState::copy), currentWorld)

    fun resumeAfterStartup() {
        if (running) return
        if (states.values.none {
                it.status == MigrationWorldStatus.WAITING || it.status == MigrationWorldStatus.RETRY
            }
        ) {
            return
        }
        running = true
        scheduleNext()
    }

    private fun execute(sender: CommandSender) {
        val candidates = resolver.findLegacyWorlds()
        if (candidates.isEmpty()) {
            send(sender, "messages.migration.none_pending")
            return
        }
        candidates.forEach { candidate ->
            val existing = states[candidate.uuid]
            if (existing?.status != MigrationWorldStatus.COMPLETED) {
                states[candidate.uuid] = existing?.apply {
                    folderName = candidate.folderName
                    if (status == MigrationWorldStatus.FAILED && attempts < MAX_ATTEMPTS) {
                        status = MigrationWorldStatus.RETRY
                    }
                    updatedAt = Instant.now()
                } ?: MigrationWorldState(
                    candidate.uuid,
                    candidate.folderName,
                    MigrationWorldStatus.WAITING
                )
            }
        }
        persistState()
        running = true
        send(sender, "messages.migration.started", mapOf("count" to candidates.size))
        scheduleNext()
    }

    private fun scheduleNext() {
        Bukkit.getScheduler().runTask(plugin, Runnable { processNext() })
    }

    private fun processNext() {
        val next = states.values
            .filter { it.status == MigrationWorldStatus.WAITING || it.status == MigrationWorldStatus.RETRY }
            .sortedWith(compareBy<MigrationWorldState> { it.attempts }.thenBy { it.folderName })
            .firstOrNull()
        if (next == null) {
            running = false
            currentWorld = null
            persistState()
            plugin.server.consoleSender.sendMessage(
                plugin.languageManager.getMessage("messages.migration.completed")
            )
            return
        }

        currentWorld = next.uuid
        next.status = MigrationWorldStatus.RUNNING
        next.attempts++
        next.updatedAt = Instant.now()
        persistState()
        val started = System.nanoTime()
        val result = runCatching { migrateOne(next) }
        result.onSuccess {
            next.status = MigrationWorldStatus.COMPLETED
            next.lastError = null
        }.onFailure { error ->
            next.lastError = error.message ?: error.javaClass.simpleName
            next.status = if (next.attempts >= MAX_ATTEMPTS) {
                MigrationWorldStatus.FAILED
            } else {
                MigrationWorldStatus.RETRY
            }
            plugin.logger.log(
                if (next.status == MigrationWorldStatus.FAILED) Level.SEVERE else Level.WARNING,
                "World migration failed (${next.attempts}/$MAX_ATTEMPTS): ${next.folderName}",
                error
            )
        }
        next.updatedAt = Instant.now()
        currentWorld = null
        persistState()
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        if (elapsedMillis > YIELD_THRESHOLD_MILLIS) {
            plugin.logger.info("World migration stage took ${elapsedMillis}ms; yielding before the next world.")
        }
        scheduleNext()
    }

    private fun migrateOne(state: MigrationWorldState) {
        val lease = MyWorldManagerApi.tryAcquireWorldOperation(state.uuid, WorldOperation.MIGRATE)
            ?: error("world_operation_locked")
        try {
            val worldData = plugin.worldConfigRepository.findByUuid(state.uuid)
                ?: error("world_data_missing")
            val resolution = resolver.inspect(state.folderName)
                ?: error("unsafe_world_directory")
            if (resolution.state != WorldDirectoryState.LEGACY) {
                error("unexpected_directory_state:${resolution.state}")
            }
            val source = resolution.legacyPath ?: error("legacy_directory_missing")
            val target = resolution.currentPath ?: error("current_directory_missing")
            target.parent?.let(Files::createDirectories)
            moveDirectory(source, target)
            var committed = false
            try {
                val world = plugin.server.createWorld(
                    WorldCreator(NamespacedKey.minecraft(state.folderName))
                ) ?: error("world_load_failed")
                plugin.worldEnvironmentService.applyAll(world, worldData)
                committed = true
            } finally {
                if (!committed) {
                    Bukkit.getWorld(NamespacedKey.minecraft(state.folderName))?.let {
                        plugin.server.unloadWorld(it, false)
                    }
                    if (Files.exists(target) && !Files.exists(source)) {
                        moveDirectory(target, source)
                    }
                }
            }
        } finally {
            lease.close()
        }
    }

    private fun moveDirectory(source: java.nio.file.Path, target: java.nio.file.Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target)
        }
    }

    private fun openConfirmation(player: Player) {
        val holder = MigrationConfirmationHolder()
        val inventory = GuiHelper.createConfirmationInventory(
            holder,
            plugin.languageManager.getComponent(player, "gui.migration.confirm.title")
        )
        holder.backingInventory = inventory
        GuiHelper.applyConfirmationFrame(inventory)
        GuiHelper.setConfirmationItems(
            inventory,
            item(Material.COMPASS, plugin.languageManager.getComponent(player, "gui.migration.confirm.summary")),
            item(Material.LIME_CONCRETE, plugin.languageManager.getComponent(player, "gui.migration.confirm.execute")),
            item(Material.RED_CONCRETE, plugin.languageManager.getComponent(player, "gui.migration.confirm.cancel"))
        )
        player.openInventory(inventory)
    }

    @EventHandler
    fun onConfirmationClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? MigrationConfirmationHolder ?: return
        event.isCancelled = true
        if (event.rawSlot !in 0 until event.view.topInventory.size) return
        val player = event.whoClicked as? Player ?: return
        when (event.rawSlot) {
            GuiHelper.confirmationLayout().confirmSlot -> {
                player.closeInventory()
                requestExecute(player, confirmed = true)
            }
            GuiHelper.confirmationLayout().cancelSlot -> player.closeInventory()
        }
    }

    @Synchronized
    private fun loadState() {
        if (!stateFile.exists()) return
        val yaml = YamlConfiguration.loadConfiguration(stateFile)
        val section = yaml.getConfigurationSection("worlds") ?: return
        section.getKeys(false).forEach { raw ->
            val uuid = runCatching { UUID.fromString(raw) }.getOrNull() ?: return@forEach
            val base = "worlds.$raw"
            val status = runCatching {
                MigrationWorldStatus.valueOf(yaml.getString("$base.status").orEmpty())
            }.getOrNull() ?: return@forEach
            states[uuid] = MigrationWorldState(
                uuid = uuid,
                folderName = yaml.getString("$base.folder_name") ?: "my_world.$uuid",
                status = status,
                attempts = yaml.getInt("$base.attempts", 0),
                lastError = yaml.getString("$base.last_error"),
                updatedAt = runCatching {
                    Instant.parse(yaml.getString("$base.updated_at"))
                }.getOrDefault(Instant.EPOCH)
            )
        }
    }

    @Synchronized
    private fun persistState() {
        val yaml = YamlConfiguration()
        yaml.set("running", running)
        yaml.set("current_world", currentWorld?.toString())
        states.values.sortedBy { it.folderName }.forEach {
            val base = "worlds.${it.uuid}"
            yaml.set("$base.folder_name", it.folderName)
            yaml.set("$base.status", it.status.name)
            yaml.set("$base.attempts", it.attempts)
            yaml.set("$base.last_error", it.lastError)
            yaml.set("$base.updated_at", it.updatedAt.toString())
        }
        stateFile.parentFile.mkdirs()
        val temporary = File(stateFile.parentFile, "${stateFile.name}.tmp")
        yaml.save(temporary)
        try {
            Files.move(
                temporary.toPath(),
                stateFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun item(material: Material, name: net.kyori.adventure.text.Component): ItemStack =
        ItemStack(material).apply { editMeta { it.displayName(name) } }

    private fun send(
        sender: CommandSender?,
        key: String,
        placeholders: Map<String, Any> = emptyMap()
    ) {
        val message = plugin.languageManager.getMessage(key, placeholders)
        (sender ?: plugin.server.consoleSender).sendMessage(message)
    }

    private class MigrationConfirmationHolder : InventoryHolder {
        lateinit var backingInventory: Inventory
        override fun getInventory(): Inventory = backingInventory
    }

    private companion object {
        private const val MAX_ATTEMPTS = 2
        private const val YIELD_THRESHOLD_MILLIS = 1_000L
    }
}
