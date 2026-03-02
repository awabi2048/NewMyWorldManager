package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.event.MwmMemberAddSource
import me.awabi2048.myworldmanager.api.event.MwmMemberAddedEvent
import org.bukkit.Bukkit
import me.awabi2048.myworldmanager.repository.WorldConfigRepository
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.util.UUID

data class MemberInviteInfo(
    val id: UUID,
    val worldUuid: UUID,
    val senderUuid: UUID,
    val createdAt: Long
)

class MemberInviteManager(
    private val plugin: MyWorldManager,
    private val worldConfigRepository: WorldConfigRepository,
    private val macroManager: MacroManager
) {
    private val languageManager = plugin.languageManager

    fun addInvite(targetUuid: UUID, worldUuid: UUID, senderUuid: UUID): MemberInviteInfo {
        val interaction = plugin.pendingInteractionRepository.add(
            type = me.awabi2048.myworldmanager.model.PendingInteractionType.MEMBER_INVITE,
            targetUuid = targetUuid,
            worldUuid = worldUuid,
            actorUuid = senderUuid
        )
        return MemberInviteInfo(
            id = interaction.id,
            worldUuid = interaction.worldUuid,
            senderUuid = interaction.actorUuid,
            createdAt = interaction.createdAt
        )
    }

    fun getInvite(targetUuid: UUID): MemberInviteInfo? {
        val interaction = plugin.pendingInteractionRepository.findByTarget(targetUuid)
            .firstOrNull { it.type == me.awabi2048.myworldmanager.model.PendingInteractionType.MEMBER_INVITE }
            ?: return null
        return MemberInviteInfo(
            id = interaction.id,
            worldUuid = interaction.worldUuid,
            senderUuid = interaction.actorUuid,
            createdAt = interaction.createdAt
        )
    }

    fun removeInvite(decisionId: UUID) {
        plugin.pendingInteractionRepository.remove(decisionId)
    }

    fun handleMemberInviteAccept(player: Player) {
        val lang = languageManager
        val info = getInvite(player.uniqueId)
        if (info == null) {
            player.sendMessage(lang.getMessage(player, "error.invite_expired"))
            return
        }
        removeInvite(info.id)

        handleMemberInviteAcceptDirect(player, info.worldUuid, info.senderUuid)
    }

    fun handleMemberInviteAcceptDirect(player: Player, worldUuid: UUID, senderUuid: UUID) {
        val lang = languageManager

        val worldData = worldConfigRepository.findByUuid(worldUuid)
        if (worldData == null) {
            player.sendMessage(lang.getMessage(player, "error.invite_world_not_found"))
            return
        }

        if (worldData.members.contains(player.uniqueId) || worldData.moderators.contains(player.uniqueId) || worldData.owner == player.uniqueId) {
            player.sendMessage(lang.getMessage(player, "error.invite_already_member"))
            return
        }

        worldData.members.add(player.uniqueId)
        worldConfigRepository.save(worldData)
        Bukkit.getPluginManager().callEvent(
            MwmMemberAddedEvent(
                worldUuid = worldData.uuid,
                memberUuid = player.uniqueId,
                memberName = player.name,
                addedByUuid = senderUuid,
                source = MwmMemberAddSource.INVITE
            )
        )

        player.sendMessage(lang.getMessage(player, "messages.invite_accepted_self", mapOf("world" to worldData.name)))
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f)

        val recipients = linkedSetOf<UUID>()
        recipients.add(worldData.owner)
        recipients.addAll(worldData.moderators)
        recipients.addAll(worldData.members)
        recipients.remove(player.uniqueId)

        recipients.forEach { memberUuid ->
            val memberPlayer = Bukkit.getPlayer(memberUuid) ?: return@forEach
            if (!memberPlayer.isOnline) {
                return@forEach
            }
            memberPlayer.sendMessage(
                lang.getMessage(
                    memberPlayer,
                    "messages.member_joined_notify",
                    mapOf("player" to player.name, "world" to worldData.name)
                )
            )
            memberPlayer.playSound(memberPlayer.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f)
        }

        // マクロ実行
        macroManager.execute("on_member_add", mapOf(
            "world_uuid" to worldUuid.toString(),
            "member" to player.name
        ))
    }
}
