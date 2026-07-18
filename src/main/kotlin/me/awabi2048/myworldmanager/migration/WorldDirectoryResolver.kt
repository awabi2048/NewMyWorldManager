package me.awabi2048.myworldmanager.migration

import com.awabi2048.ccsystem.api.world.WorldDirectoryService
import com.awabi2048.ccsystem.api.world.WorldDirectoryState as CommonWorldDirectoryState
import java.nio.file.Path
import java.util.UUID

/** Paperの物理配置はCC-Systemへ委譲し、MWM固有の旧MyWorld判定だけを担当する。 */
class WorldDirectoryResolver(
    private val directoryService: WorldDirectoryService
) {
    fun inspect(folderName: String): WorldDirectoryResolution? {
        if (!isValidFolderName(folderName)) return null
        val key = org.bukkit.NamespacedKey.minecraft(folderName)
        val common = directoryService.inspect(key, folderName)
        val state = when (common.state) {
            CommonWorldDirectoryState.CURRENT -> WorldDirectoryState.CURRENT
            CommonWorldDirectoryState.LEGACY -> WorldDirectoryState.LEGACY
            CommonWorldDirectoryState.MISSING -> WorldDirectoryState.MISSING
            CommonWorldDirectoryState.CONFLICT -> WorldDirectoryState.CONFLICT
            CommonWorldDirectoryState.UNSAFE -> return null
        }
        return WorldDirectoryResolution(
            state,
            common.legacyDirectory?.takeIf { state == WorldDirectoryState.LEGACY || state == WorldDirectoryState.CONFLICT },
            common.currentDirectory.takeIf { state == WorldDirectoryState.CURRENT || state == WorldDirectoryState.CONFLICT }
        )
    }

    fun findLegacyWorlds(): List<LegacyWorldDirectory> {
        return directoryService.listLegacyByNamePrefix(PREFIX).mapNotNull { path ->
            val name = path.fileName.toString()
            val uuid = parseWorldUuid(name) ?: return@mapNotNull null
            val resolution = inspect(name) ?: return@mapNotNull null
            if (resolution.state != WorldDirectoryState.LEGACY) return@mapNotNull null
            LegacyWorldDirectory(uuid, name, path)
        }.sortedBy { it.folderName }
    }

    fun findLegacyWorld(uuid: UUID): LegacyWorldDirectory? =
        findLegacyWorlds().firstOrNull { it.uuid == uuid }

    fun findConflictingWorlds(): List<LegacyWorldDirectory> {
        return directoryService.listLegacyByNamePrefix(PREFIX).mapNotNull { path ->
            val name = path.fileName.toString()
            val uuid = parseWorldUuid(name) ?: return@mapNotNull null
            val resolution = inspect(name) ?: return@mapNotNull null
            if (resolution.state != WorldDirectoryState.CONFLICT) return@mapNotNull null
            LegacyWorldDirectory(uuid, name, path)
        }.sortedBy { it.folderName }
    }

    private fun isValidFolderName(folderName: String): Boolean {
        if (folderName.isBlank() || folderName == "." || folderName == "..") return false
        if (folderName.contains('/') || folderName.contains('\\')) return false
        if (folderName.startsWith(PREFIX) && parseWorldUuid(folderName) == null) return false
        return true
    }

    companion object {
        private const val PREFIX = "my_world."

        fun parseWorldUuid(folderName: String): UUID? {
            if (!folderName.startsWith(PREFIX)) return null
            val raw = folderName.removePrefix(PREFIX)
            val uuid = runCatching { UUID.fromString(raw) }.getOrNull() ?: return null
            return uuid.takeIf { "$PREFIX$it" == folderName }
        }
    }
}

enum class WorldDirectoryState {
    CURRENT,
    LEGACY,
    MISSING,
    CONFLICT
}

data class WorldDirectoryResolution(
    val state: WorldDirectoryState,
    val legacyPath: Path?,
    val currentPath: Path?
) {
    /** 既存ワールド操作に使用できるパス。曖昧または欠落した状態では返さない。 */
    val existingPath: Path?
        get() = when (state) {
            WorldDirectoryState.CURRENT -> currentPath
            WorldDirectoryState.LEGACY -> legacyPath
            WorldDirectoryState.MISSING,
            WorldDirectoryState.CONFLICT -> null
        }
}

data class LegacyWorldDirectory(
    val uuid: UUID,
    val folderName: String,
    val path: Path
)
