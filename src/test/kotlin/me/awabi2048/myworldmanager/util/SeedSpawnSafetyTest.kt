package me.awabi2048.myworldmanager.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SeedSpawnSafetyTest {
    private val requested = SeedSpawnSafety.Position(10, 70, 20)

    @Test
    fun `requested safe position wins`() {
        assertEquals(requested, SeedSpawnSafety.choose(requested, true, listOf(65), emptyList()))
    }

    @Test
    fun `same xz safe y wins before surrounding positions`() {
        assertEquals(
            SeedSpawnSafety.Position(10, 69, 20),
            SeedSpawnSafety.choose(requested, false, listOf(69, 72), listOf(SeedSpawnSafety.Position(11, 70, 20)))
        )
    }

    @Test
    fun `surrounding position is ordered by distance`() {
        assertEquals(
            SeedSpawnSafety.Position(11, 70, 20),
            SeedSpawnSafety.choose(
                requested,
                false,
                emptyList(),
                listOf(SeedSpawnSafety.Position(13, 70, 20), SeedSpawnSafety.Position(11, 70, 20))
            )
        )
    }

    @Test
    fun `no candidates is an explicit failure`() {
        assertNull(SeedSpawnSafety.choose(requested, false, emptyList(), emptyList()))
    }
}
