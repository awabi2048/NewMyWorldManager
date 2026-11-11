package me.awabi2048.myworldmanager.api.event

import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/** 無人時の自動アンロードのみを制御し、明示的な保守操作には影響しません。 */
class MwmAutomaticWorldUnloadEvent(val worldData: WorldData) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        private val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}
