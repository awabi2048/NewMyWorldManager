package me.awabi2048.myworldmanager.api.extension

import com.awabi2048.ccsystem.api.gui.MenuRoute
import java.util.UUID
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType

/**
 * 表示実装をMWM本体から分離するためのRoute境界です。
 *
 * Providerは画面とRouteだけを所有し、ワールドの保存や権限処理はMWMの公開APIへ委譲します。
 */
fun interface WorldSettingsRouteProvider {
    fun prepare(
        player: Player,
        worldData: WorldData,
        request: WorldSettingsNavigationRequest,
    ): MenuRoute?
}

data class PlayerWorldRouteRequest(
    val page: Int = 0,
    val showBackButton: Boolean = false,
    val targetPlayerUuid: UUID,
    val targetPlayerName: String? = null,
)

fun interface PlayerWorldRouteProvider {
    fun prepare(
        player: Player,
        request: PlayerWorldRouteRequest,
    ): MenuRoute?
}

/**
 * 独立したPresenterから呼び出せる、MWM所有のワールド設定ユースケースです。
 */
enum class WorldSettingsAction {
    WARP,
    EDIT_INFO,
    SELECT_ICON,
    SET_SPAWN,
    MANAGE_MEMBERS,
    EDIT_ANNOUNCEMENT,
    MANAGE_TOUR,
    MANAGE_PORTALS,
}

data class WorldSettingsActionRequest(
    val player: Player,
    val worldUuid: UUID,
    val action: WorldSettingsAction,
    val click: ClickType,
)

/**
 * ワールド設定の標準操作について、表示と実行が共有する操作契約です。
 */
data class WorldSettingsActionContract(
    val action: WorldSettingsAction,
    val acceptedClicks: Set<ClickType>,
    val actionable: Boolean,
)

data class PendingInteractionSummary(
    val count: Int,
    val latestCreatedAt: Long?,
)
