package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.gui.MenuActionSafety
import com.awabi2048.ccsystem.api.gui.MenuGesture
import com.awabi2048.ccsystem.api.gui.MenuReversibleContract
import me.awabi2048.myworldmanager.service.MwmReversibleContracts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class MenuReversibleClassificationTest {
    @Test
    fun `classification declares a provider for every reversible semantic action`() {
        val rows = javaClass.getResourceAsStream("/menu-reversible-classification.csv")!!
            .bufferedReader().useLines { lines -> lines.drop(1).filter(String::isNotBlank).map { it.split(',') }.toList() }
        assertEquals(38, rows.size)
        assertEquals(rows.size, rows.map { it[0] }.toSet().size)
        rows.forEach { row ->
            val classification = row[3]
            val provider = row[4]
            val reason = row[5]
            assertTrue(reason.isNotBlank(), "classification reason is missing: ${row[0]}")
            assertEquals(classification == "REVERSIBLE", provider.isNotBlank(), "provider mismatch: ${row[0]}")
        }
    }

    @Test
    fun `safety boundary rejects reversible action without a contract`() {
        assertThrows(IllegalArgumentException::class.java) {
            menuGestureAction("missing", MenuGesture.ANY, "test", safety = MenuActionSafety.REVERSIBLE)
        }
        val action = menuGestureAction(
            "declared", MenuGesture.ANY, "test", safety = MenuActionSafety.REVERSIBLE,
            reversibleContract = MenuReversibleContract(MwmReversibleContracts.USER_SETTINGS_PROVIDER),
        )
        assertEquals(MwmReversibleContracts.USER_SETTINGS_PROVIDER, action.reversibleContract?.providerId)
    }

    @Test
    fun `all aggregate provider ids are distinct`() {
        val ids = listOf(
            MwmReversibleContracts.USER_SETTINGS_PROVIDER,
            MwmReversibleContracts.DISPLAY_ORDER_PROVIDER,
            MwmReversibleContracts.CREATION_SESSION_PROVIDER,
            MwmReversibleContracts.MENU_SESSION_PROVIDER,
            MwmReversibleContracts.PLAYER_STATE_PROVIDER,
            MwmReversibleContracts.WORLD_STATE_PROVIDER,
            MwmReversibleContracts.DRAFT_PROVIDER,
            MwmReversibleContracts.SETTINGS_SESSION_PROVIDER,
            MwmReversibleContracts.PORTAL_STATE_PROVIDER,
        )
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.startsWith("myworldmanager:") })
    }

    @Test
    fun `direct reversible gesture declarations always carry a contract`() {
        val missing = mutableListOf<String>()
        Files.walk(Path.of("src", "main", "kotlin")).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.forEach { path ->
                invocations(Files.readString(path), "menuGestureAction").forEach { invocation ->
                    if (invocation.contains("MenuActionSafety.REVERSIBLE") && !invocation.contains("reversibleContract")) {
                        missing += path.toString()
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "reversible contract is missing: $missing")
    }

    @Test
    fun `all aggregate providers are registered and owner lifecycle is closed`() {
        val providers = Files.readString(Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/service/MwmReversibleStateProviders.kt",
        ))
        assertEquals(9, "registry.register(".toRegex(RegexOption.LITERAL).findAll(providers).count())
        val plugin = Files.readString(Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/MyWorldManager.kt",
        ))
        assertTrue(plugin.contains("MwmReversibleStateProviders(this).register"))
        assertTrue(plugin.contains("getMenuReversibleStateProviderRegistry().unregisterOwner(\"myworldmanager\")"))
    }

    private fun invocations(source: String, function: String): List<String> = buildList {
        var cursor = 0
        while (true) {
            val start = source.indexOf("$function(", cursor)
            if (start < 0) break
            var index = start + function.length
            var depth = 0
            while (index < source.length) {
                when (source[index]) {
                    '(' -> depth++
                    ')' -> if (--depth == 0) { index++; break }
                }
                index++
            }
            check(depth == 0)
            add(source.substring(start, index))
            cursor = index
        }
    }
}
