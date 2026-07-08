package me.awabi2048.myworldmanager.api.service

import me.awabi2048.myworldmanager.api.event.MwmMemberAddSource
import me.awabi2048.myworldmanager.service.MemberInviteInfo
import org.bukkit.entity.Player
import java.util.UUID

interface ApiMemberManager {

    fun addInvite(targetUuid: UUID, worldUuid: UUID, senderUuid: UUID): MemberInviteInfo
    fun acceptInvite(player: Player, decisionId: UUID?)
    fun sendRequest(requestor: Player, worldUuid: UUID)
    fun addMemberDirect(player: Player, worldUuid: UUID, addedByUuid: UUID?, source: MwmMemberAddSource): Boolean
}
