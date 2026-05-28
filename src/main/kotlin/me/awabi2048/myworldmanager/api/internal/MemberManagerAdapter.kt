package me.awabi2048.myworldmanager.api.internal

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.service.ApiMemberManager
import me.awabi2048.myworldmanager.service.MemberInviteInfo
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
}
