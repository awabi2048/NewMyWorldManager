package me.awabi2048.myworldmanager.service

import org.bukkit.entity.Player
import java.util.UUID

/** 同じ移動先と理由の失敗通知だけを制限し、移動の可否判定自体は抑制しません。 */
class TeleportFailureNotifier(private val now: () -> Long = System::nanoTime) {
    private data class Key(val destination: String, val reason: String)
    private val sent = mutableMapOf<UUID, MutableMap<Key, Long>>()

    @Synchronized
    internal fun shouldSend(player: UUID, destination: String, reason: String): Boolean {
        val current = now()
        val entries = sent.getOrPut(player) { mutableMapOf() }
        entries.entries.removeIf { current - it.value >= COOLDOWN_NANOS }
        val key = Key(destination, reason)
        if (entries.containsKey(key)) return false
        entries[key] = current
        return true
    }

    fun send(player: Player, destination: String, reason: String, messages: List<String>): Boolean {
        if (messages.isEmpty() || !shouldSend(player.uniqueId, destination, reason)) return false
        messages.forEach(player::sendMessage)
        return true
    }

    fun send(player: Player, destination: String, reason: String, message: String): Boolean =
        send(player, destination, reason, listOf(message))

    @Synchronized
    fun clear(player: UUID) { sent.remove(player) }

    companion object { private const val COOLDOWN_NANOS = 10_000_000_000L }
}
