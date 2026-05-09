package me.awabi2048.myworldmanager.util

import org.bukkit.block.Biome

object BiomeResolver {
    @Suppress("DEPRECATION")
    fun match(name: String?): Biome? {
        if (name.isNullOrBlank()) return null
        return runCatching { Biome.valueOf(name.uppercase()) }.getOrNull()
    }
}
