package me.awabi2048.myworldmanager.api.extension

import org.bukkit.entity.Player

interface PlayerWorldMenuProvider {
    fun getId(): String

    fun open(player: Player, request: PlayerWorldMenuRequest): Boolean
}

data class PlayerWorldMenuRequest(
    val page: Int,
    val showBackButton: Boolean
)
