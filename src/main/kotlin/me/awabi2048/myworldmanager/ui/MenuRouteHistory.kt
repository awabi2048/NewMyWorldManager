package me.awabi2048.myworldmanager.ui

import com.awabi2048.ccsystem.core.gui.MenuRouteHistory as CoreMenuRouteHistory
import java.util.UUID
import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.entity.Player

class MenuRouteHistory(private val plugin: MyWorldManager) {
    private val history = CoreMenuRouteHistory()

    fun clear(player: Player) {
        history.clear(player)
    }

    fun pushPlayerWorld(player: Player, page: Int, showBackButton: Boolean) {
        push(
            player,
            "player_world:$page:$showBackButton"
        ) { target ->
            plugin.playerWorldGui.open(target, page, showBackButton)
            true
        }
    }

    fun pushWorldSettings(
        player: Player,
        worldUuid: UUID,
        showBackButton: Boolean,
        isPlayerWorldFlow: Boolean? = null,
        parentShowBackButton: Boolean? = null
    ) {
        push(
            player,
            "world_settings:$worldUuid:$showBackButton:$isPlayerWorldFlow:$parentShowBackButton"
        ) { target ->
            val latest = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return@push false
            plugin.worldSettingsGui.open(target, latest, showBackButton, isPlayerWorldFlow, parentShowBackButton)
            true
        }
    }

    fun pushCustom(player: Player, key: String, opener: CoreMenuRouteHistory.MenuRouteOpener) {
        history.push(player, key, opener)
    }

    fun openPrevious(player: Player): Boolean {
        return history.openPrevious(player)
    }

    private fun push(player: Player, key: String, opener: (Player) -> Boolean) {
        history.push(player, key) { target -> opener(target) }
    }
}
