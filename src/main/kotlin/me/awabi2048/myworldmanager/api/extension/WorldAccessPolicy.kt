package me.awabi2048.myworldmanager.api.extension

import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

interface WorldAccessPolicy {
    fun getId(): String

    fun canShowInDiscovery(viewer: Player, worldData: WorldData): Boolean =
        worldData.publishLevel == PublishLevel.PUBLIC &&
            !worldData.isArchived &&
            worldData.sourceWorld != "CONVERT"

    fun canShowInVisitWorldList(viewer: Player, worldData: WorldData): Boolean =
        worldData.publishLevel == PublishLevel.PUBLIC &&
            !worldData.isArchived

    /** 非メンバーの第三者が、表示後に実際へ直接入場できるワールドだけを一覧へ載せます。 */
    fun canShowInGuestAccessibleWorldList(viewer: Player, worldData: WorldData): Boolean =
        canShowInVisitWorldList(viewer, worldData) &&
            canDirectWorldWarp(viewer, worldData, isMember = false)

    fun canUseVisitEntry(viewer: Player, worldData: WorldData, isMember: Boolean): Boolean =
        worldData.publishLevel == PublishLevel.PUBLIC || isMember

    fun canUseSharedEntry(viewer: Player, worldData: WorldData, isMember: Boolean): Boolean =
        worldData.publishLevel == PublishLevel.PUBLIC ||
            worldData.publishLevel == PublishLevel.FRIEND ||
            isMember

    fun canInviteToWorld(sender: Player, worldData: WorldData): Boolean =
        worldData.publishLevel != PublishLevel.LOCKED

    fun canInviteTarget(sender: Player, worldData: WorldData, target: OfflinePlayer): Boolean = true

    /**
     * お気に入り経由で、同行者を対象ワールドへ一括招待できるかを返します。
     * 通常の /invite は所属ワールドを前提としますが、この操作は第三者のワールドを対象にするため、
     * 直接ワープ可能性を独立した契約として評価します。
     */
    fun canInviteFavoriteGroup(sender: Player, worldData: WorldData, isMember: Boolean): Boolean =
        canDirectWorldWarp(sender, worldData, isMember)

    /** 一括ワープ招待の対象者に固有の制約です。メンバー招待用の作成権限判定とは分離します。 */
    fun canReceiveFavoriteGroupInvite(sender: Player, worldData: WorldData, target: Player): Boolean = true

    /**
     * 有効なワールド招待を承認した時点の運用可否です。
     * 招待は非公開ワールドへの一時入場を許すため、通常の公開入場判定は使用しません。
     */
    fun canAcceptWorldInvite(target: Player, worldData: WorldData): Boolean =
        !worldData.isArchived && worldData.publishLevel != PublishLevel.LOCKED

    /**
     * Optional, user-facing explanation supplied by an overlay policy when an
     * access decision differs from MWM's normal publish-level rules.
     */
    fun inviteToWorldDeniedMessageKey(sender: Player, worldData: WorldData): String? = null

    fun inviteTargetDeniedMessageKey(sender: Player, worldData: WorldData, target: OfflinePlayer): String? = null

    fun visitDeniedMessageKey(viewer: Player, worldData: WorldData, isMember: Boolean): String? = null

    fun enterDeniedMessageKey(player: Player, worldData: WorldData, isMember: Boolean): String? = null

    fun sharedEntryDeniedMessageKey(player: Player, worldData: WorldData, isMember: Boolean): String? = null

    fun meetDeniedMessageKey(viewer: Player, target: Player, worldData: WorldData, isMember: Boolean): String? = null

    fun canEnterWorld(player: Player, worldData: WorldData, isMember: Boolean): Boolean {
        if (worldData.isArchived) return false
        if (player.hasPermission("myworldmanager.admin") || isMember) return true
        return worldData.publishLevel != PublishLevel.LOCKED
    }

    fun canDirectWorldWarp(player: Player, worldData: WorldData, isMember: Boolean): Boolean =
        canEnterWorld(player, worldData, isMember) &&
            canUseVisitEntry(player, worldData, isMember)

    fun canRequestWorldWarp(player: Player, worldData: WorldData, isMember: Boolean): Boolean =
        !worldData.isArchived &&
            !isMember &&
            worldData.publishLevel != PublishLevel.PUBLIC &&
            worldData.publishLevel != PublishLevel.LOCKED

    fun canShowMeetTarget(viewer: Player, target: Player, worldData: WorldData, isMember: Boolean): Boolean =
        getMeetTargetAction(viewer, target, worldData, isMember) != MeetTargetAction.DENY

    fun getMeetTargetAction(viewer: Player, target: Player, worldData: WorldData, isMember: Boolean): MeetTargetAction =
        if (worldData.publishLevel == PublishLevel.PUBLIC || isMember) {
            MeetTargetAction.DIRECT
        } else {
            MeetTargetAction.DENY
        }
}

enum class MeetTargetAction {
    DIRECT,
    REQUEST,
    DENY
}

object DefaultWorldAccessPolicy : WorldAccessPolicy {
    override fun getId(): String = "myworldmanager.default_world_access"

    override fun canShowMeetTarget(viewer: Player, target: Player, worldData: WorldData, isMember: Boolean): Boolean =
        worldData.publishLevel == PublishLevel.PUBLIC ||
            worldData.publishLevel == PublishLevel.FRIEND ||
            isMember
}
