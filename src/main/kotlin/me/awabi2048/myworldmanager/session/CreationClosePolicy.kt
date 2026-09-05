package me.awabi2048.myworldmanager.session

import com.awabi2048.ccsystem.api.gui.MenuCloseReason

/** 作成GUIの一時クローズと、作成フロー終了を判定するポリシーです。 */
internal object CreationClosePolicy {
    fun shouldPreserveSession(reason: MenuCloseReason, phase: WorldCreationPhase): Boolean =
        reason == MenuCloseReason.ROUTE_REPLACED || phase in EXTERNAL_INPUT_PHASES

    private val EXTERNAL_INPUT_PHASES = setOf(
        WorldCreationPhase.SEED_INPUT,
        WorldCreationPhase.NAME_INPUT,
        WorldCreationPhase.SPAWN_INPUT,
    )
}
