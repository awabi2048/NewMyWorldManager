package me.awabi2048.myworldmanager.api.event

import java.util.UUID
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class MwmWorldVisitedEvent(
    val worldUuid: UUID,
    val worldName: String,
    val visitorUuid: UUID,
    val visitorName: String,
    val ownerUuid: UUID,
    val firstVisitToday: Boolean,
    val source: MwmVisitSource
) : Event() {
    override fun getHandlers(): HandlerList = HANDLER_LIST

    companion object {
        private val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}
