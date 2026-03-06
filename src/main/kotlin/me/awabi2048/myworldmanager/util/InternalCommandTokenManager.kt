package me.awabi2048.myworldmanager.util

import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.permissions.PermissionAttachment
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InternalCommandTokenManager(private val plugin: MyWorldManager) {
    private data class GrantEntry(
        var expiresAt: Long,
        val attachment: PermissionAttachment
    )

    private data class TokenEntry(
        val playerUuid: UUID,
        val action: String,
        val arguments: List<String>,
        val expiresAt: Long
    )

    private val tokens = ConcurrentHashMap<String, TokenEntry>()
    private val grants = ConcurrentHashMap<UUID, GrantEntry>()

    fun buildCommand(
        player: Player,
        action: String,
        arguments: List<String> = emptyList()
    ): String {
        cleanupExpired()
        val expiresAt = System.currentTimeMillis() + timeoutMillis()
        grantTemporaryPermission(player, expiresAt)

        val token = UUID.randomUUID().toString()
        tokens[token] = TokenEntry(player.uniqueId, action, arguments, expiresAt)
        return listOf("/mwm_internal", action, token, *arguments.toTypedArray()).joinToString(" ")
    }

    fun consume(player: Player, action: String, token: String, arguments: List<String>): Boolean {
        cleanupExpired()
        val entry = tokens[token] ?: return false
        if (entry.playerUuid != player.uniqueId) {
            return false
        }
        if (entry.action != action) {
            return false
        }
        if (entry.arguments != arguments) {
            return false
        }
        if (entry.expiresAt < System.currentTimeMillis()) {
            tokens.remove(token)
            refreshTemporaryPermission(player.uniqueId)
            return false
        }

        val removed = tokens.remove(token, entry)
        refreshTemporaryPermission(player.uniqueId)
        return removed
    }

    fun cleanupExpired() {
        val now = System.currentTimeMillis()
        tokens.entries.removeIf { it.value.expiresAt < now }

        val expiredPlayers = grants.entries
            .filter { it.value.expiresAt < now }
            .map { it.key }

        expiredPlayers.forEach { revokeTemporaryPermission(it) }
    }

    fun clearAll() {
        tokens.clear()
        grants.keys.toList().forEach { revokeTemporaryPermission(it) }
    }

    private fun grantTemporaryPermission(player: Player, expiresAt: Long) {
        val existing = grants[player.uniqueId]
        if (existing != null) {
            existing.expiresAt = maxOf(existing.expiresAt, expiresAt)
            ensureAttachmentPermission(existing.attachment)
            runSync { if (player.isOnline) player.updateCommands() }
            return
        }

        runSync {
            if (!player.isOnline) {
                return@runSync
            }
            val attachment = player.addAttachment(plugin)
            ensureAttachmentPermission(attachment)
            grants[player.uniqueId] = GrantEntry(expiresAt, attachment)
            player.updateCommands()
        }
    }

    private fun revokeTemporaryPermission(playerUuid: UUID) {
        val grant = grants.remove(playerUuid) ?: return
        runSync {
            val player = Bukkit.getPlayer(playerUuid)
            grant.attachment.remove()
            if (player != null && player.isOnline) {
                player.updateCommands()
            }
        }
    }

    private fun refreshTemporaryPermission(playerUuid: UUID) {
        val latestExpiry = tokens.values
            .filter { it.playerUuid == playerUuid }
            .maxOfOrNull { it.expiresAt }

        if (latestExpiry == null) {
            revokeTemporaryPermission(playerUuid)
            return
        }

        grants[playerUuid]?.expiresAt = latestExpiry
    }

    private fun ensureAttachmentPermission(attachment: PermissionAttachment) {
        attachment.setPermission(PermissionManager.COMMAND_MWM_INTERNAL, true)
    }

    private fun timeoutMillis(): Long {
        return plugin.config.getLong("chat_click.timeout_seconds", 60L) * 1000L
    }

    private fun runSync(block: () -> Unit) {
        if (Bukkit.isPrimaryThread()) {
            block()
            return
        }

        Bukkit.getScheduler().runTask(plugin, Runnable { block() })
    }
}
