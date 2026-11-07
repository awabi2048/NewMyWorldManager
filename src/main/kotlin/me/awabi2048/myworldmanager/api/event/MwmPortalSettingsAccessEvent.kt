package me.awabi2048.myworldmanager.api.event

import java.util.UUID
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/** ポータル設定の表示と変更直前に発行し、外部ポリシーによる制限を反映します。 */
class MwmPortalSettingsAccessEvent(val player: Player, val portalId: UUID) : Event(), Cancellable {
    private var denied = false

    override fun isCancelled(): Boolean = denied
    override fun setCancelled(cancel: Boolean) { denied = cancel }
    override fun getHandlers(): HandlerList = HANDLER_LIST

    companion object {
        private val HANDLER_LIST = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}
