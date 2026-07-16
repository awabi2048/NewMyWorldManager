package me.awabi2048.myworldmanager.util

import org.bukkit.command.CommandSender

data class WorldCreationLimitDecision(val allowed: Boolean, val reason: Reason? = null) {
    enum class Reason { TOTAL, OWNER }
}

/** 全経路で共有する作成・移譲上限の判定。 */
object WorldCreationLimitPolicy {
    fun evaluate(actor: CommandSender, totalCount: Int, ownerCount: Int, totalLimit: Int, ownerLimit: Int): WorldCreationLimitDecision {
        return evaluate(PermissionManager.canBypassWorldLimits(actor), totalCount, ownerCount, totalLimit, ownerLimit)
    }

    fun evaluate(bypassLimits: Boolean, totalCount: Int, ownerCount: Int, totalLimit: Int, ownerLimit: Int): WorldCreationLimitDecision {
        if (bypassLimits) return WorldCreationLimitDecision(true)
        if (totalCount >= totalLimit) return WorldCreationLimitDecision(false, WorldCreationLimitDecision.Reason.TOTAL)
        if (ownerCount >= ownerLimit) return WorldCreationLimitDecision(false, WorldCreationLimitDecision.Reason.OWNER)
        return WorldCreationLimitDecision(true)
    }
}
