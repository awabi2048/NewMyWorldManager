package me.awabi2048.myworldmanager.util

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorldExportNameSafetyTest {
    @Test
    fun `出力ファイル名に使えない文字をワールド名入力時に拒否する`() {
        listOf("a/b", "a\\b", "a:b", "a*b", "a?b", "a\"b", "a<b", "a>b", "a|b", "a\nb").forEach {
            assertTrue(WorldNamePolicy.containsFileNameForbiddenCharacter(it), it)
        }
    }
}
