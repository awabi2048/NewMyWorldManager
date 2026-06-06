package me.awabi2048.myworldmanager.api.extension

import org.bukkit.entity.Player

interface DiscoveryMenuProvider {
    fun getId(): String

    fun open(player: Player, request: DiscoveryMenuRequest): Boolean
}

data class DiscoveryMenuRequest(
    val page: Int,
    val showBackButton: Boolean
)
