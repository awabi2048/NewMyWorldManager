package me.awabi2048.myworldmanager.architecture

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorldOperationSafetyContractTest {
    private val source = Path.of(
        "src/main/kotlin/me/awabi2048/myworldmanager/service/WorldService.kt",
    ).readText()

    @Test
    fun `削除はメタデータ確定前にワールドを隔離し失敗時に復元する`() {
        val body = functionBody("deleteWorld")
        assertTrue(body.indexOf("quarantineWorldDirectory") < body.indexOf("repository.delete"))
        assertTrue("restoreQuarantinedWorldDirectory" in body)
        assertFalse("folder.deleteRecursively()" in body)
    }

    @Test
    fun `アーカイブは保存失敗時に元ディレクトリへ戻す`() {
        val body = functionBody("archiveWorld")
        assertTrue("sourceFile.renameTo(targetFile)" in body)
        assertTrue("targetFile.renameTo(sourceFile)" in body)
        assertTrue("whenComplete" in body)
    }

    private fun functionBody(name: String): String {
        val start = source.indexOf("fun $name")
        require(start >= 0) { "$name が見つかりません" }
        val bodyStart = source.indexOf('{', start)
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
}
