package me.awabi2048.myworldmanager.repository

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID

data class PlayerLocationSnapshot(
    val worldUuid: UUID,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float
)

class PlayerLocationSnapshotRepository(dataFolder: File) {
    private val snapshotFolder = File(dataFolder, "data/player-location-snapshots")

    init {
        snapshotFolder.mkdirs()
    }

    @Synchronized
    fun save(playerUuid: UUID, snapshot: PlayerLocationSnapshot) {
        val target = fileFor(playerUuid)
        val temporary = File(snapshotFolder, "${playerUuid}.yml.tmp")
        val yaml = YamlConfiguration()
        yaml.set("world_uuid", snapshot.worldUuid.toString())
        yaml.set("x", snapshot.x)
        yaml.set("y", snapshot.y)
        yaml.set("z", snapshot.z)
        yaml.set("yaw", snapshot.yaw.toDouble())
        yaml.set("pitch", snapshot.pitch.toDouble())
        try {
            yaml.save(temporary)
            FileChannel.open(temporary.toPath(), StandardOpenOption.WRITE).use { it.force(true) }
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            temporary.delete()
        }
    }

    @Synchronized
    fun find(playerUuid: UUID): PlayerLocationSnapshot? {
        val file = fileFor(playerUuid)
        if (!file.isFile) return null
        val yaml = YamlConfiguration.loadConfiguration(file)
        val worldUuid = yaml.getString("world_uuid")
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return null
        if (listOf("x", "y", "z", "yaw", "pitch").any { !yaml.contains(it) }) return null
        val snapshot = PlayerLocationSnapshot(
            worldUuid = worldUuid,
            x = yaml.getDouble("x"),
            y = yaml.getDouble("y"),
            z = yaml.getDouble("z"),
            yaw = yaml.getDouble("yaw").toFloat(),
            pitch = yaml.getDouble("pitch").toFloat()
        )
        return snapshot.takeIf {
            it.x.isFinite() && it.y.isFinite() && it.z.isFinite() &&
                it.yaw.isFinite() && it.pitch.isFinite()
        }
    }

    @Synchronized
    fun delete(playerUuid: UUID): Boolean = !fileFor(playerUuid).exists() || fileFor(playerUuid).delete()

    private fun fileFor(playerUuid: UUID): File = File(snapshotFolder, "$playerUuid.yml")
}
