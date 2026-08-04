package me.awabi2048.myworldmanager

import me.awabi2048.myworldmanager.model.TourData
import me.awabi2048.myworldmanager.model.TourWaypointData
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class TourDataSerializationTest {
    @Test
    fun `waypoint description blank lines and icon survive serialization`() {
        val waypoint = TourWaypointData(
            uuid = UUID.randomUUID(),
            name = "展望台",
            blockX = 10,
            blockY = 65,
            blockZ = -4,
            createdAt = "2026-08-04 00:00:00",
            description = mutableListOf("一行目", "", "三行目"),
            icon = Material.COMPASS,
        )
        val tour = TourData(
            uuid = UUID.randomUUID(),
            name = "景色のツアー",
            description = "説明",
            icon = Material.MAP,
            waypoints = mutableListOf(waypoint),
        )

        @Suppress("UNCHECKED_CAST")
        val restored = TourData.deserialize(tour.serialize() as Map<String, Any>)

        assertEquals(Material.MAP, restored.icon)
        assertEquals(waypoint.description, restored.waypoints.single().description)
        assertEquals(Material.COMPASS, restored.waypoints.single().icon)
    }
}
