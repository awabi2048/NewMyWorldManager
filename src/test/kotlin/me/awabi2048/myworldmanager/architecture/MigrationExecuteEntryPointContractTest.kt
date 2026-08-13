package me.awabi2048.myworldmanager.architecture

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText

/** 移行書込みを /mwm migration execute 以外から開始できない契約を固定します。 */
class MigrationExecuteEntryPointContractTest {
    private val sourceRoot = Path.of("src/main/kotlin/me/awabi2048/myworldmanager")

    @Test
    fun `migration command exposes only execute and status`() {
        val command = sourceRoot.resolve("command/WorldCommand.kt").readText()

        assertFalse(command.contains("set-dimension"))
        assertFalse(command.contains("requestSetDimension"))
        assertTrue(command.contains("listOf(\"execute\", \"status\")"))
        assertTrue(command.contains("MigrationExecuteOptions.parse(args.drop(2))"))
    }

    @Test
    fun `force default is supplied only by central execute coordinator`() {
        val migration = sourceRoot.resolve("migration/WorldMigrationService.kt").readText()
        val repositories = listOf(
            sourceRoot.resolve("repository/WorldConfigRepository.kt"),
            sourceRoot.resolve("repository/TemplateRepository.kt"),
        ).joinToString("\n") { it.readText() }

        assertTrue(migration.contains("ManagedDimension.OVERWORLD.takeIf { force }"))
        assertTrue(migration.contains("if (force && !confirmed)"))
        assertFalse(repositories.contains("forceDimension"))
        assertFalse(repositories.contains("ManagedDimension.OVERWORLD.takeIf { force }"))
    }

    @Test
    fun `repository migration writes are called only by migration service`() {
        val offenders = mutableListOf<String>()
        Files.walk(sourceRoot).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.extension == "kt" }
                .forEach { source ->
                    val relative = sourceRoot.relativize(source).toString().replace('\\', '/')
                    if (relative == "migration/WorldMigrationService.kt" ||
                        relative == "repository/WorldConfigRepository.kt" ||
                        relative == "repository/TemplateRepository.kt"
                    ) return@forEach
                    val content = source.readText()
                    if (content.contains("migrateWorldData(") || content.contains("migrateTemplate(")) {
                        offenders += relative
                    }
                }
        }
        assertTrue(offenders.isEmpty(), "移行サービス外からの書込み呼出: $offenders")
    }
}
