package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuRenderer
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionObservation
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuActionSafety
import com.awabi2048.ccsystem.api.gui.MenuCapabilityDefinition
import com.awabi2048.ccsystem.api.gui.MenuCapabilityAvailability
import com.awabi2048.ccsystem.api.gui.MenuCapabilityPresentationProvider
import com.awabi2048.ccsystem.api.gui.MenuCapabilityAction
import com.awabi2048.ccsystem.api.gui.MenuCapabilityActionHandler
import com.awabi2048.ccsystem.api.gui.MenuCapabilityActionTextProvider
import com.awabi2048.ccsystem.api.gui.MenuCapabilityService
import com.awabi2048.ccsystem.api.gui.MenuCapabilityTrigger
import com.awabi2048.ccsystem.api.gui.MenuContractValidationContext
import com.awabi2048.ccsystem.api.gui.MenuContractValidator
import com.awabi2048.ccsystem.api.gui.MenuInteraction
import com.awabi2048.ccsystem.api.gui.ResolvedMenuCapability
import com.awabi2048.ccsystem.api.gui.ResolvedMenuCapabilityAction
import com.awabi2048.ccsystem.api.gui.MenuRuntimeInspectionInteractionSnapshot
import com.awabi2048.ccsystem.api.gui.MenuRuntimeInteractionKind
import com.awabi2048.ccsystem.api.gui.MenuRuntimeSlotKind
import com.awabi2048.ccsystem.api.gui.MenuRuntimeSlotSnapshot
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.util.UUID
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.readText

class MemberManagementClickBranchesTest {
    @Test
    fun `completed member definition keeps capability and host interactions by click`() {
        val subject = Any()
        val attributes = mapOf<String, Any>("member-subject" to subject)
        val payload = mapOf("operation" to "MEMBER", "target_uuid" to "member-1")
        val capability = capability("mwm-chanpon:member-tools")
        val interaction = memberManagementEntryInteraction(
            capability,
            attributes,
            listOf(
                MenuInteraction.Action(
                    actionId = "dispatch",
                    acceptedClicks = setOf(ClickType.SHIFT_LEFT),
                    payload = payload,
                    safety = MenuActionSafety.CONFIRM_ENTRY,
                    safetyByClick = mapOf(ClickType.SHIFT_LEFT to MenuActionSafety.CONFIRM_ENTRY),
                ),
                MenuInteraction.Action(
                    actionId = "dispatch",
                    acceptedClicks = setOf(ClickType.SHIFT_RIGHT),
                    payload = payload,
                    safety = MenuActionSafety.CONFIRM_ENTRY,
                    safetyByClick = mapOf(ClickType.SHIFT_RIGHT to MenuActionSafety.CONFIRM_ENTRY),
                ),
            ),
        )
        val completedDefinition = InventoryMenuDefinition(
            owner = "myworldmanager",
            id = "member_management",
            renderer = InventoryMenuRenderer { error("render is not part of this contract test") },
            actions = mapOf("dispatch" to MenuActionHandler { MenuActionResult.Ignored }),
        )

        val clickBranches = assertInstanceOf(MenuInteraction.ClickBranches::class.java, interaction)
        val left = assertInstanceOf(MenuInteraction.Capability::class.java, clickBranches.resolve(ClickType.LEFT))
        val right = assertInstanceOf(MenuInteraction.Capability::class.java, clickBranches.resolve(ClickType.RIGHT))
        val transfer = assertInstanceOf(MenuInteraction.Action::class.java, clickBranches.resolve(ClickType.SHIFT_LEFT))
        val remove = assertInstanceOf(MenuInteraction.Action::class.java, clickBranches.resolve(ClickType.SHIFT_RIGHT))

        assertEquals(capability.capabilityId, left.capabilityId)
        assertEquals(capability.capabilityId, right.capabilityId)
        assertEquals(emptyMap<String, String>(), left.arguments)
        assertSame(subject, left.attributes["member-subject"])
        assertEquals(setOf(ClickType.LEFT, ClickType.RIGHT), left.acceptedClicks)
        assertEquals(MenuActionSafety.NAVIGATION_ONLY, left.safety)
        assertEquals(MenuActionSafety.NAVIGATION_ONLY, left.safetyByClick[ClickType.LEFT])
        assertEquals(MenuActionSafety.NAVIGATION_ONLY, right.safetyByClick[ClickType.RIGHT])
        assertEquals("dispatch", transfer.actionId)
        assertEquals(payload, transfer.payload)
        assertEquals(setOf(ClickType.SHIFT_LEFT), transfer.acceptedClicks)
        assertEquals(MenuActionSafety.CONFIRM_ENTRY, transfer.safety)
        assertEquals(setOf(ClickType.SHIFT_RIGHT), remove.acceptedClicks)
        assertEquals(MenuActionSafety.CONFIRM_ENTRY, remove.safety)

        val inspection = interactionSnapshot(interaction)
        val normal = MenuRuntimeSlotSnapshot(
            slot = 20,
            kind = MenuRuntimeSlotKind.ACTION,
            material = Material.PLAYER_HEAD,
            amount = 1,
            name = Component.text("member"),
            lore = emptyList(),
            glint = false,
            role = GuiElementRole.ACTION,
            interactionKind = MenuRuntimeInteractionKind.CLICK_BRANCHES,
            actionId = null,
            capabilityId = null,
            acceptedClicks = inspection.acceptedClicks,
            payload = emptyMap(),
            enabled = true,
            safety = inspection.safety,
            safetyByClick = inspection.safetyByClick,
            branches = emptyList(),
            interaction = inspection,
        )
        assertEquals(inspection, normal.interaction)
        assertEquals(MenuRuntimeInteractionKind.CLICK_BRANCHES, inspection.kind)
        assertEquals(
            setOf(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT),
            inspection.acceptedClicks,
        )
        val capabilityBranch = inspection.branches[0].interaction
        assertEquals(setOf(ClickType.LEFT, ClickType.RIGHT), capabilityBranch.acceptedClicks)
        assertEquals(MenuRuntimeInteractionKind.CAPABILITY, capabilityBranch.kind)
        assertEquals(capability.capabilityId, capabilityBranch.capabilityId)
        assertEquals(emptyMap<String, String>(), capabilityBranch.arguments)
        assertSame(subject, capabilityBranch.attributes["member-subject"])
        assertEquals(MenuActionSafety.NAVIGATION_ONLY, capabilityBranch.safetyByClick[ClickType.LEFT])
        assertEquals(MenuActionSafety.NAVIGATION_ONLY, capabilityBranch.safetyByClick[ClickType.RIGHT])
        val transferBranch = inspection.branches[1].interaction
        assertEquals(MenuRuntimeInteractionKind.ACTION, transferBranch.kind)
        assertEquals("dispatch", transferBranch.actionId)
        assertEquals(setOf(ClickType.SHIFT_LEFT), transferBranch.acceptedClicks)
        assertEquals(payload, transferBranch.arguments)
        assertEquals(MenuActionSafety.CONFIRM_ENTRY, transferBranch.safetyByClick[ClickType.SHIFT_LEFT])
        val removeBranch = inspection.branches[2].interaction
        assertEquals(setOf(ClickType.SHIFT_RIGHT), removeBranch.acceptedClicks)
        assertEquals(MenuActionSafety.CONFIRM_ENTRY, removeBranch.safetyByClick[ClickType.SHIFT_RIGHT])
        val validationErrors = MenuContractValidator.validate(
            completedDefinition,
            listOf(MenuActionObservation(20, interaction)),
            MenuContractValidationContext(capabilityService(capability)),
        )
        assertTrue(validationErrors.isEmpty(), validationErrors.joinToString())
    }

    @Test
    fun `world settings definitions and listener have no capability payload bridge`() {
        val gui = Path.of("src/main/kotlin/me/awabi2048/myworldmanager/gui/WorldSettingsGui.kt").readText()
        val listener = Path.of("src/main/kotlin/me/awabi2048/myworldmanager/listener/WorldSettingsListener.kt").readText()

        assertEquals(3, Regex("InventoryMenuDefinition\\(").findAll(gui).count())
        assertTrue("memberManagementEntryInteraction(" in gui)
        assertFalse("ACTION_CAPABILITY" in gui)
        assertFalse("ROUTE_CAPABILITY_ID" in gui)
        assertFalse("capability_id" in gui)
        assertFalse("ROUTE_CAPABILITY_ID" in listener)
        assertFalse("getMenuCapabilityService().execute(" in listener)
    }

    private fun capability(capabilityId: String): ResolvedMenuCapability = ResolvedMenuCapability(
        capabilityId = capabilityId,
        presentation = com.awabi2048.ccsystem.api.gui.MenuCapabilityPresentation(
            GuiItemSpec(Material.STONE, GuiNameSpec.Empty, GuiLoreSpec.None, GuiElementRole.ACTION, 1),
        ),
        actions = listOf(
            ResolvedMenuCapabilityAction(
                id = "edit-tools",
                trigger = MenuCapabilityTrigger.LEFT_RIGHT,
                text = "ツール権限を編集",
                safety = MenuActionSafety.NAVIGATION_ONLY,
            ),
        ),
    )

    /**
     * Runtimeの通常snapshotとinspectが共通利用する変換器を、公開診断型として検査します。
     * Kotlinのinternal実装なので、モジュール境界を越える本テストでは反射で呼び出します。
     */
    private fun interactionSnapshot(interaction: MenuInteraction): MenuRuntimeInspectionInteractionSnapshot {
        val type = Class.forName(
            "com.awabi2048.ccsystem.core.gui.MenuRuntimeInspectionInteractionSnapshotFactory",
        )
        val instance = type.getField("INSTANCE").get(null)
        val create = type.methods.single { method ->
            method.name == "create" && method.parameterTypes.contentEquals(arrayOf(MenuInteraction::class.java))
        }
        return create.invoke(instance, interaction) as MenuRuntimeInspectionInteractionSnapshot
    }

    private fun capabilityService(capability: ResolvedMenuCapability): MenuCapabilityService = object : MenuCapabilityService {
        override fun register(definition: MenuCapabilityDefinition) = Unit
        override fun unregisterOwner(owner: String) = Unit
        override fun definition(capabilityId: String): MenuCapabilityDefinition? =
            capability.takeIf { it.capabilityId == capabilityId }?.let { resolved ->
                MenuCapabilityDefinition(
                    owner = "mwm-chanpon",
                    id = resolved.capabilityId.substringAfter(":"),
                    placement = "member-management-entry",
                    availability = MenuCapabilityAvailability { true },
                    presentationProvider = MenuCapabilityPresentationProvider { resolved.presentation },
                    actions = resolved.actions.map { action ->
                        MenuCapabilityAction(
                            id = action.id,
                            trigger = action.trigger,
                            textProvider = MenuCapabilityActionTextProvider { action.text },
                            handler = MenuCapabilityActionHandler { MenuActionResult.Ignored },
                            safety = action.safety,
                        )
                    },
                )
            }
        override fun definitions(): List<MenuCapabilityDefinition> = emptyList()
        override fun definitions(placement: String): List<MenuCapabilityDefinition> = emptyList()
        override fun resolve(
            capabilityId: String,
            player: Player,
            arguments: Map<String, String>,
            attributes: Map<String, Any>,
        ): ResolvedMenuCapability? = capability.takeIf { it.capabilityId == capabilityId }
        override fun execute(
            capabilityId: String,
            player: Player,
            click: ClickType,
            arguments: Map<String, String>,
            attributes: Map<String, Any>,
        ): MenuActionResult = MenuActionResult.Ignored
    }

    private fun player(): Player = Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java),
    ) { proxy, method, arguments ->
        when (method.name) {
            "getUniqueId" -> UUID.randomUUID()
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === arguments?.singleOrNull()
            else -> throw UnsupportedOperationException(method.name)
        }
    } as Player
}
