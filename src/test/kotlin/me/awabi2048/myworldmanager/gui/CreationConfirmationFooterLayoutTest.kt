package me.awabi2048.myworldmanager.gui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

/** テンプレート作成確認画面のフッター補助アイコン配置を固定します。 */
class CreationConfirmationFooterLayoutTest {
    private val source = Path.of(
        "src/main/kotlin/me/awabi2048/myworldmanager/gui/CreationGui.kt",
    ).readText()

    @Test
    fun `template confirmation does not render footer 3 4 or 6 helper icons`() {
        val templateBranch = source
            .substringAfter("} else if (session.creationType == WorldCreationType.TEMPLATE) {")
            .substringBefore("} else {")

        assertFalse(templateBranch.contains("interactionEntry("))
        assertTrue(templateBranch.contains("confirmationCapability?.let"))
    }
}
