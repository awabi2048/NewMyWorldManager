package me.awabi2048.myworldmanager.model

import java.util.UUID
import me.awabi2048.myworldmanager.util.WorldBorderCoordinates
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.configuration.serialization.ConfigurationSerialization
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WorldBorderCoordinatesSerializationTest {
    private fun world() = WorldData(UUID.randomUUID(), ManagedDimension.OVERWORLD,
        "border-test", "", Material.GRASS_BLOCK, "template:None", "", UUID.randomUUID())

    private fun values(data: WorldData): MutableMap<String, Any> =
        data.serialize().mapNotNull { (key, value) -> value?.let { key to it } }.toMap().toMutableMap()

    private fun record(oldX: Double = 0.5, oldZ: Double = -0.5,
        newX: Double = 250.0, newZ: Double = -250.0) =
        BorderExpansionRecord(0, 1, "NORTH_EAST", oldX, oldZ, 500.0, newX, newZ, 1000.0)

    @Test
    fun `new border and reset center use integer spawn without moving player spawn`() {
        val spawn = Location(null, -12.0, 64.0, 45.0)
        val center = WorldBorderCoordinates.fromSpawn(spawn)
        assertEquals(-12.0, center.x)
        assertEquals(45.0, center.z)
        assertEquals(-12.0, spawn.x)
        assertEquals(45.0, spawn.z)
        val halfSize = 500.0 / 2
        assertEquals(-262.0, center.x - halfSize)
        assertEquals(238.0, center.x + halfSize)
        assertEquals(-205.0, center.z - halfSize)
        assertEquals(295.0, center.z + halfSize)
    }

    @Test
    fun `border coordinates round trip exactly while landing positions remain centered`() {
        var data = world().copy(borderCenterPos = Location(null, -30.25, 64.75, 20.5),
            spawnPosMember = Location(null, -30.5, 64.0, 20.5))
        repeat(5) {
            data = WorldData.deserialize(values(data))
            assertEquals(-30.25, data.borderCenterPos!!.x)
            assertEquals(20.5, data.borderCenterPos!!.z)
            assertEquals(64.75, data.borderCenterPos!!.y)
            assertEquals(-30.5, data.spawnPosMember!!.x)
            assertEquals(20.5, data.spawnPosMember!!.z)
        }
    }

    @Test
    fun `legacy border map loses only former southeast half block offset`() {
        val old = values(world())
        old.remove("border_center_coordinates_version")
        old["border_center_pos"] = mapOf("x" to -251, "y" to 64, "z" to 250)
        val restored = WorldData.deserialize(old)
        assertEquals(-251.0, restored.borderCenterPos!!.x)
        assertEquals(250.0, restored.borderCenterPos!!.z)
        val again = WorldData.deserialize(values(restored))
        assertEquals(-251.0, again.borderCenterPos!!.x)
        assertEquals(250.0, again.borderCenterPos!!.z)
    }

    @Test
    fun `legacy undo history corrects half centers but leaves rounded integer expansion centers`() {
        val old = values(world().copy(borderExpansionLevel = 1))
        old.remove("border_center_coordinates_version")
        old["border_expansion_history"] = listOf(record().serialize())
        val restored = WorldData.deserialize(old)
        val entry = restored.latestBorderExpansionRecord()!!
        assertEquals(0.0, entry.oldCenterX)
        assertEquals(-1.0, entry.oldCenterZ)
        assertEquals(250.0, entry.newCenterX)
        assertEquals(-250.0, entry.newCenterZ)
        assertEquals(500.0, entry.oldSize)
        assertEquals(1000.0, entry.newSize)
        repeat(3) {
            assertEquals(entry, WorldData.deserialize(values(restored)).latestBorderExpansionRecord())
        }
    }

    @Test
    fun `new fractional expansion history and undo centers stay exact`() {
        val entry = record(-0.5, 20.25, 250.5, -230.25)
        val data = world().copy(borderExpansionLevel = 1, borderExpansionHistory = mutableListOf(entry))
        val restored = WorldData.deserialize(values(data))
        assertEquals(entry, restored.latestBorderExpansionRecord())
    }

    @Test
    fun `directional expansion and preview do not round toward southeast`() {
        for (x in listOf(-0.5, 0.0, 0.5, 0.25)) {
            for (dx in listOf(-250.0, 250.0)) {
                for (dz in listOf(-250.0, 250.0)) {
                    val original = Location(null, x, 64.0, x)
                    val shifted = WorldBorderCoordinates.shift(original, dx, dz)
                    assertEquals(x + dx, shifted.x)
                    assertEquals(x + dz, shifted.z)
                    assertEquals(x, original.x)
                    assertEquals(x, original.z)
                }
            }
        }
    }

    @Test
    fun `actual YAML save reload preserves precise border and legacy conversion marker`() {
        ConfigurationSerialization.registerClass(WorldData::class.java)
        val original = world().copy(borderCenterPos = Location(null, -250.25, 64.0, 250.5))
        var text = YamlConfiguration().also { it.set("world_data", original) }.saveToString()
        repeat(3) {
            val loaded = YamlConfiguration().also { it.loadFromString(text) }
            val restored = loaded.get("world_data") as WorldData
            assertEquals(-250.25, restored.borderCenterPos!!.x)
            assertEquals(250.5, restored.borderCenterPos!!.z)
            assertEquals(1, restored.serialize()["border_center_coordinates_version"])
            text = YamlConfiguration().also { it.set("world_data", restored) }.saveToString()
        }
    }

    @Test
    fun `unrecognized border coordinate format is rejected`() {
        val map = values(world())
        for (invalid in listOf(2, 1.5, "invalid")) {
            map["border_center_coordinates_version"] = invalid
            assertThrows(IllegalArgumentException::class.java) { WorldData.deserialize(map) }
        }
    }
}
