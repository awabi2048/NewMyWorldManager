package me.awabi2048.myworldmanager.session

import java.util.*

data class MeetSession(
    val playerUuid: UUID,
    var showBackButton: Boolean = false,
    var currentPage: Int = 0
)

class MeetSessionManager {
    private val sessions = mutableMapOf<UUID, MeetSession>()

    fun getSession(playerUuid: UUID): MeetSession {
        return sessions.getOrPut(playerUuid) { MeetSession(playerUuid) }
    }

    fun snapshot(playerUuid: UUID): MeetSession = getSession(playerUuid).copy()

    fun restore(playerUuid: UUID, snapshot: MeetSession) {
        require(snapshot.playerUuid == playerUuid) { "meet session player mismatch" }
        sessions[playerUuid] = snapshot.copy()
    }

    fun clearSession(playerUuid: UUID) {
        sessions.remove(playerUuid)
    }

    fun clearAll() {
        sessions.clear()
    }
}
