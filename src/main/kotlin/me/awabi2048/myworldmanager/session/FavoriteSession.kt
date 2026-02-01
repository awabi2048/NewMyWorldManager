package me.awabi2048.myworldmanager.session

import java.util.*

data class FavoriteSession(
    val playerUuid: UUID,
    var showBackButton: Boolean = false
)

class FavoriteSessionManager {
    private val sessions = mutableMapOf<UUID, FavoriteSession>()

    fun getSession(playerUuid: UUID): FavoriteSession {
        return sessions.getOrPut(playerUuid) { FavoriteSession(playerUuid) }
    }
}
