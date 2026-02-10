package me.awabi2048.myworldmanager.api.event

import java.time.LocalDate
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class MwmDailyMaintenanceCompletedEvent(
    val executedDate: LocalDate,
    val updatedCount: Int,
    val archivedCount: Int,
    val totalWorlds: Int
) : Event() {
    override fun getHandlers(): HandlerList = HANDLER_LIST

    companion object {
        private val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}
