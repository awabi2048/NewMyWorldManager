package me.awabi2048.myworldmanager.api.extension

import java.util.UUID
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.entity.Player

data class MemberManagementCapabilitySubject(
    val player: Player,
    val worldData: WorldData,
    val memberUuid: UUID,
)

object MemberManagementCapabilityContract {
    const val PLACEMENT = "myworldmanager.member-management.member"
    const val SUBJECT_ATTRIBUTE = "myworldmanager.member-management.subject"
}
