package me.awabi2048.myworldmanager.api.extension

import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.entity.Player

interface WorldSettingsMenuProvider {
    fun getId(): String

    fun open(player: Player, worldData: WorldData, request: WorldSettingsMenuRequest): Boolean
}

data class WorldSettingsMenuRequest(
    val showBackButton: Boolean,
    val isPlayerWorldFlow: Boolean?,
    val parentShowBackButton: Boolean?
)
