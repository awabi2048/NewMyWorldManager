package me.awabi2048.myworldmanager.session

import me.awabi2048.myworldmanager.model.*
import java.util.*

enum class DiscoverySort {
    HOT, NEW, FAVORITES, SPOTLIGHT, RANDOM
}

data class DiscoverySession(
    val playerUuid: UUID,
    var sort: DiscoverySort = DiscoverySort.HOT,
    var selectedTag: WorldTag? = null,
    var showBackButton: Boolean = false
)

class DiscoverySessionManager {
    private val sessions = mutableMapOf<UUID, DiscoverySession>()

    fun getSession(playerUuid: UUID): DiscoverySession {
        return sessions.getOrPut(playerUuid) { DiscoverySession(playerUuid) }
    }
}
