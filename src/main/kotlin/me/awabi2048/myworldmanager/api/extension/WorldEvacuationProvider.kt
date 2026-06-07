package me.awabi2048.myworldmanager.api.extension

import org.bukkit.Location

interface WorldEvacuationProvider {
    fun getId(): String

    fun getEvacuationLocation(): Location?
}
