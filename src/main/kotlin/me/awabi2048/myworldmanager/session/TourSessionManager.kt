package me.awabi2048.myworldmanager.session

import me.awabi2048.myworldmanager.model.TourData
import org.bukkit.Material
import java.util.UUID

data class ActiveTourSession(
    val playerUuid: UUID,
    val worldUuid: UUID,
    var tourUuid: UUID,
    var nextIndex: Int = 0
)

data class TourEditSession(
    val playerUuid: UUID,
    val worldUuid: UUID,
    var draft: TourData,
    val originalTourUuid: UUID?,
    var awaitingIconPick: Boolean = false
) {
    val isNew: Boolean get() = originalTourUuid == null
}

class TourSessionManager {
    private val sessions = mutableMapOf<UUID, ActiveTourSession>()
    private val editSessions = mutableMapOf<UUID, TourEditSession>()

    fun get(playerUuid: UUID): ActiveTourSession? = sessions[playerUuid]

    fun start(playerUuid: UUID, worldUuid: UUID, tourUuid: UUID): ActiveTourSession {
        val session = ActiveTourSession(playerUuid, worldUuid, tourUuid, 0)
        sessions[playerUuid] = session
        return session
    }

    fun end(playerUuid: UUID) {
        sessions.remove(playerUuid)
    }

    fun findActiveByTour(worldUuid: UUID, tourUuid: UUID): List<ActiveTourSession> {
        return sessions.values.filter { it.worldUuid == worldUuid && it.tourUuid == tourUuid }
    }

    fun getEdit(playerUuid: UUID): TourEditSession? = editSessions[playerUuid]

    fun openNewEdit(playerUuid: UUID, worldUuid: UUID, name: String, description: String): TourEditSession {
        val session = TourEditSession(
            playerUuid = playerUuid,
            worldUuid = worldUuid,
            draft = TourData(UUID.randomUUID(), name, description, Material.OAK_BOAT),
            originalTourUuid = null
        )
        editSessions[playerUuid] = session
        return session
    }

    fun openExistingEdit(playerUuid: UUID, worldUuid: UUID, source: TourData): TourEditSession {
        val copy = source.copy(
            signUuids = source.signUuids.toMutableList(),
            startedPlayerUuids = source.startedPlayerUuids.toMutableSet()
        )
        val session = TourEditSession(playerUuid, worldUuid, copy, source.uuid)
        editSessions[playerUuid] = session
        return session
    }

    fun clearEdit(playerUuid: UUID) {
        editSessions.remove(playerUuid)
    }
}
