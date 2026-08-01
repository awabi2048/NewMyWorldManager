package me.awabi2048.myworldmanager.session

import com.awabi2048.ccsystem.CCSystem

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.service.BoundedReversiblePlanRegistry
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CreationSessionManager(private val plugin: MyWorldManager) {
    private val sessions = ConcurrentHashMap<UUID, WorldCreationSession>()
    private val reversibleStartPlans = BoundedReversiblePlanRegistry<UUID, WorldCreationStartPlan>(
        idOf = WorldCreationStartPlan::id,
    )
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

    /** Bedrock作成開始の実更新と同じ経路で、capture済みafter snapshotを確定します。 */
    fun startBedrockSession(playerId: UUID): WorldCreationSession {
        val session = startSession(playerId)
        session.isDialogMode = false
        reversibleStartPlans.detachKey(playerId)?.complete(session.immutableSnapshot())
        return session
    }

    fun captureBedrockStart(playerId: UUID): WorldCreationStartPlan {
        val plan = WorldCreationStartPlan(snapshot(playerId), playerId)
        reversibleStartPlans.register(playerId, plan)
        return plan
    }

    internal fun consumeBedrockStartPlan(id: UUID): WorldCreationStartPlan? {
        return reversibleStartPlans.consume(id)
    }

    fun snapshot(playerId: UUID): WorldCreationSessionSnapshot? = sessions[playerId]?.immutableSnapshot()

    fun restore(playerId: UUID, snapshot: WorldCreationSessionSnapshot?) {
        require(snapshot == null || snapshot.playerId == playerId) { "creation session player mismatch" }
        if (snapshot == null) sessions.remove(playerId) else sessions[playerId] = snapshot.restore()
    }

    fun clearAll() {
        sessions.clear()
        clearReversiblePlans()
    }

    fun clearReversiblePlans() = reversibleStartPlans.clear()

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
                    CCSystem.getAPI().getMenuRuntimeService().close(player)
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

class WorldCreationStartPlan internal constructor(
    val before: WorldCreationSessionSnapshot?,
    val playerId: UUID? = before?.playerId,
    val id: UUID = UUID.randomUUID(),
) {
    var expectedAfter: WorldCreationSessionSnapshot? = null
        private set

    internal fun complete(after: WorldCreationSessionSnapshot) {
        check(expectedAfter == null) { "creation start plan is already complete" }
        expectedAfter = after
    }
}
