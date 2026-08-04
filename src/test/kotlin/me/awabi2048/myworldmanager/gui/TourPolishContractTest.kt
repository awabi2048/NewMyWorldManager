package me.awabi2048.myworldmanager.gui

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ツアー画面の今回の変更が、以前の履歴・入力・選択経路へ戻らないことを確認します。
 * 画面の見た目だけでなく、操作を受け付ける意味型とセッション処理の境界を固定します。
 */
class TourPolishContractTest {
    private val guiRoot = Path.of("src/main/kotlin/me/awabi2048/myworldmanager/gui")
    private val listenerRoot = Path.of("src/main/kotlin/me/awabi2048/myworldmanager/listener")
    private val serviceRoot = Path.of("src/main/kotlin/me/awabi2048/myworldmanager/service")

    @Test
    fun `visitor switch replaces the current tour screen history entry`() {
        val source = guiRoot.resolve("TourGui.kt").readText()
        val body = functionBody(source, "openVisitorFromEdit")

        assertTrue("MenuUpdate.Replace" in body)
        assertFalse("MenuUpdate.Navigate" in body)
    }

    @Test
    fun `tour editing separates text and icon actions and keeps the switch name only`() {
        val source = guiRoot.resolve("TourGui.kt").readText()

        assertTrue("GuiLoreSpec.NameOnly" in source)
        assertTrue("ACTION_EDIT_ICON" in source)
        assertTrue("layout.actionSlot - 3" in source)
        assertTrue("layout.actionSlot - 2" in source)
        assertFalse("MenuGesture.PLAIN_RIGHT" in source)
    }

    @Test
    fun `tour creation reopens the dialog with an inline required-name error`() {
        val source = guiRoot.resolve("TourDialogManager.kt").readText()

        assertTrue("create_dialog.name_required" in source)
        assertTrue("showCreateTourDialog(" in source)
        assertTrue("MenuActionResult.Ignored" in source)
    }

    @Test
    fun `position picking shares passable block handling and tour stop clears actionbar`() {
        val listener = listenerRoot.resolve("TourListener.kt").readText()
        val worldSettingsListener = listenerRoot.resolve("WorldSettingsListener.kt").readText()
        val spawnPreview = serviceRoot.resolve("WorldSettingsSpawnPreviewService.kt").readText()
        val targetResolver = Path.of("src/main/kotlin/me/awabi2048/myworldmanager/util/PlayerBlockTargetResolver.kt").readText()
        val manager = serviceRoot.resolve("TourManager.kt").readText()

        assertTrue("rayTraceBlocks" in targetResolver)
        assertTrue("FluidCollisionMode.NEVER" in targetResolver)
        assertTrue("PlayerBlockTargetResolver.find(player)" in listener)
        assertTrue("PlayerBlockTargetResolver.find(player)" in spawnPreview)
        assertTrue("PlayerBlockTargetResolver.find(player)" in worldSettingsListener)
        assertTrue("true," in targetResolver)
        assertTrue("clearNavigationActionbar" in manager)
        assertTrue("player.sendActionBar(Component.empty())" in manager)
    }

    private fun functionBody(source: String, functionName: String): String {
        val start = source.indexOf("fun $functionName")
        require(start >= 0) { "function not found: $functionName" }
        val bodyStart = source.indexOf('{', start)
        require(bodyStart >= 0) { "function body not found: $functionName" }
        var depth = 0
        for (index in bodyStart until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(bodyStart, index + 1)
                }
            }
        }
        error("unterminated function: $functionName")
    }
}
