package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.migration.WorldDirectoryResolver
import me.awabi2048.myworldmanager.model.ManagedDimension
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Chiyogami/Paper固有のEndドラゴン戦停止設定を、通常のワールド管理から隔離します。
 *
 * Chiyogami 26.1.2ではこの設定がEnderDragonFight生成時にdragonKilled=trueを強制するため、
 * ボスバーを一時的に隠すのではなく、初回ロード前からドラゴン戦自体を開始させません。
 */
class PaperEndDragonPolicyAdapter(
    private val resolver: WorldDirectoryResolver
) {
    fun prepareBeforeLoad(key: NamespacedKey, dimension: ManagedDimension) {
        if (dimension != ManagedDimension.END) return

        val directory = resolver.inspect(key).existingPath ?: resolver.creationDirectory(key)
        Files.createDirectories(directory)
        val target = directory.resolve(PAPER_WORLD_FILE)
        val temporary = directory.resolve("$PAPER_WORLD_FILE.tmp")
        val backup = directory.resolve("$PAPER_WORLD_FILE.pre-mwm-end-policy.bak")

        val config = YamlConfiguration.loadConfiguration(target.toFile())
        if (!config.getBoolean(SCAN_SETTING, true)) return

        if (Files.exists(target) && !Files.exists(backup)) {
            Files.copy(target, backup)
        }
        config.set(SCAN_SETTING, false)
        config.save(temporary.toFile())
        FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }

        try {
            Files.move(
                temporary,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }

        val verified = YamlConfiguration.loadConfiguration(target.toFile())
        check(!verified.getBoolean(SCAN_SETTING, true)) {
            "Paper End dragon policy verification failed: $key"
        }
    }

    private companion object {
        const val PAPER_WORLD_FILE = "paper-world.yml"
        const val SCAN_SETTING = "entities.spawning.scan-for-legacy-ender-dragon"
    }
}
