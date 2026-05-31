package me.awabi2048.myworldmanager.api.extension

import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.command.CommandSender

interface WorldDeleteGuard {
    fun getId(): String

    fun canDelete(worldData: WorldData, caller: CommandSender?): String?
}
