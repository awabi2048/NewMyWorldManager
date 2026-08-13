package me.awabi2048.myworldmanager.repository

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

/** templates.yml全体移行で未解決セクションを部分保存しない契約を固定します。 */
class TemplateForceMigrationContractTest {
    @Test
    fun `whole file migration returns unresolved before backup when a dimension is missing`() {
        val source = Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/repository/TemplateRepository.kt"
        ).readText()

        val unresolvedCheck = source.indexOf("if (unresolved.isNotEmpty())")
        val backupCreation = source.indexOf("val backup = File", unresolvedCheck)
        assertTrue(unresolvedCheck >= 0)
        assertTrue(backupCreation > unresolvedCheck, "未解決判定はバックアップ・保存処理より前である必要があります")
    }
}
