package me.awabi2048.myworldmanager.model

import java.util.UUID

enum class PendingInteractionType {
    MEMBER_INVITE,
    MEMBER_REQUEST
}

data class PendingInteraction(
    val id: UUID,
    val type: PendingInteractionType,
    val targetUuid: UUID,
    val worldUuid: UUID,
    val actorUuid: UUID,
    val createdAt: Long,
    val actionCode: String,
    /** 招待作成時点の対象オンライン状態。旧データはRepository側で安全にtrueへ補完します。 */
    val targetOnlineAtCreation: Boolean = true,
)
