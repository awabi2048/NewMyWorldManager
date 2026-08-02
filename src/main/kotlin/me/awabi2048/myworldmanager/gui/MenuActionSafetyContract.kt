package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.gui.GuiMenuActionIntent
import com.awabi2048.ccsystem.api.gui.MenuActionSafety
import com.awabi2048.ccsystem.api.gui.MenuGesture
import com.awabi2048.ccsystem.api.gui.MenuReversibleContract
import com.awabi2048.ccsystem.api.gui.ResolvedMenuCapability

/** MWM側で構築するGUI actionに安全区分の省略を許さない境界です。 */
internal fun menuGestureAction(
    actionId: String,
    gesture: MenuGesture,
    label: String,
    payload: Map<String, String> = emptyMap(),
    enabled: Boolean = true,
    safety: MenuActionSafety,
    reversibleContract: MenuReversibleContract? = null,
): GuiMenuActionIntent.GestureAction {
    require(safety != MenuActionSafety.UNSPECIFIED) {
        "MWM GUI action must declare MenuActionSafety: $actionId/$gesture"
    }
    require((safety == MenuActionSafety.REVERSIBLE) == (reversibleContract != null)) {
        "MWM REVERSIBLE action must declare exactly one reversible contract: $actionId/$gesture"
    }
    return GuiMenuActionIntent.GestureAction(
        actionId = actionId,
        gesture = gesture,
        label = label,
        payload = payload,
        enabled = enabled,
        safety = safety,
        reversibleContract = reversibleContract,
    )
}

/** 外部Capabilityも、実行可能なactionには安全区分を必須とします。 */
internal fun ResolvedMenuCapability.requireExplicitActionSafety(): ResolvedMenuCapability {
    require(actions.all { it.safety != MenuActionSafety.UNSPECIFIED }) {
        "MWM GUI capability must declare MenuActionSafety: $capabilityId"
    }
    return this
}
