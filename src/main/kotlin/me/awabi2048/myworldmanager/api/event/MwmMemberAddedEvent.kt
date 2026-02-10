package me.awabi2048.myworldmanager.api.event

import java.util.UUID
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class MwmMemberAddedEvent(
    val worldUuid: UUID,
    val memberUuid: UUID,
    val memberName: String,
    val addedByUuid: UUID?,
    val source: MwmMemberAddSource
) : Event() {
    override fun getHandlers(): HandlerList = HANDLER_LIST

    companion object {
        private val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}
