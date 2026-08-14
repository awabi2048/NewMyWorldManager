package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
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

    fun getRejectionMessageKey(reason: RejectionReason): LocalizationKey<String> {
        return when (reason) {
            RejectionReason.LOCKED -> CommonKeys.ERROR_INVITE_LOCKED_ERROR
            RejectionReason.SELF -> MyworldMessagesKeys.MESSAGES_INVITE_SELF_ERROR
            RejectionReason.ALREADY_MEMBER -> MyworldMessagesKeys.MESSAGES_INVITE_MEMBER_ERROR
            RejectionReason.ALREADY_PENDING -> MyworldMessagesKeys.MESSAGES_INVITE_ALREADY_SENT
            RejectionReason.SAME_WORLD -> MyworldMessagesKeys.MESSAGES_INVITE_SAME_WORLD_ERROR
            RejectionReason.BUSY -> MyworldMessagesKeys.MESSAGES_INVITE_BUSY_ERROR
        }
    }

    fun collectAvailableTargets(
        plugin: MyWorldManager,
        viewer: Player,
        worldData: WorldData?
    ): List<Player> {
        if (worldData == null || !MyWorldManagerApi.getWorldAccessPolicy().canInviteToWorld(viewer, worldData)) {
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

        if (!MyWorldManagerApi.getWorldAccessPolicy().canInviteToWorld(viewer, worldData)) {
            return RejectionReason.LOCKED
        }

        if (!MyWorldManagerApi.getWorldAccessPolicy().canInviteTarget(viewer, worldData, target)) {
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
