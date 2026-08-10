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
    fun sendFavoriteGroupWorldInvite(
        target: Player,
        actionCode: String,
        actorUuid: UUID,
        worldUuid: UUID,
    ) {
        val actorName = Bukkit.getOfflinePlayer(actorUuid).name
            ?: plugin.languageManager.getMessage(target, "general.unknown")
        val worldName = plugin.worldConfigRepository.findByUuid(worldUuid)?.name
            ?: plugin.languageManager.getMessage(target, "general.unknown")
        val command = "/myworld confirm $actionCode"
        val placeholders = mapOf("player" to actorName, "world" to worldName, "command" to command)
        val body = plugin.languageManager.getComponent(target, "messages.favorite_group_invite.recipient.body", placeholders)
        val presentation = favoriteGroupInvitePresentation(plugin.playerPlatformResolver.isBedrock(target))
        val actionKey = if (!presentation.interactiveRecipientAction) {
            "messages.favorite_group_invite.recipient.bedrock_action"
        } else {
            "messages.favorite_group_invite.recipient.java_action"
        }
        var action = plugin.languageManager.getComponent(target, actionKey, placeholders)
        if (presentation.interactiveRecipientAction) {
            action = action
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(plugin.languageManager.getComponent(
                    target,
                    "messages.pending_notification.hover",
                    mapOf("command" to command),
                )))
        }
        target.sendMessage(Component.text().append(body).append(Component.newline()).append(action).build())
    }

    fun sendFavoriteGroupInviteSummary(sender: Player, batchId: UUID, recipients: List<Player>) {
        val placeholders = mapOf(
            "count" to recipients.size,
            "players" to recipients.joinToString("\n") { "§f${it.name}" },
        )
        val prefix = plugin.languageManager.getComponent(sender, "messages.favorite_group_invite.sender.prefix", placeholders)
        var count = plugin.languageManager.getComponent(sender, "messages.favorite_group_invite.sender.count", placeholders)
        val suffix = plugin.languageManager.getComponent(sender, "messages.favorite_group_invite.sender.suffix", placeholders)
        val presentation = favoriteGroupInvitePresentation(plugin.playerPlatformResolver.isBedrock(sender))
        if (!presentation.senderCancelLine) {
            sender.sendMessage(Component.text().append(prefix).append(count).append(suffix).build())
            return
        }
        // 要件どおり人数部分だけに対象者一覧のホバーを付け、取消操作とは意味を分離します。
        if (presentation.countHover) {
            count = count.hoverEvent(HoverEvent.showText(plugin.languageManager.getComponent(
                sender,
                "messages.favorite_group_invite.sender.hover",
                placeholders,
            )))
        }
        val body = Component.text().append(prefix).append(count).append(suffix).build()
        val action = plugin.languageManager
            .getComponent(sender, "messages.favorite_group_invite.sender.action", placeholders)
            .clickEvent(ClickEvent.runCommand("/favorite cancel_batch $batchId"))
        sender.sendMessage(Component.text().append(body).append(Component.newline()).append(action).build())
    }

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
        val actionText = plugin.languageManager.getComponent(
            target,
            "messages.pending_notification.$key.action",
            placeholders
        )
        val hoverText = plugin.languageManager.getComponent(
            target,
            "messages.pending_notification.hover",
            mapOf("command" to command)
        )
        val action = PendingNotificationAction.create(actionText, hoverText, actionCode)
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

internal data class FavoriteGroupInvitePresentation(
    val senderCancelLine: Boolean,
    val interactiveRecipientAction: Boolean,
    val countHover: Boolean,
)

/** 統合版にはクリック・ホバー依存の導線を出さず、承認コマンドだけを明示します。 */
internal fun favoriteGroupInvitePresentation(isBedrock: Boolean): FavoriteGroupInvitePresentation =
    if (isBedrock) {
        FavoriteGroupInvitePresentation(
            senderCancelLine = false,
            interactiveRecipientAction = false,
            countHover = false,
        )
    } else {
        FavoriteGroupInvitePresentation(
            senderCancelLine = true,
            interactiveRecipientAction = true,
            countHover = true,
        )
    }

internal object PendingNotificationAction {
    fun create(action: Component, hover: Component, actionCode: String): Component {
        val command = "/myworld confirm $actionCode"
        return action
            .color(NamedTextColor.AQUA)
            .decoration(TextDecoration.UNDERLINED, true)
            .clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(hover))
    }
}
