package me.awabi2048.myworldmanager.api.extension

import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.GameMode
import org.bukkit.entity.Player

data class WorldPlayerStateDecision(
    val gameMode: GameMode? = null,
    val buildAllowed: Boolean? = null,
    val flightAllowed: Boolean? = null
)

interface WorldPlayerStatePolicy {
    fun getId(): String

    fun getPriority(): Int = 0

    fun evaluate(player: Player, worldData: WorldData): WorldPlayerStateDecision
}
