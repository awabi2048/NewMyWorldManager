package me.awabi2048.myworldmanager.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ReversiblePlanCleanupLifecycleTest {
    @Test
    fun `plugin lifecycle schedules purges and clears plans on quit and disable`() {
        val source = Files.readString(Path.of("src/main/kotlin/me/awabi2048/myworldmanager/MyWorldManager.kt"))

        assertTrue(source.contains("REVERSIBLE_PLAN_CLEANUP_INTERVAL_TICKS = 20L * 60L"))
        assertTrue(source.contains("worldPublishService.purgeExpiredReversiblePlans(now)"))
        assertTrue(source.contains("creationSessionManager.purgeExpiredReversiblePlans(now)"))
        assertTrue(source.contains("reversiblePlanCleanupTask?.cancel()"))
        assertTrue(source.contains("worldPublishService.removeReversiblePlans(playerUuid)"))
        assertTrue(source.contains("creationSessionManager.removeReversiblePlans(playerUuid)"))
        assertTrue(source.contains("worldPublishService.clearReversiblePlans()"))
        assertTrue(source.contains("creationSessionManager.clearAll()"))
        val enableStart = source.indexOf("override fun onEnable()")
        val contractCheck = source.indexOf("if (!ensureCCSystemAvailable()) return", enableStart)
        val providerRegistration = source.indexOf("MwmReversibleStateProviders(this).register", enableStart)
        assertTrue(contractCheck > enableStart)
        assertTrue(providerRegistration > contractCheck)
        assertTrue(source.contains("api.guiRuntimeContractVersion != CCSystemAPI.GUI_RUNTIME_CONTRACT_VERSION"))
        assertTrue(source.contains("server.pluginManager.disablePlugin(this)"))
    }
}
