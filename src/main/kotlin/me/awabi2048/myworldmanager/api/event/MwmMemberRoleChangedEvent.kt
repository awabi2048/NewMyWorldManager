package me.awabi2048.myworldmanager.api.event

import java.util.UUID
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * メンバーの役割がメンバー⇔モデレータで切り替わったときに発行されます。
 * WorldGuard・LuckPerms・作業グループ等の参加者状態は、このイベントを契機に再同期されます。
 */
class MwmMemberRoleChangedEvent(
    val worldUuid: UUID,
    val memberUuid: UUID,
    val newRole: MwmMemberRole,
    val changedByUuid: UUID?,
    val source: MwmMemberRoleChangeSource,
) : Event() {
    override fun getHandlers(): HandlerList = HANDLER_LIST

    companion object {
        private val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}
