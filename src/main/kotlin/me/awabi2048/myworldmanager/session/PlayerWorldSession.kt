package me.awabi2048.myworldmanager.session

import java.util.*

data class PlayerWorldSession(
    val playerUuid: UUID,
    var showBackButton: Boolean = false
)

class PlayerWorldSessionManager {
    private val sessions = mutableMapOf<UUID, PlayerWorldSession>()

    fun getSession(playerUuid: UUID): PlayerWorldSession {
        return sessions.getOrPut(playerUuid) { PlayerWorldSession(playerUuid) }
    }
}
