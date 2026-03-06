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
}
