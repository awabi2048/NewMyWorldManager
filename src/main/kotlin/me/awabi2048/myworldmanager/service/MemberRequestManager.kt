package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.event.MwmMemberAddSource
import me.awabi2048.myworldmanager.api.event.MwmMemberAddedEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

data class MemberRequestInfo(
    val requestorUuid: UUID,
    val worldUuid: UUID,
    val ownerUuid: UUID,
    val decisionId: UUID,
    val createdAt: Long
)

class MemberRequestManager(private val plugin: MyWorldManager) {
    private val languageManager = plugin.languageManager

    fun sendRequest(requestor: Player, worldUuid: UUID) {
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return
        val ownerUuid = worldData.owner

        if (ownerUuid == requestor.uniqueId ||
            worldData.moderators.contains(requestor.uniqueId) ||
            worldData.members.contains(requestor.uniqueId)
        ) {
            requestor.sendMessage(languageManager.getMessage(requestor, "messages.member_request_already_member"))
            return
        }

        requestor.sendMessage(languageManager.getMessage(requestor, "messages.member_request_sent"))

        val count = plugin.pendingDecisionManager.enqueueMemberRequest(ownerUuid, worldUuid, requestor.uniqueId)

        val owner = Bukkit.getPlayer(ownerUuid)
        if (owner != null && owner.isOnline) {
            owner.sendMessage(
                languageManager.getMessage(
                    owner,
                    "messages.member_request_received",
                    mapOf("player" to requestor.name)
                )
            )
            plugin.pendingDecisionManager.sendPendingHint(owner, count)
            plugin.soundManager.playActionSound(owner, "member_request", "received")
        }
    }

    fun handleApproval(player: Player, requestorUuid: UUID, worldUuid: UUID) {
        handleApprovalDirect(player, requestorUuid, worldUuid)
    }

    fun handleRejection(player: Player, requestorUuid: UUID, worldUuid: UUID) {
        handleRejectionDirect(player, requestorUuid, worldUuid)
    }

    fun handleApprovalDirect(player: Player, requestorUuid: UUID, worldUuid: UUID) {
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
        if (worldData == null) {
            player.sendMessage(languageManager.getMessage(player, "error.invite_world_not_found"))
            return
        }

        if (worldData.members.contains(requestorUuid) ||
            worldData.moderators.contains(requestorUuid) ||
            worldData.owner == requestorUuid
        ) {
            player.sendMessage(languageManager.getMessage(player, "error.invite_already_member"))
            return
        }

        worldData.members.add(requestorUuid)
        plugin.worldConfigRepository.save(worldData)
        Bukkit.getPluginManager().callEvent(
            MwmMemberAddedEvent(
                worldUuid = worldData.uuid,
                memberUuid = requestorUuid,
                memberName = Bukkit.getPlayer(requestorUuid)?.name ?: requestorUuid.toString(),
                addedByUuid = player.uniqueId,
                source = MwmMemberAddSource.REQUEST_APPROVE
            )
        )

        plugin.macroManager.execute("on_member_add", mapOf(
            "member" to (Bukkit.getPlayer(requestorUuid)?.name ?: requestorUuid.toString()),
            "world_name" to worldData.name,
            "world_uuid" to worldData.uuid.toString()
        ))

        val requestor = Bukkit.getPlayer(requestorUuid)
        if (requestor != null && requestor.isOnline) {
            requestor.sendMessage(languageManager.getMessage(requestor, "messages.member_request_approved"))
            plugin.soundManager.playActionSound(requestor, "member_request", "approved")
        }

        player.sendMessage(
            languageManager.getMessage(
                player,
                "messages.invite_accepted_sender",
                mapOf("player" to (requestor?.name ?: requestorUuid.toString()), "world" to worldData.name)
            )
        )
        plugin.soundManager.playActionSound(player, "member_request", "approved")
    }

    fun handleRejectionDirect(player: Player, requestorUuid: UUID, worldUuid: UUID) {
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
        if (worldData == null) {
            player.sendMessage(languageManager.getMessage(player, "error.invite_world_not_found"))
            return
        }

        val requestor = Bukkit.getPlayer(requestorUuid)
        if (requestor != null && requestor.isOnline) {
            requestor.sendMessage(languageManager.getMessage(requestor, "messages.member_request_rejected"))
            plugin.soundManager.playActionSound(requestor, "member_request", "rejected")
        }

        plugin.soundManager.playActionSound(player, "member_request", "rejected")
    }

    fun handleInternalCommand(player: Player, key: String, type: String) {
        val decisionId = runCatching { UUID.fromString(key) }.getOrNull() ?: return
        plugin.soundManager.playChatClickSound(player)
        when (type) {
            "approve" -> plugin.pendingDecisionManager.resolvePersistentById(player, decisionId, true)
            "reject" -> plugin.pendingDecisionManager.resolvePersistentById(player, decisionId, false)
        }
    }
}
