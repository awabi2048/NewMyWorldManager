package me.awabi2048.myworldmanager.architecture

import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import me.awabi2048.myworldmanager.util.semanticLore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuiSemanticProducerContractTest {
    @Test
    fun `inventory producers use v3 semantic lore and names`() {
        val sources = inventorySources()
        val combined = sources.joinToString("\n") { Files.readString(it) }

        assertFalse(combined.contains("GuiLoreSpec.Rich"))
        assertFalse(Regex("GuiNameSpec\\.(Text|Component)").containsMatchIn(combined))
        assertFalse(combined.contains("map(GuiLoreLine::Text)"))
        assertTrue(Regex("semanticLore\\(").findAll(combined).count() >= 39)
        assertTrue(combined.contains("GuiNameSpec.FixedLabel"))
        assertTrue(combined.contains("GuiNameSpec.TargetIdentity"))
        assertTrue(combined.contains("GuiNameSpec.Opaque"))
    }

    @Test
    fun `semantic lore keeps line order and requested frame`() {
        val lines = listOf(
            GuiLoreLine.Text("description"),
            GuiLoreLine.Data("label", "value", "§f"),
            GuiLoreLine.Warning("warning"),
            GuiLoreLine.Danger("danger"),
        )
        GuiLoreFrame.entries.forEach { frame ->
            val spec = semanticLore(lines, frame) as GuiLoreSpec.FramedBlocks
            assertEquals(frame, spec.frame)
            assertEquals(lines, spec.blocks.single().lines)
        }
    }

    @Test
    fun `known disabled creation entry carries a runtime reason`() {
        val source = Files.readString(sourceRoot().resolve("gui/PlayerWorldGui.kt"))
        assertTrue(source.contains("menuUnavailable("))
        assertTrue(source.contains("unavailableReason"))
        assertTrue(source.contains("warnings = plugin.languageManager.getMessageList(player, reason.loreKey)"))
    }

    private fun inventorySources(): List<Path> = Files.walk(sourceRoot()).use { paths ->
        paths.filter { Files.isRegularFile(it) && it.name.endsWith(".kt") }
            .filter { !it.toString().replace('\\', '/').contains("/ui/bedrock/") }
            .filter { it.name != "CustomItem.kt" && !it.name.contains("Dialog") }
            .toList()
    }

    private fun sourceRoot(): Path = Path.of("src/main/kotlin/me/awabi2048/myworldmanager")
}
