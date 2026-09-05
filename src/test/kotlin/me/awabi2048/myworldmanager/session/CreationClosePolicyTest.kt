package me.awabi2048.myworldmanager.session

import com.awabi2048.ccsystem.api.gui.MenuCloseReason
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CreationClosePolicyTest {
    @Test
    fun `route replacement preserves every creation phase`() {
        WorldCreationPhase.entries.forEach { phase ->
            assertTrue(CreationClosePolicy.shouldPreserveSession(MenuCloseReason.ROUTE_REPLACED, phase))
        }
    }

    @Test
    fun `manual close ends inventory creation phases`() {
        setOf(
            WorldCreationPhase.TYPE_SELECT,
            WorldCreationPhase.TEMPLATE_SELECT,
            WorldCreationPhase.TEMPLATE_DETAIL,
            WorldCreationPhase.CONFIRM,
        ).forEach { phase ->
            assertFalse(CreationClosePolicy.shouldPreserveSession(MenuCloseReason.USER_DISMISSED, phase))
        }
    }

    @Test
    fun `external input phases survive a manual inventory close`() {
        setOf(
            WorldCreationPhase.SEED_INPUT,
            WorldCreationPhase.NAME_INPUT,
            WorldCreationPhase.SPAWN_INPUT,
        ).forEach { phase ->
            assertTrue(CreationClosePolicy.shouldPreserveSession(MenuCloseReason.USER_DISMISSED, phase))
        }
    }
}
