package me.awabi2048.myworldmanager.model

import org.bukkit.World
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ManagedDimensionTest {
    @Test
    fun `Bukkit環境を対応する管理次元へ変換する`() {
        assertEquals(ManagedDimension.OVERWORLD, ManagedDimension.fromBukkit(World.Environment.NORMAL))
        assertEquals(ManagedDimension.NETHER, ManagedDimension.fromBukkit(World.Environment.NETHER))
        assertEquals(ManagedDimension.END, ManagedDimension.fromBukkit(World.Environment.THE_END))
    }

    @Test
    fun `永続値は完全一致する既知の次元だけを受理する`() {
        assertEquals(ManagedDimension.END, ManagedDimension.parse("END"))
        assertThrows(IllegalArgumentException::class.java) { ManagedDimension.parse("NORMAL") }
    }
}
