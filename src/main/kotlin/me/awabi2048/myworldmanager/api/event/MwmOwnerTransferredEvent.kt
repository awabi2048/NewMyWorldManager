package me.awabi2048.myworldmanager.api.event

import java.util.UUID
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class MwmOwnerTransferredEvent(
    val worldUuid: UUID,
    val oldOwnerUuid: UUID,
    val oldOwnerName: String,
    val newOwnerUuid: UUID,
    val newOwnerName: String,
    val transferredByUuid: UUID?,
    val source: MwmOwnerTransferSource
) : Event() {
    override fun getHandlers(): HandlerList = HANDLER_LIST

    companion object {
        private val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}
