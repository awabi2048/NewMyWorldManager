package me.awabi2048.myworldmanager.integration

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MultiverseWorldExclusionServiceTest {
    @Test
    fun `標準MyWorld名だけを判定する`() {
        assertTrue(
            MultiverseWorldExclusionService.isDefaultMyWorldName(
                "my_world.5009b655-596b-422f-8599-8f32121391c8"
            )
        )
        assertFalse(MultiverseWorldExclusionService.isDefaultMyWorldName("my_world.invalid"))
        assertFalse(MultiverseWorldExclusionService.isDefaultMyWorldName("arena.5009b655-596b-422f-8599-8f32121391c8"))
    }
}
