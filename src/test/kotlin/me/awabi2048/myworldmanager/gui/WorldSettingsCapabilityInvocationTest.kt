package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuRenderer
import com.awabi2048.ccsystem.api.gui.MenuActionObservation
import com.awabi2048.ccsystem.api.gui.MenuActionSafety
import com.awabi2048.ccsystem.api.gui.MenuReversibleContract
import com.awabi2048.ccsystem.api.gui.MenuCapabilityPresentation
import com.awabi2048.ccsystem.api.gui.MenuCapabilityTrigger
import com.awabi2048.ccsystem.api.gui.MenuContractValidator
import com.awabi2048.ccsystem.api.gui.MenuInteraction
import com.awabi2048.ccsystem.api.gui.ResolvedMenuCapability
import com.awabi2048.ccsystem.api.gui.ResolvedMenuCapabilityAction
import org.bukkit.Material
import org.bukkit.event.inventory.ClickType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorldSettingsCapabilityInvocationTest {
    @Test
    fun `world settings complete definition keeps five capabilities as direct interactions`() {
        val arguments = mapOf("world_uuid" to "world-1")
        val expectedCapabilities = listOf(
            "mwm-chanpon:world-settings-expansion" to MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE,
            "mwm-chanpon:world-settings-publish" to MenuActionSafety.REVERSIBLE,
            "mwm-chanpon:world-settings-notification" to MenuActionSafety.REVERSIBLE,
            "mwm-chanpon:world-settings-environment" to MenuActionSafety.NAVIGATION_ONLY,
            "mwm-chanpon:world-settings-critical" to MenuActionSafety.NAVIGATION_ONLY,
        )
        val invocations = expectedCapabilities.mapIndexed { index, (capabilityId, safety) ->
            worldSettingsCapabilityInvocation(index + 20, capability(capabilityId, safety), arguments)
        }

        val observations = invocations.map { invocation ->
            MenuActionObservation(
                invocation.slot,
                MenuInteraction.Capability(
                    capabilityId = invocation.capability.capabilityId,
                    arguments = invocation.arguments,
                    attributes = invocation.attributes,
                    acceptedClicks = invocation.capability.acceptedClicks,
                    safety = invocation.capability.safety,
                    safetyByClick = invocation.capability.safetyByClick,
                    reversibleContractByClick = invocation.capability.reversibleContractByClick,
                ),
            )
        }
        val completedDefinition = InventoryMenuDefinition(
            owner = "myworldmanager",
            id = "world_settings_runtime",
            renderer = InventoryMenuRenderer { error("render is not part of this contract test") },
            actions = emptyMap(),
        )

        val providerOnlyViolations = MenuContractValidator.validate(completedDefinition, observations)
        assertEquals(4, providerOnlyViolations.size)
        assertTrue(providerOnlyViolations.all { it.message == "reversible provider is not registered: test:state" })
        assertEquals(expectedCapabilities.map { it.first }, observations.map {
            assertInstanceOf(MenuInteraction.Capability::class.java, it.interaction).capabilityId
        })
        observations.forEachIndexed { index, observation ->
            val interaction = assertInstanceOf(MenuInteraction.Capability::class.java, observation.interaction)
            assertEquals(arguments, interaction.arguments)
            assertEquals(emptyMap<String, Any>(), interaction.attributes)
            assertEquals(setOf(ClickType.LEFT, ClickType.RIGHT), interaction.acceptedClicks)
            assertEquals(expectedCapabilities[index].second, interaction.safety)
            assertEquals(expectedCapabilities[index].second, interaction.safetyByClick[ClickType.LEFT])
            assertEquals(expectedCapabilities[index].second, interaction.safetyByClick[ClickType.RIGHT])
        }
    }

    private fun capability(capabilityId: String, safety: MenuActionSafety): ResolvedMenuCapability = ResolvedMenuCapability(
        capabilityId = capabilityId,
        presentation = MenuCapabilityPresentation(
            GuiItemSpec(Material.STONE, GuiNameSpec.Empty, GuiLoreSpec.None, GuiElementRole.ACTION, 1),
        ),
        actions = listOf(
            ResolvedMenuCapabilityAction(
                id = "open",
                trigger = MenuCapabilityTrigger.LEFT_RIGHT,
                text = "クリック",
                safety = safety,
                reversibleContract = if (safety == MenuActionSafety.REVERSIBLE) MenuReversibleContract("test:state") else null,
            ),
        ),
    )
}
