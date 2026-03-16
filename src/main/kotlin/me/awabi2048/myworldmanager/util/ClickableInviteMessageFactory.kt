package me.awabi2048.myworldmanager.util

import me.awabi2048.myworldmanager.MyWorldManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.entity.Player

object ClickableInviteMessageFactory {
    fun create(
        plugin: MyWorldManager,
        target: Player,
        messageKey: String,
        clickTextKey: String,
        hoverTextKey: String,
        placeholders: Map<String, Any>,
        action: String? = null,
        arguments: List<String> = emptyList()
    ): Component {
        val body = plugin.languageManager.getMessage(target, messageKey, placeholders)
        val clickText = plugin.languageManager.getMessage(target, clickTextKey).replace(Regex("§."), "")
        val hoverText = plugin.languageManager.getMessage(target, hoverTextKey).replace(Regex("§."), "")
        val command = if (action != null) {
            plugin.internalCommandTokenManager.buildCommand(target, action, arguments)
        } else {
            arguments.joinToString(" ")
        }

        return Component.text()
            .append(Component.text(body))
            .append(Component.newline())
            .append(
                Component.text(clickText, NamedTextColor.AQUA)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.UNDERLINED, true)
                    .clickEvent(ClickEvent.runCommand(command))
                    .hoverEvent(HoverEvent.showText(Component.text(hoverText)))
            )
            .build()
    }
}
