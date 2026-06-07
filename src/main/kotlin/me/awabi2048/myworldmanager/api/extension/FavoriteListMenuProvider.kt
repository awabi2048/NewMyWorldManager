package me.awabi2048.myworldmanager.api.extension

import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.entity.Player

interface FavoriteListMenuProvider {
    fun getId(): String

    fun open(player: Player, request: FavoriteListMenuRequest): Boolean
}

data class FavoriteListMenuRequest(
    val page: Int,
    val worldData: WorldData?,
    val returnToFavoriteMenu: Boolean,
    val showBackButton: Boolean
)
