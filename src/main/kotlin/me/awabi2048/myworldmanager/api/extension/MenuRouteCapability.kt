package me.awabi2048.myworldmanager.api.extension

import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuActionSoundPolicy
import java.util.UUID
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.entity.Player
import com.awabi2048.ccsystem.api.gui.MenuGesture
import org.bukkit.event.inventory.ClickType

/**
 * 表示実装をMWM本体から分離するためのRoute境界です。
 *
 * Capabilityは遷移先Routeだけを公開します。MWMの画面生成、セッション、スロットには関与しません。
 */
fun interface WorldSettingsRouteCapability {
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

fun interface PlayerWorldRouteCapability {
    fun prepare(
        player: Player,
        request: PlayerWorldRouteRequest,
    ): MenuRoute?
}

/**
 * Discovery の表示実装をアドオンへ差し替えるためのRoute境界です。
 *
 * 標準MWM画面を直接変更せず、アドオンがRouteを返した場合だけその画面へ遷移します。
 * nullを返した場合はMWM標準画面へフォールバックします。
 */
data class DiscoveryRouteRequest(
    val page: Int = 0,
    val showBackButton: Boolean = false,
)

fun interface DiscoveryRouteCapability {
    fun prepare(
        player: Player,
        request: DiscoveryRouteRequest,
    ): MenuRoute?
}

/** Favorite一覧の表示実装をアドオンへ差し替えるためのRoute境界です。 */
data class FavoriteListRouteRequest(
    val page: Int = 0,
    val showBackButton: Boolean = false,
)

fun interface FavoriteListRouteCapability {
    fun prepare(
        player: Player,
        request: FavoriteListRouteRequest,
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
    val options: List<WorldSettingsActionOption>,
    val actionable: Boolean,
    val sounds: MenuActionSoundPolicy = MenuActionSoundPolicy(),
) {
    val acceptedClicks: Set<ClickType>
        get() = options.flatMapTo(linkedSetOf()) { it.gesture.clicks }
}

data class WorldSettingsActionOption(
    val gesture: MenuGesture,
)

data class PendingInteractionSummary(
    val count: Int,
    val latestCreatedAt: Long?,
)
