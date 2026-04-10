package me.awabi2048.myworldmanager.util

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.service.PendingDecisionManager
import org.bukkit.entity.Player

object InviteTargetResolver {

    enum class RejectionReason {
        LOCKED,
        SELF,
        ALREADY_MEMBER,
        ALREADY_PENDING,
        SAME_WORLD,
        BUSY,
    }

    fun getRejectionMessageKey(reason: RejectionReason): String? {
        return when (reason) {
            RejectionReason.LOCKED -> "error.invite_locked_error"
            RejectionReason.SELF -> "messages.invite_self_error"
            RejectionReason.ALREADY_MEMBER -> "messages.invite_member_error"
            RejectionReason.ALREADY_PENDING -> "messages.invite_already_sent"
            RejectionReason.SAME_WORLD -> "messages.invite_same_world_error"
            RejectionReason.BUSY -> "messages.invite_busy_error"
        }
    }

    fun collectAvailableTargets(
        plugin: MyWorldManager,
        viewer: Player,
        worldData: WorldData?
    ): List<Player> {
        if (worldData == null || worldData.publishLevel == PublishLevel.LOCKED) {
            return emptyList()
        }

        return plugin.playerVisibilityService.getVisibleOnlinePlayers(viewer)
            .filter { target -> getRejectionReason(plugin, viewer, worldData, target) == null }
            .sortedBy { it.name }
    }

    fun resolveAvailableTarget(
        plugin: MyWorldManager,
        viewer: Player,
        worldData: WorldData?,
        inputName: String
    ): Player? {
        val target = plugin.playerVisibilityService.resolveVisibleOnlinePlayer(viewer, inputName) ?: return null
        return if (getRejectionReason(plugin, viewer, worldData, target) == null) target else null
    }

    fun getRejectionReason(
        plugin: MyWorldManager,
        viewer: Player,
        worldData: WorldData?,
        target: Player
    ): RejectionReason? {
        if (worldData == null) {
            return RejectionReason.LOCKED
        }

        if (worldData.publishLevel == PublishLevel.LOCKED) {
            return RejectionReason.LOCKED
        }

        if (target.uniqueId == viewer.uniqueId) {
            return RejectionReason.SELF
        }

        if (
            worldData.owner == target.uniqueId ||
            worldData.members.contains(target.uniqueId) ||
            worldData.moderators.contains(target.uniqueId)
        ) {
            return RejectionReason.ALREADY_MEMBER
        }

        val hasPendingInvite = plugin.pendingDecisionManager.getPendingEntries(target.uniqueId)
            .any {
                it.type == PendingDecisionManager.PendingType.WORLD_INVITE &&
                    it.worldUuid == worldData.uuid
            }
        if (hasPendingInvite) {
            return RejectionReason.ALREADY_PENDING
        }

        if (target.world.uid == viewer.world.uid) {
            return RejectionReason.SAME_WORLD
        }

        val stats = plugin.playerStatsRepository.findByUuid(target.uniqueId)
        if (stats.meetStatus == "BUSY") {
            return RejectionReason.BUSY
        }

        return null
    }
}
