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
