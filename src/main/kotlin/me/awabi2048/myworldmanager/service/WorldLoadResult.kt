package me.awabi2048.myworldmanager.service

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.migration.WorldDirectoryState
import org.bukkit.World
import org.bukkit.entity.Player

/**
 * ワールドのロード結果を、利用者への案内と運用ログで同じ理由に分類するための型です。
 *
 * 失敗をBooleanへ潰すと、移行待ちと破損を区別できず、呼び出し元が成功表示を続行するため、
 * ロード境界では必ずこの結果を保持します。
 */
class WorldLoadResult private constructor(
    val world: World?,
    val loadedNow: Boolean,
    val failure: WorldLoadFailure?
) {
    val isSuccess: Boolean
        get() = world != null && failure == null

    companion object {
        fun success(world: World, loadedNow: Boolean): WorldLoadResult =
            WorldLoadResult(world, loadedNow, null)

        fun failure(reason: WorldLoadFailure): WorldLoadResult =
            WorldLoadResult(null, false, reason)
    }
}

/**
 * ロード不能の原因です。入口ごとの文言差や誤案内を防ぐため、この分類から案内を解決します。
 */
enum class WorldLoadFailure {
    INVALID_KEY,
    OPERATION_LOCKED,
    MIGRATION_REQUIRED,
    DIRECTORY_CONFLICT,
    DIRECTORY_MISSING,
    DIRECTORY_UNSAFE,
    BUKKIT_LOAD_FAILED;

    fun message(plugin: MyWorldManager, player: Player): String = when (this) {
        INVALID_KEY -> plugin.languageManager.getMessage(player, CommonKeys.ERROR_WORLD_DIRECTORY_UNSAFE)
        OPERATION_LOCKED -> plugin.languageManager.getMessage(player, MyworldMessagesKeys.MESSAGES_WORLD_OPERATION_LOCKED)
        MIGRATION_REQUIRED -> plugin.languageManager.getMessage(player, CommonKeys.ERROR_WORLD_MIGRATION_REQUIRED)
        DIRECTORY_CONFLICT -> plugin.languageManager.getMessage(player, CommonKeys.ERROR_WORLD_DIRECTORY_CONFLICT)
        DIRECTORY_MISSING -> plugin.languageManager.getMessage(player, CommonKeys.ERROR_WORLD_DIRECTORY_MISSING)
        DIRECTORY_UNSAFE -> plugin.languageManager.getMessage(player, CommonKeys.ERROR_WORLD_DIRECTORY_UNSAFE)
        BUKKIT_LOAD_FAILED -> plugin.languageManager.getMessage(player, CommonKeys.ERROR_WORLD_LOAD_FAILED)
    }
}

/** CURRENT以外をBukkitロードへ進ませない、既存ワールド共通の判定表です。 */
internal object WorldLoadDirectoryPolicy {
    fun rejectionFor(state: WorldDirectoryState): WorldLoadFailure? = when (state) {
        WorldDirectoryState.CURRENT -> null
        WorldDirectoryState.LEGACY -> WorldLoadFailure.MIGRATION_REQUIRED
        WorldDirectoryState.CONFLICT -> WorldLoadFailure.DIRECTORY_CONFLICT
        WorldDirectoryState.MISSING -> WorldLoadFailure.DIRECTORY_MISSING
        WorldDirectoryState.UNSAFE -> WorldLoadFailure.DIRECTORY_UNSAFE
    }
}
