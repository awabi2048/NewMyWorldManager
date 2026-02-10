package me.awabi2048.myworldmanager.api.event

import java.util.UUID
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class MwmWorldDeletedEvent(
    val worldUuid: UUID,
    val ownerUuid: UUID,
    val refundPoints: Int,
    val wasArchived: Boolean
) : Event() {
    override fun getHandlers(): HandlerList = HANDLER_LIST

    companion object {
        private val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}
