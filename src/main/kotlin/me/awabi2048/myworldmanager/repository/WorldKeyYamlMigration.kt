package me.awabi2048.myworldmanager.repository

import java.util.UUID

/**
 * Paper 26移行前のWorldData YAMLへ、デシリアライズ前にworld_keyを追加する。
 */
object WorldKeyYamlMigration {
    fun migrate(lines: List<String>, uuid: UUID): List<String>? {
        val worldDataStart = lines.indexOfFirst { it.trimEnd() == "world_data:" }
        if (worldDataStart < 0) return null

        val worldDataEnd = (worldDataStart + 1 until lines.size)
            .firstOrNull { lines[it].isNotBlank() && !lines[it].startsWith(" ") }
            ?: lines.size
        if ((worldDataStart + 1 until worldDataEnd).any { lines[it].startsWith("  world_key:") }) {
            return null
        }

        val customWorldName = (worldDataStart + 1 until worldDataEnd)
            .map { lines[it] }
            .firstOrNull { it.startsWith("  custom_world_name:") }
            ?.substringAfter(':')
            ?.trim()
            ?.trim('\'', '"')
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?: "my_world.$uuid"
        val insertAfter = (worldDataStart + 1 until worldDataEnd)
            .lastOrNull { lines[it].startsWith("  custom_world_name:") }
            ?: worldDataStart

        return lines.toMutableList().apply {
            add(insertAfter + 1, "  world_key: minecraft:$customWorldName")
        }
    }
}
