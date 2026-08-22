package me.awabi2048.myworldmanager.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * /mwm migration status の行圧縮表示（状態色● + 識別子 + 短縮コード、詳細はホバー）を固定します。
 * 行数が増えても1行で状態を俯瞰でき、詳細はホバーで確認できることを契約とします。
 */
class MigrationStatusOutputContractTest {
    private val migrationSource: String =
        Files.readString(Path.of("src/main/kotlin/me/awabi2048/myworldmanager/migration/WorldMigrationService.kt"))

    @Test
    fun `status uses compact per-line rendering with hover detail`() {
        assertTrue(migrationSource.contains("fun status(sender: CommandSender)"))
        assertTrue(migrationSource.contains("sendStatusLine("))
        assertTrue(migrationSource.contains("HoverEvent.showText(hover)"))
        assertTrue(migrationSource.contains("fun statusHover("))
        // 詳細行テンプレート（status_world）は1行ごとの送信でなく、ホバー表示の生成元としてだけ使う
        assertTrue(migrationSource.contains("MyworldMessagesKeys.MESSAGES_MIGRATION_STATUS_WORLD,"))
    }

    @Test
    fun `every world migration status maps to a colored short code`() {
        val presentation = migrationSource.substringAfter("private fun MigrationWorldStatus.presentation()")
            .substringBefore("private companion object")
        listOf(
            "MigrationWorldStatus.WAITING" to "MESSAGES_MIGRATION_STATUS_SHORT_WAITING",
            "MigrationWorldStatus.RUNNING" to "MESSAGES_MIGRATION_STATUS_SHORT_RUNNING",
            "MigrationWorldStatus.RETRY" to "MESSAGES_MIGRATION_STATUS_SHORT_RETRY",
            "MigrationWorldStatus.COMPLETED" to "MESSAGES_MIGRATION_STATUS_SHORT_COMPLETED",
            "MigrationWorldStatus.FAILED" to "MESSAGES_MIGRATION_STATUS_SHORT_FAILED",
        ).forEach { (status, key) ->
            assertTrue(
                presentation.contains(status) && presentation.contains(key),
                "presentation に $status -> $key の対応が必要",
            )
        }
    }
}
