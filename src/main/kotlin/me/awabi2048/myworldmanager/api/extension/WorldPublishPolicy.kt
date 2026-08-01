package me.awabi2048.myworldmanager.api.extension

import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.entity.Player

interface WorldPublishPolicy {
    fun getId(): String

    fun getPublishDisplayName(
        player: Player,
        worldData: WorldData,
        defaultDisplayName: String
    ): String = defaultDisplayName

    /**
     * この policy が [cyclePublishLevel] で標準の WorldData 更新を置き換えるかを示します。
     *
     * 置き換える場合、GUI の可逆監査へ参加するため [ReversibleWorldPublishPolicy] の実装が必須です。
     * false のまま true を返す実装は MWM 側で契約違反として拒否します。
     */
    fun handlesPublishCycle(worldData: WorldData): Boolean = false

    fun cyclePublishLevel(
        player: Player,
        worldData: WorldData
    ): Boolean = false
}

/** policy 所有の公開状態を、可逆監査用に不透明な値として表します。 */
interface WorldPublishReversibleState

sealed interface WorldPublishReversibleRestoreResult {
    data object Restored : WorldPublishReversibleRestoreResult

    data class Rejected(val reason: String) : WorldPublishReversibleRestoreResult {
        init {
            require(reason.isNotBlank()) { "publish restore rejection reason must not be blank" }
        }
    }
}

/**
 * 標準公開状態ではなく独自状態を更新する [WorldPublishPolicy] の必須拡張です。
 * capture / expectedAfter / restore の三段階を policy 自身の通常 storage 経路で実装します。
 */
interface ReversibleWorldPublishPolicy : WorldPublishPolicy {
    fun capturePublishCycleState(player: Player, worldData: WorldData): WorldPublishReversibleState

    fun restorePublishCycleState(
        player: Player,
        worldData: WorldData,
        before: WorldPublishReversibleState,
        expectedAfter: WorldPublishReversibleState,
    ): WorldPublishReversibleRestoreResult
}

object DefaultWorldPublishPolicy : WorldPublishPolicy {
    override fun getId(): String = "myworldmanager.default_publish"
}
