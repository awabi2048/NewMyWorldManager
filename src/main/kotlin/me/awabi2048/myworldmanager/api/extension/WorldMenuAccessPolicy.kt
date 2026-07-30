package me.awabi2048.myworldmanager.api.extension

import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.entity.Player

data class WorldMenuAccessContext(
    val player: Player,
    val worldData: WorldData,
)

data class WorldMenuAccessChallenge(
    val publicMenuRouteId: String,
    val arguments: Map<String, String> = emptyMap(),
)

interface WorldMenuAccessPolicy {
    fun getId(): String

    fun challenge(context: WorldMenuAccessContext): WorldMenuAccessChallenge?
}
