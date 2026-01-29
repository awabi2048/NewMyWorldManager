package me.awabi2048.myworldmanager.session

import java.util.UUID

data class InviteSession(
    val inviterUuid: UUID,
    val worldUuid: UUID
)
