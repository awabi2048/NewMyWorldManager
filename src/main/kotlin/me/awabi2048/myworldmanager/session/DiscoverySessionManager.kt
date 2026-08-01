package me.awabi2048.myworldmanager.session

import java.util.*

enum class DiscoverySort {
    HOT, NEW, FAVORITES, SPOTLIGHT, RANDOM
}

enum class DiscoverySpecialFilter {
    NONE, UNVISITED
}

data class DiscoverySession(
    val playerUuid: UUID,
    var sort: DiscoverySort = DiscoverySort.HOT,
    var selectedTag: String? = null,
    var specialFilter: DiscoverySpecialFilter = DiscoverySpecialFilter.NONE,
    var showBackButton: Boolean = false
)

class DiscoverySessionManager {
    private val sessions = mutableMapOf<UUID, DiscoverySession>()

    fun getSession(playerUuid: UUID): DiscoverySession {
        return sessions.getOrPut(playerUuid) { DiscoverySession(playerUuid) }
    }

    fun snapshot(playerUuid: UUID): DiscoverySession = getSession(playerUuid).copy()

    fun restore(playerUuid: UUID, snapshot: DiscoverySession) {
        require(snapshot.playerUuid == playerUuid) { "discovery session player mismatch" }
        sessions[playerUuid] = snapshot.copy()
    }

    fun clearSession(playerUuid: UUID) {
        sessions.remove(playerUuid)
    }

    fun clearAll() {
        sessions.clear()
    }
}
