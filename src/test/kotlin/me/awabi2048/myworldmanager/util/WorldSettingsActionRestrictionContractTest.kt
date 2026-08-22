package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiSettingsKeys
import me.awabi2048.myworldmanager.api.extension.WorldSettingsAction
import me.awabi2048.myworldmanager.api.extension.WorldSettingsActionContract
import me.awabi2048.myworldmanager.api.extension.WorldSettingsActionOption
import me.awabi2048.myworldmanager.api.extension.WorldSettingsActionRestriction
import com.awabi2048.ccsystem.api.gui.MenuGesture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 操作契約の制約理由(restriction)に関する不変条件と、理由→警告キー写像の一貫性を検証します。
 * 「実行可能なのに理由を持つ」矛盾した契約が生成できないことを保証し、GUI側の警告表示の信頼性を支えます。
 */
class WorldSettingsActionRestrictionContractTest {
    @Test
    fun `actionable contract must not carry a restriction reason`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            WorldSettingsActionContract(
                action = WorldSettingsAction.MANAGE_TOUR,
                options = listOf(WorldSettingsActionOption(MenuGesture.ANY)),
                actionable = true,
                restriction = WorldSettingsActionRestriction.NOT_IN_TARGET_WORLD,
            )
        }
        assertTrue(exception.message!!.contains("MANAGE_TOUR"))
    }

    @Test
    fun `non actionable contract may omit restriction for dynamic reasons`() {
        // 入場権限など動的な理由は静的な型では表現できないため、restriction=null の非実行可能契約は有効です。
        val contract = WorldSettingsActionContract(
            action = WorldSettingsAction.WARP,
            options = listOf(WorldSettingsActionOption(MenuGesture.ANY)),
            actionable = false,
            restriction = null,
        )
        assertEquals(false, contract.actionable)
        assertEquals(null, contract.restriction)
    }

    @Test
    fun `non actionable contract may carry a restriction reason`() {
        val contract = WorldSettingsActionContract(
            action = WorldSettingsAction.SET_SPAWN,
            options = listOf(WorldSettingsActionOption(MenuGesture.LEFT), WorldSettingsActionOption(MenuGesture.RIGHT)),
            actionable = false,
            restriction = WorldSettingsActionRestriction.NOT_IN_TARGET_WORLD,
        )
        assertEquals(WorldSettingsActionRestriction.NOT_IN_TARGET_WORLD, contract.restriction)
    }

    @Test
    fun `every restriction reason maps to a warning key`() {
        // 理由が追加されたときに写像漏れが起きないよう、全列挙値のキー解決を検証します。
        WorldSettingsActionRestriction.entries.forEach { restriction ->
            assertEquals(MyworldGuiSettingsKeys.GUI_SETTINGS_COMMON_MUST_BE_IN_WORLD, WorldSettingsRestrictionMessages.warningKey(restriction))
        }
    }
}
