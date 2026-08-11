package me.awabi2048.myworldmanager.api.extension

import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuReversibleContract
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.entity.Player
import java.util.UUID

/** Discovery画面が表示する、MWM側で認可済みの一覧スナップショットです。 */
data class DiscoveryListSnapshot(
    val worlds: List<WorldData>,
    val visitedWorldUuids: Set<UUID>,
    val favoriteWorldUuids: Set<UUID>,
    val currentWorldUuid: UUID?,
)

/** Favorite画面が表示する一覧と現在ワールドのスナップショットです。 */
data class FavoriteListSnapshot(
    val worlds: List<WorldData>,
    val currentWorldUuid: UUID?,
    val currentWorldIsFavorite: Boolean,
)

enum class DiscoveryWorldAction {
    VISIT,
    PREVIEW,
    REQUEST_MEMBERSHIP,
    TOGGLE_FAVORITE,
}

sealed interface FavoriteListAction {
    data class OpenWorldDetail(val worldUuid: UUID) : FavoriteListAction
    data class OpenOtherWorlds(val expectedCurrentWorldUuid: UUID) : FavoriteListAction
    data class ToggleCurrentFavorite(val expectedCurrentWorldUuid: UUID) : FavoriteListAction
}

/**
 * MWM所有の一覧Query/Action境界です。
 *
 * Chanpon等の差し替え画面はこのFacadeだけを利用し、Favorite上限、公開判定、確認画面、
 * プレビュー復帰、イベント、サウンドなどの既存挙動を再実装しません。
 */
interface ApiWorldListMenuService {
    fun discoverySnapshot(player: Player): DiscoveryListSnapshot

    fun favoriteSnapshot(player: Player): FavoriteListSnapshot

    fun executeDiscovery(
        player: Player,
        worldUuid: UUID,
        action: DiscoveryWorldAction,
    ): MenuActionResult

    fun executeFavorite(
        player: Player,
        action: FavoriteListAction,
    ): MenuActionResult

    /** Favorite追加をREVERSIBLEとして宣言する、MWMの安定した公開契約です。 */
    fun favoriteToggleContract(): MenuReversibleContract
}
