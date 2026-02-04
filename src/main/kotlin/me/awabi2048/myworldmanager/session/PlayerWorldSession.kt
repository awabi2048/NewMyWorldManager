package me.awabi2048.myworldmanager.session

import java.util.*

data class PlayerWorldSession(
    val playerUuid: UUID,
    var showBackButton: Boolean = false,
    var currentPage: Int = 0
)

class PlayerWorldSessionManager {
    private val sessions = mutableMapOf<UUID, PlayerWorldSession>()

    fun getSession(playerUuid: UUID): PlayerWorldSession {
        return sessions.getOrPut(playerUuid) { PlayerWorldSession(playerUuid) }
    }
}
