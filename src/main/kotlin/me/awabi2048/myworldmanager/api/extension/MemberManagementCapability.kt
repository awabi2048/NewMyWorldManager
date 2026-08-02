package me.awabi2048.myworldmanager.api.extension

import java.util.UUID
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.entity.Player

@Deprecated("Capability境界ではWORLD_UUID_ARGUMENTとTARGET_PLAYER_UUID_ARGUMENTを使用してください")
data class MemberManagementCapabilitySubject(
    val player: Player,
    val worldData: WorldData,
    val memberUuid: UUID,
)

object MemberManagementCapabilityContract {
    const val PLACEMENT = "myworldmanager.member-management.member"
    const val WORLD_UUID_ARGUMENT = "world_uuid"
    const val TARGET_PLAYER_UUID_ARGUMENT = "target_player_uuid"
    @Deprecated("opaque attribute境界はcontract 6で廃止されました")
    const val SUBJECT_ATTRIBUTE = "myworldmanager.member-management.subject"

    @JvmStatic
    fun resolveTarget(arguments: Map<String, String>): MemberManagementCapabilityTarget? {
        val worldUuid = arguments[WORLD_UUID_ARGUMENT]?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
            ?: return null
        val targetPlayerUuid = arguments[TARGET_PLAYER_UUID_ARGUMENT]?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
            ?: return null
        return MemberManagementCapabilityTarget(worldUuid, targetPlayerUuid)
    }
}

data class MemberManagementCapabilityTarget(
    val worldUuid: UUID,
    val targetPlayerUuid: UUID,
)
