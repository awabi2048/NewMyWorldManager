package me.awabi2048.myworldmanager.api.extension

import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

interface VisitMenuProvider {
    fun getId(): String

    fun open(player: Player, owner: OfflinePlayer, request: VisitMenuRequest): Boolean
}

data class VisitMenuRequest(
    val page: Int,
    val worldData: WorldData?
)
