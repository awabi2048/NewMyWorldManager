package me.awabi2048.myworldmanager.migration

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.UUID

/** Paper 26.1.2の既存ワールド配置を、実在する安全なディレクトリだけから判定する。 */
class WorldDirectoryResolver(serverRoot: Path) {
    private val root = serverRoot.toAbsolutePath().normalize()
    private val currentRoot = root.resolve("world/dimensions/minecraft").normalize()

    fun inspect(folderName: String): WorldDirectoryResolution? {
        if (!isValidFolderName(folderName)) return null

        val legacy = root.resolve(folderName).normalize()
        val current = currentRoot.resolve(folderName).normalize()
        if (!legacy.startsWith(root) || !current.startsWith(currentRoot)) return null

        val legacyExists = isSafeDirectory(legacy, root)
        val currentExists = isSafeDirectory(current, currentRoot)
        val state = when {
            legacyExists && currentExists -> WorldDirectoryState.CONFLICT
            legacyExists -> WorldDirectoryState.LEGACY
            currentExists -> WorldDirectoryState.CURRENT
            else -> WorldDirectoryState.MISSING
        }
        return WorldDirectoryResolution(state, legacy.takeIf { legacyExists }, current.takeIf { currentExists })
    }

    fun findLegacyWorlds(): List<LegacyWorldDirectory> {
        if (!isSafeDirectory(root, root)) return emptyList()
        return Files.list(root).use { stream ->
            stream.iterator().asSequence().mapNotNull { path ->
                val name = path.fileName.toString()
                val uuid = parseWorldUuid(name) ?: return@mapNotNull null
                val resolution = inspect(name) ?: return@mapNotNull null
                if (resolution.state != WorldDirectoryState.LEGACY) return@mapNotNull null
                LegacyWorldDirectory(uuid, name, path)
            }.sortedBy { it.folderName }.toList()
        }
    }

    fun findLegacyWorld(uuid: UUID): LegacyWorldDirectory? =
        findLegacyWorlds().firstOrNull { it.uuid == uuid }

    fun findConflictingWorlds(): List<LegacyWorldDirectory> {
        if (!isSafeDirectory(root, root)) return emptyList()
        return Files.list(root).use { stream ->
            stream.iterator().asSequence().mapNotNull { path ->
                val name = path.fileName.toString()
                val uuid = parseWorldUuid(name) ?: return@mapNotNull null
                val resolution = inspect(name) ?: return@mapNotNull null
                if (resolution.state != WorldDirectoryState.CONFLICT) return@mapNotNull null
                LegacyWorldDirectory(uuid, name, path)
            }.sortedBy { it.folderName }.toList()
        }
    }

    private fun isValidFolderName(folderName: String): Boolean {
        if (folderName.isBlank() || folderName == "." || folderName == "..") return false
        if (folderName.contains('/') || folderName.contains('\\')) return false
        if (folderName.startsWith(PREFIX) && parseWorldUuid(folderName) == null) return false
        return true
    }

    private fun isSafeDirectory(path: Path, boundary: Path): Boolean {
        if (!path.startsWith(boundary)) return false
        var current = path
        while (current != boundary) {
            if (Files.isSymbolicLink(current)) return false
            current = current.parent ?: return false
        }
        return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
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
