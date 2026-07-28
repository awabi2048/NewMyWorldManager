package me.awabi2048.myworldmanager.session

import com.awabi2048.ccsystem.api.gui.MenuCloseReason

internal object SettingsClosePolicy {
    fun shouldPreserveSession(reason: MenuCloseReason): Boolean =
        reason == MenuCloseReason.ROUTE_REPLACED
}
