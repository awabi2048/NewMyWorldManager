package me.awabi2048.myworldmanager.api

import org.bukkit.GameMode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlightPermissionDecisionTest {
    @Test
    fun `局所要求はワールドと通常ポリシーに優先する`() {
        assertTrue(resolveFlightPermission(true, GameMode.ADVENTURE, false) { false })
        assertFalse(resolveFlightPermission(false, GameMode.CREATIVE, true) { true })
        assertFalse(resolveFlightPermission(null, GameMode.ADVENTURE, false) { true })
        assertFalse(resolveFlightPermission(null, GameMode.SURVIVAL, true) { false })
        assertTrue(resolveFlightPermission(null, GameMode.SURVIVAL, true) { true })
    }
}
