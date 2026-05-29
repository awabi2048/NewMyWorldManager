package me.awabi2048.myworldmanager.api.service

import org.bukkit.entity.Player

interface ApiWorldTagService {

    fun getEnabledTagIds(): List<String>
    fun normalizeTagId(raw: String?): String?
    fun getDisplayName(player: Player?, tagId: String): String
}
