package me.awabi2048.myworldmanager.api.extension

import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.entity.Player

interface WorldPublishPolicy {
    fun getId(): String

    fun getPublishDisplayName(
        player: Player,
        worldData: WorldData,
        defaultDisplayName: String
    ): String = defaultDisplayName

    fun cyclePublishLevel(
        player: Player,
        worldData: WorldData
    ): Boolean = false
}

object DefaultWorldPublishPolicy : WorldPublishPolicy {
    override fun getId(): String = "myworldmanager.default_publish"
}
