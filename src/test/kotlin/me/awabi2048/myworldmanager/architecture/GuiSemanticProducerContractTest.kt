package me.awabi2048.myworldmanager.architecture

import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.MenuElementPresentationSemantics
import com.awabi2048.ccsystem.api.gui.MenuLoreSemanticSource
import com.awabi2048.ccsystem.api.gui.MenuNameSemantic
import com.awabi2048.ccsystem.api.gui.MenuPresentationProfile
import com.awabi2048.ccsystem.api.gui.MenuPresentationSemanticsValidator
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import me.awabi2048.myworldmanager.util.semanticLore
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
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

    @Test
    fun `creation type unavailability and tour current world preserve typed semantics`() {
        val creation = Files.readString(sourceRoot().resolve("gui/CreationGui.kt"))
        assertTrue(creation.contains("getGuiElementService().menuUnavailable("))
        assertTrue(creation.contains("lang.getComponent(player, reason)"))
        assertTrue(creation.contains("warnings = warnings"))

        val tour = Files.readString(sourceRoot().resolve("gui/TourGui.kt"))
        val currentWorld = tour.substringAfter("private fun createCurrentWorldEntry")
            .substringBefore("private fun createTourEntry")
        assertTrue(currentWorld.indexOf("GuiLoreLine.UserText") < currentWorld.indexOf("GuiLoreLine.Data"))
        assertTrue(tour.contains("MenuGesture.LEFT_RIGHT"))
        val createEntry = tour.substringAfter("gui.tour.menu.create.action")
            .substringBefore("elements += createCurrentWorldEntry")
        assertTrue(createEntry.contains("gesture = MenuGesture.ANY"))
    }

    @Test
    fun `member slot presentation is a covered dynamic target with one custom action`() {
        val settings = Files.readString(sourceRoot().resolve("gui/WorldSettingsGui.kt"))
        val memberProducer = settings.substringAfter("private fun createMemberEntrySpec")
            .substringBefore("private fun formatPendingInviteDateTimeForPlayer")
        assertTrue(memberProducer.contains("name = GuiNameSpec.TargetIdentity("))
        assertFalse(memberProducer.contains("GuiNameSpec.Opaque"))
        assertTrue(memberProducer.contains("semanticLoreBlocks"))
        assertTrue(memberProducer.contains("copyWithPresentationSemantics("))

        val lore = GuiLoreSpec.Blocks(
            listOf(com.awabi2048.ccsystem.api.gui.GuiLoreBlock(listOf(GuiLoreLine.Text("online")))),
        )
        val semantics = semantics(
            GuiNameSpec.TargetIdentity(Component.text("player")),
            lore,
            MenuPresentationProfile.SINGLE_CUSTOM_ACTION,
        )
        assertEquals(MenuNameSemantic.TARGET_IDENTITY, semantics.name)
        assertEquals(MenuLoreSemanticSource.STRUCTURED, semantics.lore.source)
        assertEquals(MenuPresentationProfile.SINGLE_CUSTOM_ACTION, semantics.profile)
        assertTrue(MenuPresentationSemanticsValidator.violations(semantics).isEmpty())
    }

    @Test
    fun `dynamic player and world lists avoid opaque names outside generic boundaries`() {
        val settings = Files.readString(sourceRoot().resolve("gui/WorldSettingsGui.kt"))
        assertFalse(settings.contains("GuiNameSpec.Opaque"))

        val combined = inventorySources().joinToString("\n") { Files.readString(it) }
        assertTrue(combined.contains("GuiNameSpec.TargetIdentity"))
        assertTrue(combined.contains("GuiNameSpec.FixedLabel"))
        assertTrue(combined.contains("GuiNameSpec.Opaque"))
        assertTrue(Files.readString(sourceRoot().resolve("util/GuiSpecFactory.kt")).contains("opaqueName("))
    }

    private fun inventorySources(): List<Path> = Files.walk(sourceRoot()).use { paths ->
        paths.filter { Files.isRegularFile(it) && it.name.endsWith(".kt") }
            .filter { !it.toString().replace('\\', '/').contains("/ui/bedrock/") }
            .filter { it.name != "CustomItem.kt" && !it.name.contains("Dialog") }
            .toList()
    }

    private fun sourceRoot(): Path = Path.of("src/main/kotlin/me/awabi2048/myworldmanager")

    private fun semantics(
        name: GuiNameSpec,
        lore: GuiLoreSpec,
        profile: MenuPresentationProfile,
    ): MenuElementPresentationSemantics {
        val type = Class.forName("com.awabi2048.ccsystem.core.gui.MenuPresentationSemanticsFactory")
        val i18n: (Player?, String, Map<String, Any>) -> String = { _, key, _ -> key }
        val factory = type.getConstructor(Function3::class.java).newInstance(i18n)
        return type.getMethod(
            "create",
            GuiNameSpec::class.java,
            GuiLoreSpec::class.java,
            MenuPresentationProfile::class.java,
            Component::class.java,
        ).invoke(factory, name, lore, profile, null) as MenuElementPresentationSemantics
    }
}
