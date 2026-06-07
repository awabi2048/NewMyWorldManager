package me.awabi2048.myworldmanager.api.extension

import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.entity.Player

interface FavoriteMenuProvider {
    fun getId(): String

    fun open(player: Player, worldData: WorldData?): Boolean
}
