package me.awabi2048.myworldmanager.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

object ColorConverter {
    fun toComponent(text: String): Component {
        return LegacyComponentSerializer.legacySection()
                .deserialize(text)
                .decoration(TextDecoration.ITALIC, false)
    }
}
