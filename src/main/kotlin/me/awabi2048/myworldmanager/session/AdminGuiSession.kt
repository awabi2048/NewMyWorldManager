package me.awabi2048.myworldmanager.session

import com.awabi2048.ccsystem.api.gui.GuiCycle
import com.awabi2048.ccsystem.api.gui.GuiCycleDirection
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiAdminKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiPortalKeys
import me.awabi2048.myworldmanager.model.PublishLevel
import java.util.UUID

/**
 * 管理者GUI用のフィルター・ソート状態を保持するセッション
 */
data class AdminGuiSession(
    var currentPage: Int = 0,
    var archiveFilter: ArchiveFilter = ArchiveFilter.ALL,
    var publishFilter: PublishFilter = PublishFilter.ALL,
    var sortBy: AdminSortType = AdminSortType.CREATED_DESC,
    var playerFilter: UUID? = null,           // 指定プレイヤーでフィルタリング
    var playerFilterType: PlayerFilterType = PlayerFilterType.NONE,

    // ポータル用
    var portalPage: Int = 0,
    var portalSortBy: PortalSortType = PortalSortType.CREATED_DESC,

    // その他
    var fromAdminMenu: Boolean = false
)

/**
 * アーカイブ状態フィルター
 */
enum class ArchiveFilter(val displayKey: LocalizationKey<String>) {
    ALL(MyworldGuiAdminKeys.GUI_ADMIN_FILTER_ARCHIVE_ALL),           // すべて
    ACTIVE_ONLY(MyworldGuiAdminKeys.GUI_ADMIN_FILTER_ARCHIVE_ACTIVE), // アクティブのみ
    ARCHIVED_ONLY(MyworldGuiAdminKeys.GUI_ADMIN_FILTER_ARCHIVE_ARCHIVED) // アーカイブのみ
}

/**
 * 公開レベルフィルター
 */
enum class PublishFilter(val displayKey: LocalizationKey<String>, val publishLevel: PublishLevel?) {
    ALL(MyworldGuiAdminKeys.GUI_ADMIN_FILTER_PUBLISH_ALL, null),
    PUBLIC(MyworldGuiAdminKeys.GUI_ADMIN_FILTER_PUBLISH_PUBLIC, PublishLevel.PUBLIC),
    FRIEND(MyworldGuiAdminKeys.GUI_ADMIN_FILTER_PUBLISH_FRIEND, PublishLevel.FRIEND),
    PRIVATE(MyworldGuiAdminKeys.GUI_ADMIN_FILTER_PUBLISH_PRIVATE, PublishLevel.PRIVATE),
    LOCKED(MyworldGuiAdminKeys.GUI_ADMIN_FILTER_PUBLISH_LOCKED, PublishLevel.LOCKED)
}

/**
 * プレイヤーフィルタータイプ
 */
enum class PlayerFilterType(val displayKey: LocalizationKey<String>) {
    NONE(MyworldGuiAdminKeys.GUI_ADMIN_FILTER_PLAYER_NONE),     // フィルターなし
    OWNER(MyworldGuiAdminKeys.GUI_ADMIN_FILTER_PLAYER_OWNER),   // オーナーであるワールド
    MEMBER(MyworldGuiAdminKeys.GUI_ADMIN_FILTER_PLAYER_MEMBER)  // メンバーであるワールド
}

/**
 * ソート種別
 */
enum class AdminSortType(val displayKey: LocalizationKey<String>) {
    CREATED_DESC(MyworldGuiAdminKeys.GUI_ADMIN_SORT_CREATED_DESC),     // 作成日（新しい順）
    CREATED_ASC(MyworldGuiAdminKeys.GUI_ADMIN_SORT_CREATED_ASC),       // 作成日（古い順）
    EXPIRE_ASC(MyworldGuiAdminKeys.GUI_ADMIN_SORT_EXPIRE_ASC),         // アーカイブ予定日（近い順）
    EXPIRE_DESC(MyworldGuiAdminKeys.GUI_ADMIN_SORT_EXPIRE_DESC),       // アーカイブ予定日（遠い順）
    WORLD_SIZE_DESC(MyworldGuiAdminKeys.GUI_ADMIN_SORT_WORLD_SIZE_DESC), // ワールドサイズ（大きい順）
    MSPT_DESC(MyworldGuiAdminKeys.GUI_ADMIN_SORT_MSPT_DESC)            // MSPT（高い順）
}

/**
 * ポータル用ソート種別
 */
enum class PortalSortType(val displayKey: LocalizationKey<String>) {
    CREATED_DESC(MyworldGuiPortalKeys.GUI_ADMIN_PORTALS_SORT_CREATED_DESC), // 設置日（新しい順）
    CREATED_ASC(MyworldGuiPortalKeys.GUI_ADMIN_PORTALS_SORT_CREATED_ASC)    // 設置日（古い順）
}

/**
 * 管理者GUIセッションマネージャー
 */
class AdminGuiSessionManager {
    private val sessions = mutableMapOf<UUID, AdminGuiSession>()

    fun getSession(playerUuid: UUID): AdminGuiSession {
        return sessions.getOrPut(playerUuid) { AdminGuiSession() }
    }

    fun setPage(playerUuid: UUID, page: Int) {
        getSession(playerUuid).currentPage = page
    }

    fun cycleArchiveFilter(playerUuid: UUID, direction: GuiCycleDirection) {
        val session = getSession(playerUuid)
        session.archiveFilter = GuiCycle.select(session.archiveFilter, ArchiveFilter.values(), direction)
        session.currentPage = 0 // フィルター変更時はページをリセット
    }

    fun snapshot(playerUuid: UUID): AdminGuiSession = getSession(playerUuid).copy()

    fun restore(playerUuid: UUID, snapshot: AdminGuiSession) {
        sessions[playerUuid] = snapshot.copy()
    }

    fun cyclePublishFilter(playerUuid: UUID, direction: GuiCycleDirection) {
        val session = getSession(playerUuid)
        session.publishFilter = GuiCycle.select(session.publishFilter, PublishFilter.values(), direction)
        session.currentPage = 0
    }

    fun cycleSortType(playerUuid: UUID, direction: GuiCycleDirection) {
        val session = getSession(playerUuid)
        session.sortBy = GuiCycle.select(session.sortBy, availableSortTypes(), direction)
        session.currentPage = 0
    }

    /** 描画・実Action・可逆providerで共有する、実行可能なソート候補です。 */
    fun availableSortTypes(): List<AdminSortType> =
        AdminSortType.entries.filter {
            it != AdminSortType.MSPT_DESC || me.awabi2048.myworldmanager.util.ChiyogamiUtil.isChiyogamiActive()
        }

    fun cyclePortalSortType(playerUuid: UUID, direction: GuiCycleDirection) {
        val session = getSession(playerUuid)
        session.portalSortBy = GuiCycle.select(session.portalSortBy, PortalSortType.values(), direction)
        session.portalPage = 0
    }

    fun setPlayerFilter(playerUuid: UUID, targetPlayer: UUID?, filterType: PlayerFilterType) {
        val session = getSession(playerUuid)
        session.playerFilter = targetPlayer
        session.playerFilterType = filterType
        session.currentPage = 0
    }

    fun cyclePlayerFilterType(playerUuid: UUID, direction: GuiCycleDirection) {
        val session = getSession(playerUuid)
        session.playerFilterType = GuiCycle.select(session.playerFilterType, PlayerFilterType.values(), direction)
        /* Preserved for usability
        if (session.playerFilterType == PlayerFilterType.NONE) {
            session.playerFilter = null
        }
        */
        session.currentPage = 0
    }

    fun clearSession(playerUuid: UUID) {
        sessions.remove(playerUuid)
    }

    fun clearAll() {
        sessions.clear()
    }
}
