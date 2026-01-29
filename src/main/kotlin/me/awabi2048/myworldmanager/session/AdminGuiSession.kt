package me.awabi2048.myworldmanager.session

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
enum class ArchiveFilter(val displayKey: String) {
    ALL("gui.admin.filter.archive.all"),           // すべて
    ACTIVE_ONLY("gui.admin.filter.archive.active"), // アクティブのみ
    ARCHIVED_ONLY("gui.admin.filter.archive.archived") // アーカイブのみ
}

/**
 * 公開レベルフィルター
 */
enum class PublishFilter(val displayKey: String, val publishLevel: PublishLevel?) {
    ALL("gui.admin.filter.publish.all", null),
    PUBLIC("gui.admin.filter.publish.public", PublishLevel.PUBLIC),
    FRIEND("gui.admin.filter.publish.friend", PublishLevel.FRIEND),
    PRIVATE("gui.admin.filter.publish.private", PublishLevel.PRIVATE),
    LOCKED("gui.admin.filter.publish.locked", PublishLevel.LOCKED)
}

/**
 * プレイヤーフィルタータイプ
 */
enum class PlayerFilterType(val displayKey: String) {
    NONE("gui.admin.filter.player.none"),     // フィルターなし
    OWNER("gui.admin.filter.player.owner"),   // オーナーであるワールド
    MEMBER("gui.admin.filter.player.member")  // メンバーであるワールド
}

/**
 * ソート種別
 */
enum class AdminSortType(val displayKey: String) {
    CREATED_DESC("gui.admin.sort.created_desc"),     // 作成日（新しい順）
    CREATED_ASC("gui.admin.sort.created_asc"),       // 作成日（古い順）
    EXPIRE_ASC("gui.admin.sort.expire_asc"),         // アーカイブ予定日（近い順）
    EXPIRE_DESC("gui.admin.sort.expire_desc"),       // アーカイブ予定日（遠い順）
    EXPANSION_DESC("gui.admin.sort.expansion_desc"), // 拡張レベル（高い順）
    EXPANSION_ASC("gui.admin.sort.expansion_asc"),    // 拡張レベル（低い順）
    MSPT_DESC("gui.admin.sort.mspt_desc")            // MSPT（高い順）
}

/**
 * ポータル用ソート種別
 */
enum class PortalSortType(val displayKey: String) {
    CREATED_DESC("gui.admin_portals.sort.created_desc"), // 設置日（新しい順）
    CREATED_ASC("gui.admin_portals.sort.created_asc")    // 設置日（古い順）
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

    fun cycleArchiveFilter(playerUuid: UUID) {
        val session = getSession(playerUuid)
        val values = ArchiveFilter.values()
        session.archiveFilter = values[(session.archiveFilter.ordinal + 1) % values.size]
        session.currentPage = 0 // フィルター変更時はページをリセット
    }

    fun cyclePublishFilter(playerUuid: UUID, reverse: Boolean = false) {
        val session = getSession(playerUuid)
        val values = PublishFilter.values()
        val delta = if (reverse) values.size - 1 else 1
        session.publishFilter = values[(session.publishFilter.ordinal + delta) % values.size]
        session.currentPage = 0
    }

    fun cycleSortType(playerUuid: UUID, reverse: Boolean = false) {
        val session = getSession(playerUuid)
        var values = AdminSortType.values()
        
        // Chiyogamiが有効でない場合はMSPTソートを除外
        if (!me.awabi2048.myworldmanager.util.ChiyogamiUtil.isChiyogamiActive()) {
            values = values.filter { it != AdminSortType.MSPT_DESC }.toTypedArray()
        }
        
        val currentIndex = values.indexOf(session.sortBy).let { if (it == -1) 0 else it }
        val delta = if (reverse) values.size - 1 else 1
        session.sortBy = values[(currentIndex + delta) % values.size]
        session.currentPage = 0
    }

    fun cyclePortalSortType(playerUuid: UUID) {
        val session = getSession(playerUuid)
        val values = PortalSortType.values()
        session.portalSortBy = values[(session.portalSortBy.ordinal + 1) % values.size]
        session.portalPage = 0
    }

    fun setPlayerFilter(playerUuid: UUID, targetPlayer: UUID?, filterType: PlayerFilterType) {
        val session = getSession(playerUuid)
        session.playerFilter = targetPlayer
        session.playerFilterType = filterType
        session.currentPage = 0
    }

    fun cyclePlayerFilterType(playerUuid: UUID) {
        val session = getSession(playerUuid)
        val values = PlayerFilterType.values()
        session.playerFilterType = values[(session.playerFilterType.ordinal + 1) % values.size]
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
}
