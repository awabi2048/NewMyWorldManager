package me.awabi2048.myworldmanager.util

import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.entity.TextDisplay
import org.bukkit.persistence.PersistentDataType

object TextDisplayEntityUtil {
    fun spawnTaggedDisplay(
        world: World,
        location: Location,
        key: NamespacedKey,
        value: String,
        configure: (TextDisplay) -> Unit = {}
    ): TextDisplay {
        return world.spawn(location, TextDisplay::class.java) { display ->
            display.persistentDataContainer.set(key, PersistentDataType.STRING, value)
            configure(display)
        }
    }

    fun setTag(display: TextDisplay, key: NamespacedKey, value: String) {
        display.persistentDataContainer.set(key, PersistentDataType.STRING, value)
    }

    fun hasTag(display: TextDisplay, key: NamespacedKey, value: String): Boolean {
        return display.persistentDataContainer.get(key, PersistentDataType.STRING) == value
    }

    fun findNearbyTaggedDisplay(
        world: World,
        location: Location,
        radius: Double,
        key: NamespacedKey,
        value: String
    ): TextDisplay? {
        return world.getNearbyEntities(location, radius, radius, radius)
            .filterIsInstance<TextDisplay>()
            .firstOrNull { it.isValid && hasTag(it, key, value) }
    }

    fun findTaggedDisplays(world: World, key: NamespacedKey, value: String): List<TextDisplay> {
        return world.entities.filterIsInstance<TextDisplay>()
            .filter { it.isValid && hasTag(it, key, value) }
    }

    fun removeIfValid(display: TextDisplay?) {
        if (display != null && display.isValid) {
            display.remove()
        }
    }
}
