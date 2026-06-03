package me.awabi2048.myworldmanager.api.extension

import org.bukkit.entity.Player

interface AdminWorldListProvider {
    fun getId(): String

    fun open(player: Player, request: AdminWorldListRequest): Boolean
}

data class AdminWorldListRequest(
    val page: Int?,
    val fromAdminMenu: Boolean?,
    val suppressSound: Boolean
)
