package me.awabi2048.myworldmanager.api.extension

interface WorldRuntimePolicy {
    fun getId(): String

    fun getCreationCost(type: WorldCreationType, configuredCost: Int): Int = configuredCost

    fun getMaxCreateCountDefault(configuredLimit: Int): Int = configuredLimit

    fun getMaxWorldSlotLimit(configuredLimit: Int): Int = configuredLimit

    fun shouldReduceOwnerSlotOnDelete(configured: Boolean): Boolean = configured

    fun getExpansionCost(targetLevel: Int, configuredCost: Int): Int = configuredCost

    fun getEnvironmentCost(type: String, configuredCost: Int): Int = configuredCost

    fun getPortalWorldGatePointCostPerBlock(configuredCost: Int): Int = configuredCost
}

object DefaultWorldRuntimePolicy : WorldRuntimePolicy {
    override fun getId(): String = "myworldmanager.default_runtime"
}
