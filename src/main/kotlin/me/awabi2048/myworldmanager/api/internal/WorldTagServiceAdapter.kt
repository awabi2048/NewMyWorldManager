package me.awabi2048.myworldmanager.api.internal

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.service.ApiWorldTagService
import org.bukkit.entity.Player

internal class WorldTagServiceAdapter(private val plugin: MyWorldManager) : ApiWorldTagService {

    override fun getEnabledTagIds(): List<String> {
        return plugin.worldTagManager.getEnabledTagIds()
    }

    override fun normalizeTagId(raw: String?): String? {
        return plugin.worldTagManager.normalizeTagId(raw)
    }

    override fun getDisplayName(player: Player?, tagId: String): String {
        return plugin.worldTagManager.getDisplayName(player, tagId)
    }
}
