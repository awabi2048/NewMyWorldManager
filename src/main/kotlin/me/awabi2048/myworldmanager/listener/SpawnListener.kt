package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.repository.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import java.util.UUID

/**
 * リスポーン地点や入室時のスポーン処理を管理するリスナー。
 */
class SpawnListener(private val repository: WorldConfigRepository) : Listener {

    /**
     * プレイヤーがリスポーンする際、適切な位置にスポーンさせる。
     */
    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        val world = event.respawnLocation.world ?: return
        
        // 管理対象ワールドかチェック（命名規則：my_world.{uuid}）
        if (!world.name.startsWith("my_world.")) return

        val uuidStr = world.name.removePrefix("my_world.")
        val worldUuid = try {
            UUID.fromString(uuidStr)
        } catch (e: Exception) { return }

        val worldData = repository.findByUuid(worldUuid) ?: return
        val player = event.player
        val playerUuid = player.uniqueId

        // メンバー判定（所有者、メンバー、モデレーター）
        val isMember = playerUuid == worldData.owner || 
                       worldData.members.contains(playerUuid) || 
                       worldData.moderators.contains(playerUuid)

        if (isMember) {
            // メンバー用スポーン地点へ
            worldData.spawnPosMember?.let { event.respawnLocation = it }
        } else {
            // ゲスト用スポーン地点へ
            worldData.spawnPosGuest?.let { event.respawnLocation = it }
        }
    }

    @EventHandler
    fun onJoin(@Suppress("UNUSED_PARAMETER") event: PlayerJoinEvent) {
        // 必要に応じて初回参加時の処理を記載（現在は実装なし）。
    }
}
