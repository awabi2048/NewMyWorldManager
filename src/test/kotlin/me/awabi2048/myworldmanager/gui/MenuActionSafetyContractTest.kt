package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.gui.MenuActionSafety
import com.awabi2048.ccsystem.api.gui.MenuGesture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

class MenuActionSafetyContractTest {
    @Test
    fun `production gesture actions use the single safety boundary`() {
        val sources = kotlinSources()
        val rawConstructors = sources.flatMap { source ->
            findAll(source.content, "GuiMenuActionIntent.GestureAction(").map { source.path }
        }

        assertEquals(
            listOf(contractSourcePath()),
            rawConstructors,
            "低水準GestureAction構築は共通安全境界だけに限定してください。",
        )

        val unclassified = sources
            .filter { source ->
                source.path != contractSourcePath() &&
                    source.content.contains("MenuActionSafety.UNSPECIFIED")
            }
            .map { it.path }
        assertTrue(unclassified.isEmpty(), "未分類のMenuActionSafetyがあります: $unclassified")
    }

    @Test
    fun `capability actions are checked at each menu construction boundary`() {
        val unguardedCapabilities = kotlinSources().flatMap { source ->
            functionInvocations(source.content, "GuiMenuCapabilityInvocationSpec")
                .filterNot { invocation -> invocation.contains("requireExplicitActionSafety()") }
                .map { source.path }
        }

        assertTrue(
            unguardedCapabilities.isEmpty(),
            "Capability actionの安全区分検証がありません: $unguardedCapabilities",
        )
    }

    @Test
    fun `safety boundary rejects unspecified actions`() {
        assertThrows(IllegalArgumentException::class.java) {
            menuGestureAction(
                actionId = "test_unspecified",
                gesture = MenuGesture.ANY,
                label = "test",
                safety = MenuActionSafety.UNSPECIFIED,
            )
        }

        val action = menuGestureAction(
            actionId = "test_navigation",
            gesture = MenuGesture.ANY,
            label = "test",
            safety = MenuActionSafety.NAVIGATION_ONLY,
        )
        assertEquals(MenuActionSafety.NAVIGATION_ONLY, action.safety)
    }

    private fun kotlinSources(): List<KotlinSource> = Files.walk(Path.of("src", "main", "kotlin")).use { paths ->
        paths
            .filter { path -> Files.isRegularFile(path) && path.fileName.toString().endsWith(".kt") }
            .map { path -> KotlinSource(path.normalize(), Files.readString(path)) }
            .collect(Collectors.toList())
    }

    private fun contractSourcePath(): Path =
        Path.of("src", "main", "kotlin", "me", "awabi2048", "myworldmanager", "gui", "MenuActionSafetyContract.kt")
            .normalize()

    private fun findAll(source: String, token: String): List<Int> = buildList {
        var index = source.indexOf(token)
        while (index >= 0) {
            add(index)
            index = source.indexOf(token, index + token.length)
        }
    }

    private fun functionInvocations(source: String, functionName: String): List<String> = buildList {
        var searchFrom = 0
        while (true) {
            val start = source.indexOf(functionName, searchFrom)
            if (start < 0) break
            val opening = start + functionName.length
            if (opening >= source.length || source[opening] != '(') {
                searchFrom = opening
                continue
            }

            var depth = 0
            var end = opening
            while (end < source.length) {
                when (source[end]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) {
                            end++
                            break
                        }
                    }
                }
                end++
            }
            check(depth == 0) { "呼出しの括弧が閉じていません: $functionName" }
            add(source.substring(start, end))
            searchFrom = end
        }
    }

    private data class KotlinSource(
        val path: Path,
        val content: String,
    )
}
