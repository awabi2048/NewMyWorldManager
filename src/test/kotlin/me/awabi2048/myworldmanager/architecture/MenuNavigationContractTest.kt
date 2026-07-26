package me.awabi2048.myworldmanager.architecture

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MenuNavigationContractTest {
    private val guiRoot = Path.of("src/main/kotlin/me/awabi2048/myworldmanager/gui")

    @Test
    fun `履歴を持つ子画面の戻る操作はRuntimeのBackへ委譲する`() {
        BACK_SCREENS.forEach { fileName ->
            val source = guiRoot.resolve(fileName).readText()
            val body = functionBody(source, "back")
            assertTrue(
                "MenuUpdate.Back" in body,
                "$fileName の戻る操作がRuntimeの履歴へ委譲されていません",
            )
            assertFalse(
                "MenuUpdate.Close" in body,
                "$fileName の戻る操作で履歴を破棄するCloseを返しています",
            )
        }
    }

    @Test
    fun `申請項目から確認画面を開いた後に新しい画面を閉じない`() {
        val source = guiRoot.resolve("PendingInteractionGui.kt").readText()
        val body = functionBody(source, "openEntry")
        assertTrue("MenuUpdate.None" in body)
        assertFalse("MenuUpdate.Close" in body)
    }

    @Test
    fun `単独起動可能な一覧は戻る表示時だけ履歴へ積む`() {
        listOf("InviteGui.kt", "MeetGui.kt", "VisitWorldGui.kt").forEach { fileName ->
            val source = guiRoot.resolve(fileName).readText()
            assertTrue(
                "runtime.navigate(player, route)" in source,
                "$fileName に親画面からのNavigate経路がありません",
            )
            assertTrue(
                "runtime.open(player, route)" in source,
                "$fileName に単独起動用のRoot経路がありません",
            )
        }
    }

    private fun functionBody(source: String, name: String): String {
        val start = source.indexOf("private fun $name")
        require(start >= 0) { "$name が見つかりません" }
        val bodyStart = source.indexOf('{', start)
        require(bodyStart >= 0) { "$name の本体が見つかりません" }
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
        error("$name の本体が閉じられていません")
    }

    private companion object {
        val BACK_SCREENS = listOf(
            "AdminPortalGui.kt",
            "EnvironmentGui.kt",
            "FavoriteGui.kt",
            "InviteGui.kt",
            "MeetGui.kt",
            "PendingInteractionGui.kt",
            "VisitWorldGui.kt",
        )
    }
}
