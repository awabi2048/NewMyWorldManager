package me.awabi2048.myworldmanager.service

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.event.MwmMemberAddSource
import me.awabi2048.myworldmanager.api.event.MwmMemberAddedEvent
import me.awabi2048.myworldmanager.api.extension.PendingOfflineMemberInvite
import me.awabi2048.myworldmanager.model.PendingInteraction
import me.awabi2048.myworldmanager.model.PendingInteractionType
import org.bukkit.Bukkit
import me.awabi2048.myworldmanager.repository.WorldConfigRepository
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.util.UUID

data class MemberInviteInfo(
    val id: UUID,
    val worldUuid: UUID,
    val senderUuid: UUID,
    val createdAt: Long,
    val actionCode: String
)

class MemberInviteManager(
    private val plugin: MyWorldManager,
    private val worldConfigRepository: WorldConfigRepository,
    private val macroManager: MacroManager
) {
    private val languageManager = plugin.languageManager

    fun addInvite(targetUuid: UUID, worldUuid: UUID, senderUuid: UUID): MemberInviteInfo {
        // 招待作成時点の状態を記録し、ログイン時にオフライン招待だけを外部へ照会可能にします。
        val targetOnlineAtCreation = Bukkit.getPlayer(targetUuid)?.isOnline == true
        val result = plugin.pendingDecisionManager.enqueueMemberInvite(
            targetUuid = targetUuid,
            worldUuid = worldUuid,
            senderUuid = senderUuid,
            targetOnlineAtCreation = targetOnlineAtCreation,
        )
        val interaction = plugin.pendingInteractionRepository.findById(result.id)
            ?: error("Created member invite is missing: ${result.id}")
        return MemberInviteInfo(
            id = interaction.id,
            worldUuid = interaction.worldUuid,
            senderUuid = interaction.actorUuid,
            createdAt = interaction.createdAt,
            actionCode = interaction.actionCode
        )
    }

    /**
     * 外部アドオン向けに、対象がオフライン中に作成された未処理メンバー招待だけを返します。
     * PendingInteractionRepository自体は公開せず、公開APIからこのサービスを経由して照会します。
     */
    fun getPendingOfflineMemberInvites(targetUuid: UUID): List<PendingOfflineMemberInvite> =
        pendingOfflineMemberInvites(
            interactions = plugin.pendingInteractionRepository.findByTarget(targetUuid),
            targetUuid = targetUuid,
        )

    fun getInvite(targetUuid: UUID, decisionId: UUID? = null): MemberInviteInfo? {
        val interaction = plugin.pendingInteractionRepository.findByTarget(targetUuid)
            .firstOrNull {
                it.type == me.awabi2048.myworldmanager.model.PendingInteractionType.MEMBER_INVITE &&
                    (decisionId == null || it.id == decisionId)
            }
            ?: return null
        return MemberInviteInfo(
            id = interaction.id,
            worldUuid = interaction.worldUuid,
            senderUuid = interaction.actorUuid,
            createdAt = interaction.createdAt,
            actionCode = interaction.actionCode
        )
    }

    fun removeInvite(decisionId: UUID) {
        plugin.pendingInteractionRepository.remove(decisionId)
    }

    fun handleMemberInviteAccept(player: Player, decisionId: UUID? = null) {
        val lang = languageManager
        val info = getInvite(player.uniqueId, decisionId)
        if (info == null) {
            player.sendMessage(lang.getMessage(player, CommonKeys.ERROR_INVITE_EXPIRED))
            return
        }
        removeInvite(info.id)

        handleMemberInviteAcceptDirect(player, info.worldUuid, info.senderUuid)
    }

    fun handleMemberInviteAcceptDirect(player: Player, worldUuid: UUID, senderUuid: UUID) {
        val lang = languageManager

        val worldData = worldConfigRepository.findByUuid(worldUuid)
        if (worldData == null) {
            player.sendMessage(lang.getMessage(player, CommonKeys.ERROR_INVITE_WORLD_NOT_FOUND))
            return
        }

        if (worldData.members.contains(player.uniqueId) || worldData.moderators.contains(player.uniqueId) || worldData.owner == player.uniqueId) {
            player.sendMessage(lang.getMessage(player, CommonKeys.ERROR_INVITE_ALREADY_MEMBER))
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

        player.sendMessage(lang.getMessage(player, MyworldMessagesKeys.MESSAGES_INVITE_ACCEPTED_SELF, mapOf("world" to worldData.name)))
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
                    MyworldMessagesKeys.MESSAGES_MEMBER_JOINED_NOTIFY,
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

/** 公開APIへ渡す前に、対象・種別・作成時状態を一箇所で厳密に絞り込みます。 */
internal fun pendingOfflineMemberInvites(
    interactions: Iterable<PendingInteraction>,
    targetUuid: UUID,
): List<PendingOfflineMemberInvite> =
    interactions
        .asSequence()
        .filter {
            it.targetUuid == targetUuid &&
                it.type == PendingInteractionType.MEMBER_INVITE &&
                !it.targetOnlineAtCreation
        }
        .map { interaction ->
            PendingOfflineMemberInvite(
                id = interaction.id,
                worldUuid = interaction.worldUuid,
                createdAt = interaction.createdAt,
            )
        }
        .toList()
