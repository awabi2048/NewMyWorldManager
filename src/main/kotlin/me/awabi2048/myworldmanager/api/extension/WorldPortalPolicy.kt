package me.awabi2048.myworldmanager.api.extension

import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.entity.Player

interface WorldPortalPolicy {
    fun getId(): String

    fun isPortalOpen(destination: WorldData): Boolean = true

    fun canUsePortal(player: Player, destination: WorldData): Boolean = isPortalOpen(destination)

    fun blockedMessages(player: Player, destination: WorldData): List<String> = emptyList()
}

object DefaultWorldPortalPolicy : WorldPortalPolicy {
    override fun getId(): String = "default"
}
