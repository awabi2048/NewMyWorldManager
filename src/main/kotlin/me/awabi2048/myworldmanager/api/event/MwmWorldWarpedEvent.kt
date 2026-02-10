package me.awabi2048.myworldmanager.api.event

import java.util.UUID
import org.bukkit.Location
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class MwmWorldWarpedEvent(
    val playerUuid: UUID,
    val playerName: String,
    val worldUuid: UUID,
    val toLocation: Location,
    val reason: MwmWarpReason
) : Event() {
    override fun getHandlers(): HandlerList = HANDLER_LIST

    companion object {
        private val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}
