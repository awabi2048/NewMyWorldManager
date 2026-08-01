package me.awabi2048.myworldmanager.session

import me.awabi2048.myworldmanager.model.TourData
import me.awabi2048.myworldmanager.model.TourWaypointData
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
    var awaitingIconPick: Boolean = false,
    var awaitingWaypointPick: Boolean = false
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

    fun snapshotEdit(playerUuid: UUID): TourEditSessionSnapshot? = editSessions[playerUuid]?.immutableSnapshot()

    fun restoreEdit(playerUuid: UUID, snapshot: TourEditSessionSnapshot?) {
        require(snapshot == null || snapshot.playerUuid == playerUuid) { "tour edit session player mismatch" }
        if (snapshot == null) editSessions.remove(playerUuid) else editSessions[playerUuid] = snapshot.restore()
    }

    fun openNewEdit(playerUuid: UUID, worldUuid: UUID, name: String, description: String): TourEditSession {
        val session = TourEditSession(
            playerUuid = playerUuid,
            worldUuid = worldUuid,
            draft = TourData(UUID.randomUUID(), name, description, Material.OAK_BOAT, createdBy = playerUuid),
            originalTourUuid = null
        )
        editSessions[playerUuid] = session
        return session
    }

    fun openExistingEdit(playerUuid: UUID, worldUuid: UUID, source: TourData): TourEditSession {
        val copy = source.copy(
            startSignUuid = source.startSignUuid,
            waypoints = source.waypoints.map {
                TourWaypointData(
                    uuid = it.uuid,
                    name = it.name,
                    blockX = it.blockX,
                    blockY = it.blockY,
                    blockZ = it.blockZ,
                    createdAt = it.createdAt
                )
            }.toMutableList(),
            startedPlayerUuids = source.startedPlayerUuids.toMutableSet()
        )
        val session = TourEditSession(playerUuid, worldUuid, copy, source.uuid)
        editSessions[playerUuid] = session
        return session
    }

    fun getAllSessions(): Collection<ActiveTourSession> = sessions.values.toList()

    fun clearEdit(playerUuid: UUID) {
        editSessions.remove(playerUuid)
    }

    fun clearPlayer(playerUuid: UUID) {
        sessions.remove(playerUuid)
        editSessions.remove(playerUuid)
    }

    fun clearAll() {
        sessions.clear()
        editSessions.clear()
    }
}

data class TourWaypointSnapshot(val uuid: UUID, val name: String, val x: Int, val y: Int, val z: Int, val createdAt: String)
data class TourDraftSnapshot(
    val uuid: UUID, val name: String, val description: String, val icon: Material, val createdBy: UUID?,
    val startSignUuid: UUID?, val waypoints: List<TourWaypointSnapshot>, val completedCount: Int,
    val startedPlayers: Set<UUID>, val activeProgress: Map<UUID, Int>, val createdAt: String,
)
data class TourEditSessionSnapshot(
    val playerUuid: UUID, val worldUuid: UUID, val draft: TourDraftSnapshot, val originalTourUuid: UUID?,
    val awaitingIconPick: Boolean, val awaitingWaypointPick: Boolean,
) {
    fun restore(): TourEditSession = TourEditSession(
        playerUuid, worldUuid,
        TourData(
            draft.uuid, draft.name, draft.description, draft.icon, draft.createdBy, draft.startSignUuid,
            draft.waypoints.map { TourWaypointData(it.uuid, it.name, it.x, it.y, it.z, it.createdAt) }.toMutableList(),
            draft.completedCount, draft.startedPlayers.toMutableSet(), draft.activeProgress.toMutableMap(), draft.createdAt,
        ),
        originalTourUuid, awaitingIconPick, awaitingWaypointPick,
    )
}

private fun TourEditSession.immutableSnapshot(): TourEditSessionSnapshot = TourEditSessionSnapshot(
    playerUuid, worldUuid,
    TourDraftSnapshot(
        draft.uuid, draft.name, draft.description, draft.icon, draft.createdBy, draft.startSignUuid,
        draft.waypoints.map { TourWaypointSnapshot(it.uuid, it.name, it.blockX, it.blockY, it.blockZ, it.createdAt) },
        draft.completedCount, draft.startedPlayerUuids.toSet(), draft.activePlayerProgress.toMap(), draft.createdAt,
    ),
    originalTourUuid, awaitingIconPick, awaitingWaypointPick,
)
