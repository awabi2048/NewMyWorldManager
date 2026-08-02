package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuActionSafety
import com.awabi2048.ccsystem.api.gui.MenuCapabilityAction
import com.awabi2048.ccsystem.api.gui.MenuCapabilityActionHandler
import com.awabi2048.ccsystem.api.gui.MenuCapabilityActionTextProvider
import com.awabi2048.ccsystem.api.gui.MenuCapabilityAvailability
import com.awabi2048.ccsystem.api.gui.MenuCapabilityCompositionMode
import com.awabi2048.ccsystem.api.gui.MenuCapabilityDefinition
import com.awabi2048.ccsystem.api.gui.MenuCapabilityPresentation
import com.awabi2048.ccsystem.api.gui.MenuCapabilityPresentationProvider
import com.awabi2048.ccsystem.api.gui.MenuCapabilityTrigger
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuElementPresentationSemantics
import com.awabi2048.ccsystem.api.gui.MenuInteraction
import com.awabi2048.ccsystem.api.gui.MenuPresentationProfile
import com.awabi2048.ccsystem.api.gui.MenuNameSemantic
import com.awabi2048.ccsystem.api.gui.MenuLoreSemanticSource
import com.awabi2048.ccsystem.api.gui.MenuLoreSemantics
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.ResolvedMenuCapability
import com.awabi2048.ccsystem.api.gui.withCapabilityComposition
import com.awabi2048.ccsystem.core.gui.MenuCapabilityServiceImpl
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class MemberManagementHostAugmentationTest {
    private val hostLines = listOf(
        GuiLoreLine.Text("オンライン"),
        GuiLoreLine.Data("権限", "オーナー", "§f"),
    )
    private val toolLines = listOf(
        GuiLoreLine.Text("ツールの使用権限"),
        GuiLoreLine.Data("WorldEdit", "有効", "§a"),
        GuiLoreLine.Data("VoxelSniper", "有効", "§a"),
        GuiLoreLine.Data("Axiom", "無効", "§c"),
    )

    @Test
    fun `fake resolve composes completed member semantics and lore once in contract order`() {
        val service = MenuCapabilityServiceImpl()
        service.register(definition("tools", available = true, hostAugmentation = true))
        val capability = resolve(service)
        val actionLines = listOf(
            GuiLoreLine.Interaction(player(), setOf(ClickType.LEFT, ClickType.RIGHT), "ツールの使用権限を変更する"),
            GuiLoreLine.Interaction(player(), setOf(ClickType.SHIFT_LEFT), "所有権を譲渡する"),
            GuiLoreLine.Interaction(player(), setOf(ClickType.SHIFT_RIGHT), "メンバーを削除する"),
        )
        val composition = composeMemberManagementHost(
            capability,
            hostItem(),
            listOf(GuiLoreBlock(hostLines)),
            actionLines,
        )
        val completedLore = (com.awabi2048.ccsystem.api.gui.MenuCapabilityComposer.composeHostAugmentation(
            capability!!, hostItem(), listOf(GuiLoreBlock(hostLines)), actionLines,
        ).lore as GuiLoreSpec.Blocks).blocks
        val element = completedElement(capability, composition.snapshot, completedLore)

        assertEquals(listOf(hostLines, toolLines, actionLines), completedLore.map { it.lines })
        assertEquals(1, completedLore.flatten().count { it == GuiLoreLine.Text("オンライン") })
        toolLines.forEach { line -> assertEquals(1, completedLore.flatten().count { it == line }) }
        assertEquals(
            listOf("ツールの使用権限を変更する", "所有権を譲渡する", "メンバーを削除する"),
            completedLore.flatten().filterIsInstance<GuiLoreLine.Interaction>().map { it.label },
        )
        assertEquals(MenuNameSemantic.TARGET_IDENTITY, element.presentationSemantics.name)
        assertEquals(MenuCapabilityCompositionMode.HOST_AUGMENTATION, element.presentationSemantics.capabilityCompositionMode)
        assertEquals("mwm-chanpon:tools", element.presentationSemantics.capabilityComposition?.contributorCapabilityId)
        assertEquals(toolLines, element.presentationSemantics.capabilityComposition?.augmentationBlocks?.single()?.lines)
    }

    @Test
    fun `zero resolved capabilities keeps host only`() {
        val service = MenuCapabilityServiceImpl()
        service.register(definition("unavailable", available = false, hostAugmentation = true))
        val capability = resolve(service)
        val composition = composeMemberManagementHost(capability, hostItem(), listOf(GuiLoreBlock(hostLines)), emptyList())
        assertNull(capability)
        assertNull(composition.snapshot)
        assertEquals(listOf(hostLines), composition.semanticLoreBlocks.map { it.lines })
    }

    @Test
    fun `one available capability ignores unavailable sibling`() {
        val service = MenuCapabilityServiceImpl()
        service.register(definition("unavailable", available = false, hostAugmentation = true))
        service.register(definition("tools", available = true, hostAugmentation = true))
        assertEquals("mwm-chanpon:tools", resolve(service)?.capabilityId)
    }

    @Test
    fun `multiple available capabilities fail with all ids`() {
        val service = MenuCapabilityServiceImpl()
        service.register(definition("alpha", available = true, hostAugmentation = true))
        service.register(definition("beta", available = true, hostAugmentation = true))
        val error = assertThrows(IllegalArgumentException::class.java) { resolve(service) }
        assertTrue(error.message.orEmpty().contains("mwm-chanpon:alpha"))
        assertTrue(error.message.orEmpty().contains("mwm-chanpon:beta"))
    }

    @Test
    fun `full item capability is rejected by member host placement`() {
        val service = MenuCapabilityServiceImpl()
        service.register(definition("full", available = true, hostAugmentation = false))
        val error = assertThrows(IllegalArgumentException::class.java) { resolve(service) }
        assertTrue(error.message.orEmpty().contains("mwm-chanpon:full"))
        assertTrue(error.message.orEmpty().contains("FULL_ITEM"))
    }

    private fun resolve(service: MenuCapabilityServiceImpl): ResolvedMenuCapability? =
        resolveMemberManagementHostAugmentation(service.definitions("member-management-entry")) { definition ->
            service.resolve(definition.capabilityId, player())
        }

    private fun definition(id: String, available: Boolean, hostAugmentation: Boolean): MenuCapabilityDefinition =
        MenuCapabilityDefinition(
            owner = "mwm-chanpon",
            id = id,
            placement = "member-management-entry",
            availability = MenuCapabilityAvailability { available },
            presentationProvider = MenuCapabilityPresentationProvider {
                if (hostAugmentation) MenuCapabilityPresentation.hostAugmentation(listOf(GuiLoreBlock(toolLines)))
                else MenuCapabilityPresentation(hostItem())
            },
            actions = listOf(
                MenuCapabilityAction(
                    id = "edit-tools",
                    trigger = MenuCapabilityTrigger.LEFT_RIGHT,
                    textProvider = MenuCapabilityActionTextProvider { "ツールの使用権限を変更する" },
                    handler = MenuCapabilityActionHandler { MenuActionResult.Ignored },
                    safety = MenuActionSafety.NAVIGATION_ONLY,
                ),
            ),
        )

    private fun hostItem() = GuiItemSpec(
        Material.PLAYER_HEAD,
        GuiNameSpec.TargetIdentity(Component.text("awabi2048")),
        GuiLoreSpec.Blocks(listOf(GuiLoreBlock(hostLines))),
        GuiElementRole.ACTION,
        1,
    )

    private fun completedElement(
        capability: ResolvedMenuCapability,
        snapshot: com.awabi2048.ccsystem.api.gui.MenuCapabilityCompositionSnapshot?,
        completedLore: List<GuiLoreBlock>,
    ): MenuElement = MenuElement(
        slot = 19,
        item = emptyItemStack(),
        role = GuiElementRole.ACTION,
        interaction = MenuInteraction.Capability(
            capability.capabilityId,
            acceptedClicks = setOf(ClickType.LEFT, ClickType.RIGHT),
            safety = MenuActionSafety.NAVIGATION_ONLY,
        ),
    ).also { element ->
        val semantics = MenuElementPresentationSemantics(
            MenuNameSemantic.TARGET_IDENTITY,
            MenuLoreSemantics(MenuLoreSemanticSource.STRUCTURED, GuiLoreFrame.BOTH, completedLore.map {
                com.awabi2048.ccsystem.api.gui.MenuLoreBlockSemantics(it.lines.map { line ->
                    com.awabi2048.ccsystem.api.gui.MenuLoreLineSemantics(
                        if (line is GuiLoreLine.Interaction) com.awabi2048.ccsystem.api.gui.MenuLoreLineKind.ACTION
                        else com.awabi2048.ccsystem.api.gui.MenuLoreLineKind.DATA,
                        (line as? GuiLoreLine.Interaction)?.let { interaction ->
                            com.awabi2048.ccsystem.api.gui.MenuLoreActionSemantics(
                                interaction.gesture,
                                (interaction.gesture as com.awabi2048.ccsystem.api.gui.GuiInputGesture.MenuClicks).acceptedClicks,
                                interaction.label,
                                interaction.label,
                            )
                        },
                    )
                })
            }),
            MenuPresentationProfile.LIST_TARGET,
        )
        MenuElement::class.java.getDeclaredField("presentationSemantics").also { it.isAccessible = true }
            .set(element, semantics)
    }.withCapabilityComposition(capability, snapshot)

    private fun List<GuiLoreBlock>.flatten() = flatMap { it.lines }

    private fun player(): Player = Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java),
    ) { proxy, method, _ ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> false
            "toString" -> "member-test-player"
            else -> defaultValue(method.returnType)
        }
    } as Player

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Character.TYPE -> '\u0000'
        else -> null
    }

    private fun emptyItemStack(): ItemStack {
        val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").also { it.isAccessible = true }
        return (field.get(null) as sun.misc.Unsafe).allocateInstance(ItemStack::class.java) as ItemStack
    }
}
