package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import java.util.UUID

/** ワールド設定の小さな永続状態を、通常Actionと可逆restoreで共有します。 */
class WorldSettingsStateService(private val plugin: MyWorldManager) {
    fun toggleNotification(worldUuid: UUID): Boolean {
        val world = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return false
        world.notificationEnabled = !world.notificationEnabled
        plugin.worldConfigRepository.save(world)
        return true
    }

    fun restoreNotification(
        worldUuid: UUID,
        before: Boolean,
        expectedAfter: Boolean,
    ): RestoreResult {
        val world = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return RestoreResult.TARGET_MISSING
        if (world.notificationEnabled != expectedAfter) return RestoreResult.CONCURRENT_CHANGE
        world.notificationEnabled = before
        plugin.worldConfigRepository.save(world)
        return RestoreResult.RESTORED
    }

    fun toggleMemberRole(worldUuid: UUID, memberUuid: UUID): Boolean {
        val world = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return false
        if (memberUuid !in world.members && memberUuid !in world.moderators) return false
        if (memberUuid in world.moderators) {
            world.moderators.remove(memberUuid)
            world.members.add(memberUuid)
        } else {
            world.members.remove(memberUuid)
            world.moderators.add(memberUuid)
        }
        plugin.worldConfigRepository.save(world)
        return true
    }

    fun restoreMemberRole(
        worldUuid: UUID,
        memberUuid: UUID,
        beforeMember: Boolean,
        beforeModerator: Boolean,
        expectedMember: Boolean,
        expectedModerator: Boolean,
    ): RestoreResult {
        val world = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return RestoreResult.TARGET_MISSING
        if ((memberUuid in world.members) != expectedMember || (memberUuid in world.moderators) != expectedModerator) {
            return RestoreResult.CONCURRENT_CHANGE
        }
        if (beforeMember) world.members.add(memberUuid) else world.members.remove(memberUuid)
        if (beforeModerator) world.moderators.add(memberUuid) else world.moderators.remove(memberUuid)
        plugin.worldConfigRepository.save(world)
        return RestoreResult.RESTORED
    }

    enum class RestoreResult { RESTORED, TARGET_MISSING, CONCURRENT_CHANGE }
}
