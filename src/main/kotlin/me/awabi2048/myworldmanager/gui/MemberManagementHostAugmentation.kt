package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.MenuCapabilityComposer
import com.awabi2048.ccsystem.api.gui.MenuCapabilityCompositionMode
import com.awabi2048.ccsystem.api.gui.MenuCapabilityCompositionSnapshot
import com.awabi2048.ccsystem.api.gui.MenuCapabilityDefinition
import com.awabi2048.ccsystem.api.gui.MenuAvailabilityResult
import com.awabi2048.ccsystem.api.gui.ResolvedMenuCapability

internal fun resolveMemberManagementHostAugmentation(
    definitions: List<MenuCapabilityDefinition>,
    resolve: (MenuCapabilityDefinition) -> ResolvedMenuCapability?,
): ResolvedMenuCapability? {
    val resolved = definitions.mapNotNull(resolve).filter {
        it.availabilityResult is MenuAvailabilityResult.Available
    }
    require(resolved.size <= 1) {
        "member management host placement resolved multiple capabilities: " +
            resolved.joinToString { it.capabilityId }
    }
    return resolved.singleOrNull()?.also { capability ->
        require(capability.compositionMode == MenuCapabilityCompositionMode.HOST_AUGMENTATION) {
            "member management host placement requires HOST_AUGMENTATION: ${capability.capabilityId} " +
                "was ${capability.compositionMode}"
        }
    }
}

internal data class MemberManagementHostComposition(
    val semanticLoreBlocks: List<GuiLoreBlock>,
    val snapshot: MenuCapabilityCompositionSnapshot?,
    val capabilityArguments: Map<String, String>,
)

internal fun composeMemberManagementHost(
    capability: ResolvedMenuCapability?,
    hostItem: GuiItemSpec,
    hostBlocks: List<GuiLoreBlock>,
    actionLines: List<GuiLoreLine.Interaction>,
    capabilityArguments: Map<String, String> = emptyMap(),
): MemberManagementHostComposition {
    if (capability == null) {
        return MemberManagementHostComposition(hostBlocks, null, capabilityArguments.toMap())
    }
    val composition = MenuCapabilityComposer.composeHostAugmentation(
        capability = capability,
        hostItem = hostItem,
        hostBlocks = hostBlocks,
        actions = actionLines,
    )
    val completedBlocks = (composition.lore as GuiLoreSpec.Blocks).blocks
    return MemberManagementHostComposition(
        semanticLoreBlocks = if (actionLines.isEmpty()) completedBlocks else completedBlocks.dropLast(1),
        snapshot = composition.snapshot,
        capabilityArguments = capabilityArguments.toMap(),
    )
}
