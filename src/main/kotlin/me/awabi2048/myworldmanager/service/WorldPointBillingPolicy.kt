package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.api.service.WorldPointBillingMode

internal object WorldPointBillingPolicy {
    fun billableCost(cost: Int, mode: WorldPointBillingMode, economyEnabled: Boolean): Int {
        if (mode == WorldPointBillingMode.NONE || !economyEnabled) return 0
        return cost.coerceAtLeast(0)
    }
}
