package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.gui.MenuInteraction
import com.awabi2048.ccsystem.api.gui.MenuInteractionBranch
import com.awabi2048.ccsystem.api.gui.ResolvedMenuCapability

/**
 * メンバー一覧の1枠にある外部CapabilityとMWM固有操作を、クリック単位の最終interactionへ確定します。
 *
 * Capabilityのattributesは文字列payloadへ変換せず、そのままRuntimeに渡します。
 */
internal fun memberManagementEntryInteraction(
    capability: ResolvedMenuCapability?,
    capabilityAttributes: Map<String, Any>,
    hostActions: List<MenuInteraction.Action>,
    capabilityArguments: Map<String, String> = emptyMap(),
): MenuInteraction {
    val branches = buildList {
        capability
            ?.takeIf { it.acceptedClicks.isNotEmpty() }
            ?.let { resolved ->
                add(
                    MenuInteractionBranch(
                        resolved.acceptedClicks,
                        MenuInteraction.Capability(
                            capabilityId = resolved.capabilityId,
                            arguments = capabilityArguments,
                            attributes = capabilityAttributes,
                            acceptedClicks = resolved.acceptedClicks,
                            safety = resolved.safety,
                            safetyByClick = resolved.safetyByClick,
                        ),
                    ),
                )
            }
        hostActions.forEach { action ->
            add(MenuInteractionBranch(action.acceptedClicks, action))
        }
    }
    return when (branches.size) {
        0 -> MenuInteraction.DisplayOnly
        1 -> branches.single().interaction
        else -> MenuInteraction.ClickBranches(branches)
    }
}
