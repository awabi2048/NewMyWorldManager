package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.event.MwmFavoriteAddSource
import me.awabi2048.myworldmanager.api.event.MwmWorldFavoritedEvent
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.time.LocalDate
import java.util.UUID

/** お気に入り数とプレイヤー記録を一つの永続操作として更新・復元します。 */
class FavoriteStateService(private val plugin: MyWorldManager) {
    sealed interface ToggleResult {
        data object Added : ToggleResult
        data object Removed : ToggleResult
        data object LimitReached : ToggleResult
    }

    enum class RestoreResult { RESTORED, TARGET_MISSING, CONCURRENT_CHANGE }

    fun toggle(
        player: Player,
        worldData: WorldData,
        addSource: MwmFavoriteAddSource? = null,
    ): ToggleResult {
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val result = if (stats.favoriteWorlds.containsKey(worldData.uuid)) {
            stats.favoriteWorlds.remove(worldData.uuid)
            worldData.favorite = (worldData.favorite - 1).coerceAtLeast(0)
            ToggleResult.Removed
        } else {
            val maxFavoriteCount = plugin.config.getInt("favorite.max_count", 1000)
            if (stats.favoriteWorlds.size >= maxFavoriteCount) return ToggleResult.LimitReached
            stats.favoriteWorlds[worldData.uuid] = LocalDate.now().toString()
            worldData.favorite++
            ToggleResult.Added
        }
        plugin.playerStatsRepository.save(stats)
        plugin.worldConfigRepository.save(worldData)
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
        if (stats.favoriteWorlds[worldUuid] != expectedDate || world.favorite != expectedCount) {
            return RestoreResult.CONCURRENT_CHANGE
        }
        if (beforeDate == null) stats.favoriteWorlds.remove(worldUuid)
        else stats.favoriteWorlds[worldUuid] = beforeDate
        world.favorite = beforeCount
        plugin.playerStatsRepository.save(stats)
        plugin.worldConfigRepository.save(world)
        return RestoreResult.RESTORED
    }
}
