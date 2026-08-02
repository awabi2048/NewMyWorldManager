package me.awabi2048.myworldmanager.api.extension

import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.UUID

enum class WorldCreationOperation {
    NORMAL,
    PRODUCTION
}

data class WorldCreationRequest(
    val actor: CommandSender?,
    val player: Player?,
    val operation: WorldCreationOperation,
    val type: WorldCreationType?,
    val targetUuid: UUID? = player?.uniqueId
)

data class WorldCreationDecision(
    val allowed: Boolean,
    val denialMessage: Component? = null
) {
    companion object {
        @JvmStatic
        fun allow(): WorldCreationDecision = WorldCreationDecision(true)

        @JvmStatic
        fun deny(message: Component? = null): WorldCreationDecision = WorldCreationDecision(false, message)
    }
}

/**
 * ワールド作成可否を判定する拡張ポイントです。
 *
 * この判定は通常実行だけでなく、GUIの描画およびRuntime inspect中にも呼び出されます。
 * [evaluate] は状態変更、メッセージ送信、スケジューラ登録、ワールド操作その他の外部操作を
 * 一切行わない純粋な照会として実装してください。
 */
interface WorldCreationGuard {
    fun getId(): String

    fun evaluate(request: WorldCreationRequest): WorldCreationDecision
}
