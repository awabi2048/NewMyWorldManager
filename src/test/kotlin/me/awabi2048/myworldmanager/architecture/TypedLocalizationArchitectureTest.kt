package me.awabi2048.myworldmanager.architecture

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText

class TypedLocalizationArchitectureTest {
    private val sourceRoot = Path.of("src/main/kotlin")

    @Test
    fun `localization consumers cannot recreate raw key APIs`() {
        val sources = kotlinSources()

        sources.forEach { source ->
            val text = source.readText()
            assertFalse(
                "LocalizationKey.text(" in text || "LocalizationKey.textList(" in text,
                "生成コード外でキーを手生成してはいけません: $source",
            )
        }

        val languageManager = sourceRoot
            .resolve("me/awabi2048/myworldmanager/util/LanguageManager.kt")
            .readText()
        assertFalse(Regex("key\\s*:\\s*String").containsMatchIn(languageManager))
        assertFalse("hasI18nKey(" in languageManager)
        assertFalse("isI18nKeyMatch(" in languageManager)
        assertFalse("isI18nKeyStartWith(" in languageManager)
    }

    @Test
    fun `runtime resolver is isolated to explicit external identifier boundary`() {
        val resolverUsers = kotlinSources()
            .filter { "LocalizationCatalogContract" in it.readText() }
            .map { it.name }

        assertTrue(
            resolverUsers == listOf("CatalogKeyResolver.kt"),
            "実行時キー解決を表示処理へ持ち込んではいけません: $resolverUsers",
        )
    }

    private fun kotlinSources(): List<Path> = Files.walk(sourceRoot).use { paths ->
        paths.filter { Files.isRegularFile(it) && it.extension == "kt" }
            .sorted()
            .toList()
    }
}
