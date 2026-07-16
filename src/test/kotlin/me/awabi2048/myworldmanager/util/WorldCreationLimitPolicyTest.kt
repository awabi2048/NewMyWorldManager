package me.awabi2048.myworldmanager.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorldCreationLimitPolicyTest {
    @Test
    fun `total limit is checked before owner limit`() {
        val decision = WorldCreationLimitPolicy.evaluate(false, 10, 2, 10, 3)
        assertEquals(WorldCreationLimitDecision.Reason.TOTAL, decision.reason)
    }

    @Test
    fun `owner limit is checked after total limit`() {
        val decision = WorldCreationLimitPolicy.evaluate(false, 9, 3, 10, 3)
        assertEquals(WorldCreationLimitDecision.Reason.OWNER, decision.reason)
    }
}
