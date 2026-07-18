package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

class PendingNotificationService(
    private val plugin: MyWorldManager
) {
    fun send(
        target: Player,
        type: PendingDecisionManager.PendingType,
        actionCode: String,
        actorUuid: UUID,
        worldUuid: UUID?
    ) {
        val actorName = Bukkit.getOfflinePlayer(actorUuid).name
            ?: plugin.languageManager.getMessage(target, "general.unknown")
        val worldName = worldUuid
            ?.let(plugin.worldConfigRepository::findByUuid)
            ?.name
            ?: plugin.languageManager.getMessage(target, "general.unknown")
        val command = "/myworld confirm $actionCode"
        val key = type.name.lowercase()
        val placeholders = mapOf(
            "player" to actorName,
            "world" to worldName,
            "command" to command
        )
        val body = plugin.languageManager.getComponent(
            target,
            "messages.pending_notification.$key.body",
            placeholders
        )
        val action = plugin.languageManager.getComponent(
            target,
            "messages.pending_notification.$key.action",
            placeholders
        )
            .color(NamedTextColor.AQUA)
            .decoration(TextDecoration.UNDERLINED, true)
            .clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(
                HoverEvent.showText(
                    plugin.languageManager.getComponent(
                        target,
                        "messages.pending_notification.hover",
                        mapOf("command" to command)
                    )
                )
            )
        target.sendMessage(Component.text().append(body).append(Component.newline()).append(action).build())
    }

    fun resendPersistent(target: Player) {
        plugin.pendingDecisionManager.getPendingEntries(target.uniqueId)
            .filter(PendingDecisionManager.PendingEntryView::persistent)
            .sortedBy(PendingDecisionManager.PendingEntryView::createdAt)
            .forEach { entry ->
                send(target, entry.type, entry.actionCode, entry.actorUuid, entry.worldUuid)
            }
    }
}
