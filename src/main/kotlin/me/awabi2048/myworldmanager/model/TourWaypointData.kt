package me.awabi2048.myworldmanager.model

import org.bukkit.Material
import java.util.UUID

data class TourWaypointData(
    val uuid: UUID,
    var name: String,
    var blockX: Int,
    var blockY: Int,
    var blockZ: Int,
    val createdAt: String,
    var description: MutableList<String> = mutableListOf(),
    var icon: Material = Material.OAK_BOAT,
)
