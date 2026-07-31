package me.awabi2048.myworldmanager.api.extension

import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.entity.Player

data class WorldSettingsStateContext(
    val player: Player,
    val worldData: WorldData,
    val isOwner: Boolean,
    val isModerator: Boolean,
    val isMember: Boolean,
)

enum class WorldSettingsRestriction {
    SUBMISSION_LOCKED,
    IMPORTED_ARCHIVE,
}

interface WorldSettingsStatePolicy {
    fun getId(): String

    fun restriction(
        context: WorldSettingsStateContext,
    ): WorldSettingsRestriction?
}
