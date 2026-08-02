package me.awabi2048.myworldmanager.gui

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * テンプレート一覧のJE/BE導線を、UI実機なしでも明示的に検証します。
 */
class CreationTemplateFlowContractTest {
    private val source = Path.of(
        "src/main/kotlin/me/awabi2048/myworldmanager/gui/CreationGui.kt",
    ).readText()

    @Test
    fun `java template list has direct left use and right preview actions`() {
        val render = source.substringAfter("private fun renderTemplateSelection")
            .substringBefore("fun openTemplateDetail")

        assertTrue(render.contains("val isDialogMode = creationSession?.isDialogMode == true"))
        assertTrue(render.contains("ACTION_SELECT_TEMPLATE"))
        assertTrue(render.contains("MenuGesture.LEFT"))
        assertTrue(render.contains("ACTION_PREVIEW_TEMPLATE"))
        assertTrue(render.contains("MenuGesture.RIGHT"))
    }

    @Test
    fun `bedrock template list retains detail navigation`() {
        val render = source.substringAfter("private fun renderTemplateSelection")
            .substringBefore("fun openTemplateDetail")
        val handler = source.substringAfter("private fun selectTemplate")
            .substringBefore("private fun templateListBack")

        assertTrue(render.contains("MenuGesture.LEFT_RIGHT"))
        assertTrue(handler.contains("if (session.isDialogMode)"))
        assertTrue(handler.contains("WorldCreationPhase.NAME_INPUT"))
        assertTrue(handler.contains("WorldCreationPhase.TEMPLATE_DETAIL"))
    }
}
