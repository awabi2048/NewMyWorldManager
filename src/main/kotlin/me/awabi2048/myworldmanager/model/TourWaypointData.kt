package me.awabi2048.myworldmanager.model

import java.util.UUID

data class TourWaypointData(
    val uuid: UUID,
    var name: String,
    var blockX: Int,
    var blockY: Int,
    var blockZ: Int,
    val createdAt: String
)
