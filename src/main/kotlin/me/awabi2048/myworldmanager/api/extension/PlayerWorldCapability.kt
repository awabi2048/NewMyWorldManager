package me.awabi2048.myworldmanager.api.extension

import com.awabi2048.ccsystem.api.gui.MenuRoute
import java.util.UUID
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.entity.Player

data class PlayerWorldCapabilitySubject(
    val player: Player,
    val targetPlayerUuid: UUID,
    val targetPlayerName: String?,
    val worlds: List<WorldData>,
    val showBackButton: Boolean,
    val returnRoute: MenuRoute,
)

object PlayerWorldCapabilityContract {
    const val WORLD_ITEM_PLACEMENT = "myworldmanager.player-world.world-item"
    const val SUMMARY_PLACEMENT = "myworldmanager.player-world.summary"
    const val CREATION_PLACEMENT = "myworldmanager.player-world.creation"
    const val SUBJECT_ATTRIBUTE = "myworldmanager.player-world.subject"
    const val WORLD_ATTRIBUTE = "myworldmanager.player-world.world"
}
