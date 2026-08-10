package me.awabi2048.myworldmanager.model

import java.time.LocalDate
import java.util.UUID
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class WorldDataDimensionSerializationTest {
    @Test
    fun `全管理次元を往復できる`() {
        ManagedDimension.entries.forEach { dimension ->
            val original = world(dimension)
            val serialized = original.serialize().mapNotNull { (key, value) ->
                value?.let { key to it }
            }.toMap()
            val restored = WorldData.deserialize(serialized)
            assertEquals(dimension, restored.dimension)
        }
    }

    @Test
    fun `次元が欠落した永続データは拒否する`() {
        val serialized = world(ManagedDimension.OVERWORLD).serialize().toMutableMap()
        serialized.remove("dimension")

        assertThrows(IllegalArgumentException::class.java) {
            @Suppress("UNCHECKED_CAST")
            WorldData.deserialize(serialized.filterValues { it != null } as Map<String, Any>)
        }
    }

    private fun world(dimension: ManagedDimension) = WorldData(
        uuid = UUID.randomUUID(),
        dimension = dimension,
        name = "test",
        description = "test",
        icon = Material.GRASS_BLOCK,
        sourceWorld = "template:None",
        expireDate = LocalDate.now().plusDays(1).toString(),
        owner = UUID.randomUUID(),
        cumulativePoints = 0,
    )
}
