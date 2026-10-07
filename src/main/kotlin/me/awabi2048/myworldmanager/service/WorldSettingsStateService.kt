package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.event.MwmMemberRole
import me.awabi2048.myworldmanager.api.event.MwmMemberRoleChangeSource
import me.awabi2048.myworldmanager.api.event.MwmMemberRoleChangedEvent
import java.util.UUID
import org.bukkit.Bukkit

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

    /**
     * メンバーの役割を切り替えます。変更は [MwmMemberRoleChangedEvent] で通知し、
     * メンバー追加・削除と同じく WorldGuard・LuckPerms 側の参加者同期を再適用させます。
     */
    fun toggleMemberRole(worldUuid: UUID, memberUuid: UUID, actorUuid: UUID? = null): Boolean {
        val world = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return false
        if (memberUuid !in world.members && memberUuid !in world.moderators) return false
        val newRole = if (memberUuid in world.moderators) {
            world.moderators.remove(memberUuid)
            world.members.add(memberUuid)
            MwmMemberRole.MEMBER
        } else {
            world.members.remove(memberUuid)
            world.moderators.add(memberUuid)
            MwmMemberRole.MODERATOR
        }
        plugin.worldConfigRepository.save(world)
        Bukkit.getPluginManager().callEvent(
            MwmMemberRoleChangedEvent(
                worldUuid = worldUuid,
                memberUuid = memberUuid,
                newRole = newRole,
                changedByUuid = actorUuid,
                source = MwmMemberRoleChangeSource.MANUAL,
            )
        )
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
        // 復元も役割変更と同じ同期経路へ戻すため、役割が残る場合だけイベントで通知します。
        if (beforeMember || beforeModerator) {
            Bukkit.getPluginManager().callEvent(
                MwmMemberRoleChangedEvent(
                    worldUuid = worldUuid,
                    memberUuid = memberUuid,
                    newRole = if (beforeModerator) MwmMemberRole.MODERATOR else MwmMemberRole.MEMBER,
                    changedByUuid = null,
                    source = MwmMemberRoleChangeSource.RESTORE,
                )
            )
        }
        return RestoreResult.RESTORED
    }

    enum class RestoreResult { RESTORED, TARGET_MISSING, CONCURRENT_CHANGE }
}
