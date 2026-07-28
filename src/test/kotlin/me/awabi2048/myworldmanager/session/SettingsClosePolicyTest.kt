package me.awabi2048.myworldmanager.session

import com.awabi2048.ccsystem.api.gui.MenuCloseReason
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsClosePolicyTest {
    @Test
    fun `route replacement preserves settings session`() {
        assertTrue(SettingsClosePolicy.shouldPreserveSession(MenuCloseReason.ROUTE_REPLACED))
    }

    @Test
    fun `user dismissal ends settings session`() {
        assertFalse(SettingsClosePolicy.shouldPreserveSession(MenuCloseReason.USER_DISMISSED))
    }

    @Test
    fun `runtime close ends settings session`() {
        assertFalse(SettingsClosePolicy.shouldPreserveSession(MenuCloseReason.RUNTIME_CLOSED))
    }
}
