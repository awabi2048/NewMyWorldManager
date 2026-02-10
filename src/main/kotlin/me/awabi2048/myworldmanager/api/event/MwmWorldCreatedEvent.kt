package me.awabi2048.myworldmanager.api.event

import java.util.UUID
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class MwmWorldCreatedEvent(
    val worldUuid: UUID,
    val worldName: String,
    val ownerUuid: UUID,
    val ownerName: String,
    val templateName: String,
    val createdAt: String
) : Event() {
    override fun getHandlers(): HandlerList = HANDLER_LIST

    companion object {
        private val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}
