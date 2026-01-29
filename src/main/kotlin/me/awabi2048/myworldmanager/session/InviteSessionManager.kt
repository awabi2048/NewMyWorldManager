package me.awabi2048.myworldmanager.session

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InviteSessionManager {
    private val sessions = ConcurrentHashMap<UUID, InviteSession>()

    fun startSession(playerId: UUID, worldUuid: UUID) {
        sessions[playerId] = InviteSession(playerId, worldUuid)
    }

    fun getSession(playerId: UUID): InviteSession? {
        return sessions[playerId]
    }

    fun endSession(playerId: UUID) {
        sessions.remove(playerId)
    }
}
