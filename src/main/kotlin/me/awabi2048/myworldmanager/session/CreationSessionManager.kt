package me.awabi2048.myworldmanager.session

import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CreationSessionManager(private val plugin: MyWorldManager) {
    private val sessions = ConcurrentHashMap<UUID, WorldCreationSession>()
    private var timeoutTask: BukkitTask? = null

    init {
        startTimeoutChecker()
    }

    fun startSession(playerId: UUID): WorldCreationSession {
        val session = WorldCreationSession(playerId)
        sessions[playerId] = session
        return session
    }

    fun getSession(playerId: UUID): WorldCreationSession? {
        return sessions[playerId]
    }

    fun endSession(playerId: UUID) {
        sessions.remove(playerId)
    }

    fun updateSession(playerId: UUID, updater: (WorldCreationSession) -> Unit) {
        sessions[playerId]?.let { 
            it.touch()
            updater(it) 
        }
    }

    /**
     * セッションの最終操作時間を更新
     */
    fun touchSession(playerId: UUID) {
        sessions[playerId]?.touch()
    }

    /**
     * タイムアウトチェッカーを開始
     */
    private fun startTimeoutChecker() {
        // 1分ごとにチェック
        timeoutTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val timeoutSeconds = plugin.config.getInt("creation_session.timeout_seconds", 300)
            val timeoutMs = timeoutSeconds * 1000L
            val now = System.currentTimeMillis()

            val timedOutPlayers = sessions.filter { (_, session) ->
                now - session.lastActivity > timeoutMs
            }.keys.toList()

            for (playerId in timedOutPlayers) {
                sessions.remove(playerId)
                val player = Bukkit.getPlayer(playerId)
                if (player != null && player.isOnline) {
                    player.closeInventory()
                    player.sendMessage(plugin.languageManager.getMessage(player, "messages.creation_timeout"))
                }
            }
        }, 20L * 60, 20L * 60) // 1分ごと
    }

    /**
     * タイムアウトチェッカーを停止（プラグイン無効化時用）
     */
    fun stopTimeoutChecker() {
        timeoutTask?.cancel()
        timeoutTask = null
    }
}
