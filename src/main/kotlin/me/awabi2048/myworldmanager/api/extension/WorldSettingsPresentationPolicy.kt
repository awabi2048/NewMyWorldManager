package me.awabi2048.myworldmanager.api.extension

import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.entity.Player

data class WorldSettingsPresentationContext(
    val player: Player,
    val worldData: WorldData,
    val isOwner: Boolean,
    val isModerator: Boolean,
    val isMember: Boolean,
)

enum class WorldSettingsLayoutMode {
    DEFAULT,
    COMPACT_LIMITED_OWNER,
    READ_ONLY_DEFAULT,
    READ_ONLY_COMPACT,
}

data class WorldSettingsPresentationDecision(
    val layoutMode: WorldSettingsLayoutMode,
)

interface WorldSettingsPresentationPolicy {
    fun getId(): String

    fun evaluate(
        context: WorldSettingsPresentationContext,
    ): WorldSettingsPresentationDecision?
}
