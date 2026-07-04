package me.awabi2048.myworldmanager.session

import com.awabi2048.ccsystem.api.gui.GuiCycle
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
    WORLD_SIZE_DESC("gui.admin.sort.world_size_desc"), // ワールドサイズ（大きい順）
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

    fun cycleArchiveFilter(playerUuid: UUID, reverse: Boolean = false) {
        val session = getSession(playerUuid)
        session.archiveFilter = GuiCycle.select(session.archiveFilter, ArchiveFilter.values(), reverse)
        session.currentPage = 0 // フィルター変更時はページをリセット
    }

    fun cyclePublishFilter(playerUuid: UUID, reverse: Boolean = false) {
        val session = getSession(playerUuid)
        session.publishFilter = GuiCycle.select(session.publishFilter, PublishFilter.values(), reverse)
        session.currentPage = 0
    }

    fun cycleSortType(playerUuid: UUID, reverse: Boolean = false) {
        val session = getSession(playerUuid)
        var values = AdminSortType.values()
            .filter { it != AdminSortType.EXPIRE_ASC && it != AdminSortType.EXPIRE_DESC }
            .toTypedArray()
        
        // Chiyogamiが有効でない場合はMSPTソートを除外
        if (!me.awabi2048.myworldmanager.util.ChiyogamiUtil.isChiyogamiActive()) {
            values = values.filter { it != AdminSortType.MSPT_DESC }.toTypedArray()
        }
        
        session.sortBy = GuiCycle.select(session.sortBy, values, reverse)
        session.currentPage = 0
    }

    fun cyclePortalSortType(playerUuid: UUID, reverse: Boolean = false) {
        val session = getSession(playerUuid)
        session.portalSortBy = GuiCycle.select(session.portalSortBy, PortalSortType.values(), reverse)
        session.portalPage = 0
    }

    fun setPlayerFilter(playerUuid: UUID, targetPlayer: UUID?, filterType: PlayerFilterType) {
        val session = getSession(playerUuid)
        session.playerFilter = targetPlayer
        session.playerFilterType = filterType
        session.currentPage = 0
    }

    fun cyclePlayerFilterType(playerUuid: UUID, reverse: Boolean = false) {
        val session = getSession(playerUuid)
        session.playerFilterType = GuiCycle.select(session.playerFilterType, PlayerFilterType.values(), reverse)
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
