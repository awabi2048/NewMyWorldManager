package me.awabi2048.myworldmanager.architecture

import com.awabi2048.ccsystem.api.CCSystemAPI
import me.awabi2048.myworldmanager.MyWorldManager
import org.junit.jupiter.api.Assertions.assertEquals
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
        assertTrue(source.contains("actualVersion != REQUIRED_GUI_RUNTIME_CONTRACT_VERSION"))
        assertTrue(source.contains(
            "REQUIRED_GUI_RUNTIME_CONTRACT_VERSION = CCSystemAPI.GUI_RUNTIME_CONTRACT_VERSION",
        ))
        assertEquals(CCSystemAPI.GUI_RUNTIME_CONTRACT_VERSION, requiredRuntimeContractFromBytecode())
        assertTrue(source.contains("server.pluginManager.disablePlugin(this)"))
    }

    @Test
    fun `missing runtime contract getter disables plugin before initialization`() {
        val source = Files.readString(Path.of("src/main/kotlin/me/awabi2048/myworldmanager/MyWorldManager.kt"))

        val enableStart = source.indexOf("override fun onEnable()")
        val guardedCheck = source.indexOf("if (!ensureCCSystemAvailable()) return", enableStart)
        val firstInitialization = source.indexOf("ConfigurationSerialization.registerClass", enableStart)
        assertTrue(guardedCheck in (enableStart + 1) until firstInitialization)
        assertTrue(source.contains("CCSystem.getAPI().guiRuntimeContractVersion"))
        assertTrue(source.contains("catch (failure: LinkageError)"))
        assertTrue(source.contains("catch (failure: RuntimeException)"))
        assertTrue(source.contains("return disableForGuiRuntimeContractFailure(failure)"))
        assertTrue(source.contains("GUI runtime契約の取得に失敗したため MyWorldManager を無効化します"))
    }

    private fun requiredRuntimeContractFromBytecode(): Int = MyWorldManager::class.java
        .getDeclaredField("REQUIRED_GUI_RUNTIME_CONTRACT_VERSION")
        .also { it.isAccessible = true }
        .getInt(null)
}
