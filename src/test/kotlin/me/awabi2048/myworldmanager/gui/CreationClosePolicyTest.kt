package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.gui.MenuCloseReason
import me.awabi2048.myworldmanager.session.WorldCreationPhase
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CreationClosePolicyTest {
    @Test
    fun `正規の画面遷移では作成セッションを維持する`() {
        WorldCreationPhase.entries.forEach { phase ->
            assertTrue(shouldPreserveCreationSessionOnClose(MenuCloseReason.ROUTE_REPLACED, phase))
        }
    }

    @Test
    fun `作成GUIの手動クローズではセッションを終了する`() {
        val inventoryPhases = setOf(
            WorldCreationPhase.TYPE_SELECT,
            WorldCreationPhase.TEMPLATE_SELECT,
            WorldCreationPhase.TEMPLATE_DETAIL,
            WorldCreationPhase.CONFIRM,
        )

        inventoryPhases.forEach { phase ->
            assertFalse(shouldPreserveCreationSessionOnClose(MenuCloseReason.USER_DISMISSED, phase))
        }
    }

    @Test
    fun `外部入力開始に伴うクローズでは作成セッションを維持する`() {
        val inputPhases = setOf(
            WorldCreationPhase.SEED_INPUT,
            WorldCreationPhase.NAME_INPUT,
            WorldCreationPhase.SPAWN_INPUT,
        )

        inputPhases.forEach { phase ->
            assertTrue(shouldPreserveCreationSessionOnClose(MenuCloseReason.USER_DISMISSED, phase))
        }
    }
}
