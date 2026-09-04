package me.awabi2048.myworldmanager.api.extension

import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.entity.Player

/**
 * ワールドプレイヤー状態ポリシーが返す、建築・飛行制約だけの判定結果です。
 *
 * ゲームモードはプレビューやFreeCamなどの明示的な一時セッションが管理するため、
 * ワールドポリシーからは変更しません。
 */
data class WorldPlayerStateDecision(
    val buildAllowed: Boolean? = null,
    val flightAllowed: Boolean? = null
)

interface WorldPlayerStatePolicy {
    fun getId(): String

    fun getPriority(): Int = 0

    fun evaluate(player: Player, worldData: WorldData): WorldPlayerStateDecision
}
