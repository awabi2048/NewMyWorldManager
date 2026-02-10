package me.awabi2048.myworldmanager.api.event

import java.util.UUID
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class MwmLikeSignLikedEvent(
    val worldUuid: UUID,
    val signUuid: UUID,
    val title: String,
    val likedByUuid: UUID,
    val likedByName: String,
    val source: MwmLikeSignLikeSource,
    val likeCount: Int
) : Event() {
    override fun getHandlers(): HandlerList = HANDLER_LIST

    companion object {
        private val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}
