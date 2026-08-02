package me.awabi2048.myworldmanager.session

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * プレビューの状態遷移を、Bukkit実機なしでも崩さないためのソース契約です。
 * 実行時のCraftPlayer実装に依存する箇所は、開始・回転・終了の順序を静的に固定します。
 */
class PreviewSessionManagerContractTest {
    private val source = Path.of(
        "src/main/kotlin/me/awabi2048/myworldmanager/session/PreviewSessionManager.kt",
    ).readText()

    @Test
    fun `preview enters spectator before and after teleport`() {
        val start = source.substringAfter("session.previewLocation = viewLocation")
            .substringBefore("private fun setWorldWeather")
        val teleportIndex = start.indexOf("player.teleport(viewLocation)")
        val spectatorAssignments = buildList {
            var index = start.indexOf("player.gameMode = GameMode.SPECTATOR")
            while (index >= 0) {
                add(index)
                index = start.indexOf("player.gameMode = GameMode.SPECTATOR", index + 1)
            }
        }

        assertTrue(teleportIndex >= 0, "プレビュー開始時のテレポートが見つかりません")
        assertTrue(spectatorAssignments.size >= 2, "テレポート前後のモード保証が必要です")
        assertTrue(spectatorAssignments.first() < teleportIndex)
        assertTrue(spectatorAssignments[1] > teleportIndex)
    }

    @Test
    fun `rotation reasserts spectator and end clears target only in spectator`() {
        val rotation = source.substringAfter("private fun startRotationTask")
            .substringBefore("fun handlePlayerQuit")
        val end = source.substringAfter("fun endPreview")
            .substringBefore("fun handlePlayerJoin")

        assertTrue(rotation.contains("if (player.gameMode != GameMode.SPECTATOR)"))
        assertTrue(end.contains("if (player.gameMode == GameMode.SPECTATOR)"))
        assertTrue(end.indexOf("player.spectatorTarget = null") > end.indexOf("if (player.gameMode == GameMode.SPECTATOR)"))
    }
}
