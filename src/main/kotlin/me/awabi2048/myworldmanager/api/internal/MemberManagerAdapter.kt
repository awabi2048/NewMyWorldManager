package me.awabi2048.myworldmanager.api.internal

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.event.MwmMemberAddSource
import me.awabi2048.myworldmanager.api.event.MwmMemberAddedEvent
import me.awabi2048.myworldmanager.api.service.ApiMemberManager
import me.awabi2048.myworldmanager.service.MemberInviteInfo
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

internal class MemberManagerAdapter(private val plugin: MyWorldManager) : ApiMemberManager {

    override fun addInvite(targetUuid: UUID, worldUuid: UUID, senderUuid: UUID): MemberInviteInfo {
        return plugin.memberInviteManager.addInvite(targetUuid, worldUuid, senderUuid)
    }

    override fun acceptInvite(player: Player, decisionId: UUID?) {
        plugin.memberInviteManager.handleMemberInviteAccept(player, decisionId)
    }

    override fun sendRequest(requestor: Player, worldUuid: UUID) {
        plugin.memberRequestManager.sendRequest(requestor, worldUuid)
    }

    override fun addMemberDirect(
        player: Player,
        worldUuid: UUID,
        addedByUuid: UUID?,
        source: MwmMemberAddSource,
        providerId: String?,
        detail: String?
    ): Boolean {
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return false
        if (worldData.owner == player.uniqueId ||
            player.uniqueId in worldData.moderators ||
            player.uniqueId in worldData.members
        ) {
            return false
        }

        worldData.members.add(player.uniqueId)
        plugin.worldConfigRepository.save(worldData)
        Bukkit.getPluginManager().callEvent(
            MwmMemberAddedEvent(
                worldUuid = worldData.uuid,
                memberUuid = player.uniqueId,
                memberName = player.name,
                addedByUuid = addedByUuid,
                source = source,
                providerId = providerId,
                detail = detail
            )
        )
        plugin.macroManager.execute("on_member_add", mapOf(
            "world_uuid" to worldUuid.toString(),
            "member" to player.name
        ))
        return true
    }
}
