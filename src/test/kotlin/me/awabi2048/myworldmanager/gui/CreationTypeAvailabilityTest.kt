package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.MenuAcceptedClicks
import com.awabi2048.ccsystem.api.gui.MenuActionSafety
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuInteraction
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.session.WorldCreationType
import net.kyori.adventure.text.Component
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class CreationTypeAvailabilityTest {
    @Test
    fun `template with no usable entries completes as typed unavailable for standard clicks`() {
        val availability = resolveCreationTypeAvailability(WorldCreationType.TEMPLATE, false, true)
        val element = completedElement(availability)

        assertFalse(element.enabled)
        assertEquals(GuiElementRole.CONTENT, element.role)
        val unavailable = assertInstanceOf(MenuInteraction.Unavailable::class.java, element.resolvedInteraction())
        assertEquals(Component.text("テンプレートが見つかりませんでした"), unavailable.message)
        listOf(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT, ClickType.MIDDLE)
            .forEach { click -> assertTrue(click in acceptedClicks(element), "$click must be rejected with the reason") }
        assertEquals("error.preview_template_not_found", availability.reasonKey)
    }

    @Test
    fun `template with a usable entry remains navigation action`() {
        val availability = resolveCreationTypeAvailability(WorldCreationType.TEMPLATE, true, true)
        val element = completedElement(availability)
        val interaction = assertInstanceOf(MenuInteraction.Action::class.java, element.resolvedInteraction())

        assertTrue(element.enabled)
        assertEquals(GuiElementRole.ACTION, element.role)
        assertEquals(MenuAcceptedClicks.STANDARD, interaction.acceptedClicks)
        assertEquals(MenuActionSafety.NAVIGATION_ONLY, interaction.safety)
        val route = com.awabi2048.ccsystem.api.gui.MenuRoute("myworldmanager", "creation_template_list")
        assertEquals(route, assertInstanceOf(MenuUpdate.Navigate::class.java, templateCreationTypeUpdate(route)).route)
    }

    @Test
    fun `all creation types share affordability and only template requires a template`() {
        WorldCreationType.entries.forEach { type ->
            assertFalse(resolveCreationTypeAvailability(type, true, false).enabled)
        }
        assertTrue(resolveCreationTypeAvailability(WorldCreationType.SEED, false, true).enabled)
        assertTrue(resolveCreationTypeAvailability(WorldCreationType.RANDOM, false, true).enabled)
    }

    @Test
    fun `render and direct handler use the same availability evaluator`() {
        val source = Path.of("src/main/kotlin/me/awabi2048/myworldmanager/gui/CreationGui.kt").readText()
        val render = source.substringAfter("private fun renderTypeSelection").substringBefore("private fun selectCreationType")
        val handler = source.substringAfter("private fun selectCreationType").substringBefore("private fun cancelCreation")
        assertTrue(render.contains("creationTypeAvailability("))
        assertTrue(handler.contains("creationTypeAvailability("))
        assertFalse(handler.contains("none(plugin.templateRepository::isUsable)"))
        assertFalse(handler.contains("stats.worldPoint < cost"))
    }

    private fun completedElement(availability: CreationTypeAvailability): MenuElement = if (availability.enabled) {
        MenuElement(
            20,
            emptyItemStack(),
            GuiElementRole.ACTION,
            interaction = MenuInteraction.Action(
                "select_type",
                MenuAcceptedClicks.STANDARD,
                mapOf("type" to WorldCreationType.TEMPLATE.name),
                safety = MenuActionSafety.NAVIGATION_ONLY,
            ),
        )
    } else {
        MenuElement(
            20,
            emptyItemStack(),
            GuiElementRole.CONTENT,
            enabled = false,
            interaction = MenuInteraction.Unavailable(
                MenuAcceptedClicks.STANDARD,
                Component.text("テンプレートが見つかりませんでした"),
            ),
        )
    }

    private fun acceptedClicks(element: MenuElement): Set<ClickType> = when (val interaction = element.resolvedInteraction()) {
        is MenuInteraction.Action -> interaction.acceptedClicks
        is MenuInteraction.Unavailable -> interaction.acceptedClicks
        else -> emptySet()
    }

    private fun emptyItemStack(): ItemStack {
        val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").also { it.isAccessible = true }
        return (field.get(null) as sun.misc.Unsafe).allocateInstance(ItemStack::class.java) as ItemStack
    }

}
