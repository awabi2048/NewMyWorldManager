package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.api.service.WorldPointBillingMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorldPointBillingPolicyTest {
    @Test
    fun billsOnlyStandardModeWhenEconomyIsEnabled() {
        assertEquals(10, WorldPointBillingPolicy.billableCost(10, WorldPointBillingMode.STANDARD, true))
        assertEquals(0, WorldPointBillingPolicy.billableCost(10, WorldPointBillingMode.NONE, true))
        assertEquals(0, WorldPointBillingPolicy.billableCost(10, WorldPointBillingMode.STANDARD, false))
        assertEquals(0, WorldPointBillingPolicy.billableCost(-10, WorldPointBillingMode.STANDARD, true))
    }
}
