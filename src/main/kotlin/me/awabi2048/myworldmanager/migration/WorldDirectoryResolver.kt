package me.awabi2048.myworldmanager.migration

import com.awabi2048.ccsystem.api.world.WorldDirectoryService
import com.awabi2048.ccsystem.api.world.WorldDirectoryState as CommonWorldDirectoryState
import java.nio.file.Path
import java.util.UUID
import org.bukkit.NamespacedKey

/** Paperの物理配置はCC-Systemへ委譲し、MWM固有の旧MyWorld判定だけを担当する。 */
class WorldDirectoryResolver(
    private val directoryService: WorldDirectoryService
) {
    /** 新規ワールドを作る際にPaperが使用する正規ディレクトリです。 */
    fun creationDirectory(key: NamespacedKey): Path = directoryService.creationDirectory(key)

    fun inspect(folderName: String): WorldDirectoryResolution? {
        if (!isValidFolderName(folderName)) return null
        return inspect(NamespacedKey.minecraft(folderName))
    }

    /**
     * 実際にWorldCreatorへ渡すキーと同じキーで物理配置を診断します。
     * 旧ルート直下形式はminecraft名前空間でのみ成立するため、他名前空間へ名前を推測しません。
     */
    fun inspect(key: NamespacedKey): WorldDirectoryResolution {
        val legacyName = key.key.takeIf { key.namespace == NamespacedKey.MINECRAFT }
        val common = directoryService.inspect(key, legacyName)
        val state = when (common.state) {
            CommonWorldDirectoryState.CURRENT -> WorldDirectoryState.CURRENT
            CommonWorldDirectoryState.LEGACY -> WorldDirectoryState.LEGACY
            CommonWorldDirectoryState.MISSING -> WorldDirectoryState.MISSING
            CommonWorldDirectoryState.CONFLICT -> WorldDirectoryState.CONFLICT
            CommonWorldDirectoryState.UNSAFE -> WorldDirectoryState.UNSAFE
        }
        // LEGACY も currentPath を保持します。CC-System側の currentDirectory は存在有無に関わらず
        // 計算済みの移行先パスであり、migrateOne はこれを LEGACY→CURRENT の移動先として使います。
        // ここで null に落とすと純粋MWM環境（全ワールドが LEGACY）で移行が全件失敗するため、
        // 実在ディレクトリの選択（existingPath）と移行先（currentPath）を分離して返します。
        return WorldDirectoryResolution(
            state,
            common.legacyDirectory?.takeIf { state == WorldDirectoryState.LEGACY || state == WorldDirectoryState.CONFLICT },
            common.currentDirectory.takeIf {
                state == WorldDirectoryState.CURRENT ||
                    state == WorldDirectoryState.CONFLICT ||
                    state == WorldDirectoryState.LEGACY
            }
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

    /**
     * UUIDを安全に特定できない、または通常の移行対象一覧へ分類できないディレクトリです。
     * 全ワールド操作ではこれを無視すると、未解決データを残したまま削除・アーカイブを
     * 進めるため、対象UUIDを持たない保留として別途報告します。
     */
    fun findUnresolvedWorldDirectories(): List<String> =
        directoryService.listLegacyByNamePrefix(PREFIX)
            .map { it.fileName.toString() }
            .filter { folderName ->
                parseWorldUuid(folderName) == null ||
                    inspect(folderName)?.state == WorldDirectoryState.UNSAFE
            }
            .sorted()

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
    CONFLICT,
    UNSAFE
}

data class WorldDirectoryResolution(
    val state: WorldDirectoryState,
    val legacyPath: Path?,
    /** 現行配置のパス。CURRENT/CONFLICT は実在パス、LEGACY は移行先パス（実在とは限らない）。 */
    val currentPath: Path?
) {
    /** 既存ワールド操作に使用できるパス。曖昧または欠落した状態では返さない。 */
    val existingPath: Path?
        get() = when (state) {
            WorldDirectoryState.CURRENT -> currentPath
            WorldDirectoryState.LEGACY -> legacyPath
            WorldDirectoryState.MISSING,
            WorldDirectoryState.CONFLICT,
            WorldDirectoryState.UNSAFE -> null
        }
}

data class LegacyWorldDirectory(
    val uuid: UUID,
    val folderName: String,
    val path: Path
)
