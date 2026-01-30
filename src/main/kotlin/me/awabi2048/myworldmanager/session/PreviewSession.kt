package me.awabi2048.myworldmanager.session

import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

/**
 * プレビューセッションのデータクラス
 */
data class PreviewSession(
    val playerUuid: UUID,
    val originalLocation: Location,
    val originalGameMode: GameMode,
    val templatePath: String,
    var previewLocation: Location? = null,
    var rotationTask: BukkitTask? = null,
    var currentYaw: Float = 0f,
    val source: PreviewSource = PreviewSource.TEMPLATE_SELECTION
)

enum class PreviewSource {
    TEMPLATE_SELECTION,
    FAVORITE_MENU,
    DISCOVERY_MENU
}
