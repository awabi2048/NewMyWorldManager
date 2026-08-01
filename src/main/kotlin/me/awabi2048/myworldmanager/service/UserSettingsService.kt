package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.model.TourNavigationMode
import me.awabi2048.myworldmanager.repository.PlayerStatsRepository
import org.bukkit.entity.Player
import java.util.UUID

class UserSettingsService(
    private val repository: PlayerStatsRepository,
    private val tourManager: TourManager,
) {
    fun notification(playerId: UUID): Boolean = repository.findByUuid(playerId).visitorNotificationEnabled
    fun criticalVisibility(playerId: UUID): Boolean = repository.findByUuid(playerId).criticalSettingsEnabled
    fun tourNavigation(playerId: UUID): TourNavigationMode = repository.findByUuid(playerId).tourNavigationMode

    fun setNotification(playerId: UUID, enabled: Boolean) {
        repository.findByUuid(playerId).also { it.visitorNotificationEnabled = enabled; repository.save(it) }
    }

    fun setCriticalVisibility(playerId: UUID, enabled: Boolean) {
        repository.findByUuid(playerId).also { it.criticalSettingsEnabled = enabled; repository.save(it) }
    }

    fun setTourNavigation(player: Player, mode: TourNavigationMode) {
        repository.findByUuid(player.uniqueId).also { it.tourNavigationMode = mode; repository.save(it) }
        tourManager.refreshNavigation(player)
    }
}
