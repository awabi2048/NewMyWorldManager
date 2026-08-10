package me.awabi2048.myworldmanager.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 管理ワールドが環境未指定のWorldCreatorへ再び分散しないことを検証します。 */
class ManagedWorldCreatorBoundaryTest {
    @Test
    fun `WorldCreatorの構築は共通ファクトリーだけに存在する`() {
        val root = Path.of("src/main/kotlin")
        val occurrences = Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.extension == "kt" }
                .filter { "WorldCreator(" in it.readText() }
                .map { root.relativize(it).toString().replace('\\', '/') }
                .toList()
        }

        assertEquals(
            listOf("me/awabi2048/myworldmanager/service/ManagedWorldCreatorFactory.kt"),
            occurrences
        )
    }

    @Test
    fun `ロード境界は保存済み次元をファクトリーへ渡す`() {
        val source = Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/service/WorldService.kt"
        ).readText()

        assertTrue("managedWorldCreatorFactory.create(key, dimension)" in source)
        assertTrue("worldData?.dimension" in source)
    }
}
