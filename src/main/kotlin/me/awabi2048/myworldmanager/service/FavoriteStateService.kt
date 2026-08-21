package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.event.MwmFavoriteAddSource
import me.awabi2048.myworldmanager.api.event.MwmWorldFavoritedEvent
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import me.awabi2048.myworldmanager.util.FavoriteRegistrationTimestamp
import java.util.UUID

/** お気に入り数とプレイヤー記録を一つの永続操作として更新・復元します。 */
class FavoriteStateService(private val plugin: MyWorldManager) {
    sealed interface ToggleResult {
        data object Added : ToggleResult
        data object Removed : ToggleResult
        data object LimitReached : ToggleResult
    }

    enum class RestoreResult { RESTORED, TARGET_MISSING, CONCURRENT_CHANGE }

    companion object {
        /** 登録時刻は操作実行時に確定するため、追加操作の復元では「登録済み」を期待状態にします。 */
        const val EXPECTED_REGISTERED_TIMESTAMP = "<registered>"
    }

    fun toggle(
        player: Player,
        worldData: WorldData,
        addSource: MwmFavoriteAddSource? = null,
    ): ToggleResult {
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val originalFavoriteDate = stats.favoriteWorlds[worldData.uuid]
        val originalFavoriteCount = worldData.favorite
        val result = if (stats.favoriteWorlds.containsKey(worldData.uuid)) {
            stats.favoriteWorlds.remove(worldData.uuid)
            worldData.favorite = (worldData.favorite - 1).coerceAtLeast(0)
            ToggleResult.Removed
        } else {
            val maxFavoriteCount = plugin.config.getInt("favorite.max_count", 1000)
            if (stats.favoriteWorlds.size >= maxFavoriteCount) return ToggleResult.LimitReached
            stats.favoriteWorlds[worldData.uuid] = FavoriteRegistrationTimestamp.now()
            worldData.favorite++
            ToggleResult.Added
        }
        try {
            plugin.playerStatsRepository.save(stats)
            plugin.worldConfigRepository.save(worldData)
        } catch (failure: Throwable) {
            // 全体を拒否するため、メモリ上の変更を元に戻してから再スローする
            if (result == ToggleResult.Removed) {
                if (originalFavoriteDate != null) stats.favoriteWorlds[worldData.uuid] = originalFavoriteDate
            } else {
                stats.favoriteWorlds.remove(worldData.uuid)
            }
            worldData.favorite = originalFavoriteCount
            throw failure
        }
        if (result == ToggleResult.Added && addSource != null) {
            Bukkit.getPluginManager().callEvent(
                MwmWorldFavoritedEvent(
                    worldUuid = worldData.uuid,
                    worldName = worldData.name,
                    playerUuid = player.uniqueId,
                    playerName = player.name,
                    source = addSource,
                ),
            )
        }
        return result
    }

    fun restore(
        playerId: UUID,
        worldUuid: UUID,
        beforeDate: String?,
        expectedDate: String?,
        beforeCount: Int,
        expectedCount: Int,
    ): RestoreResult {
        val world = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return RestoreResult.TARGET_MISSING
        val stats = plugin.playerStatsRepository.findByUuid(playerId)
        val currentDate = stats.favoriteWorlds[worldUuid]
        val dateMatches = if (expectedDate == EXPECTED_REGISTERED_TIMESTAMP) {
            currentDate != null
        } else {
            currentDate == expectedDate
        }
        if (!dateMatches || world.favorite != expectedCount) {
            return RestoreResult.CONCURRENT_CHANGE
        }
        val originalDate = stats.favoriteWorlds[worldUuid]
        val originalCount = world.favorite
        if (beforeDate == null) stats.favoriteWorlds.remove(worldUuid)
        else stats.favoriteWorlds[worldUuid] = beforeDate
        world.favorite = beforeCount
        try {
            plugin.playerStatsRepository.save(stats)
            plugin.worldConfigRepository.save(world)
        } catch (failure: Throwable) {
            if (originalDate == null) stats.favoriteWorlds.remove(worldUuid) else stats.favoriteWorlds[worldUuid] = originalDate
            world.favorite = originalCount
            throw failure
        }
        return RestoreResult.RESTORED
    }
}
