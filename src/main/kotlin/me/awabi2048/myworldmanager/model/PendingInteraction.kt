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
    val createdAt: Long
)
