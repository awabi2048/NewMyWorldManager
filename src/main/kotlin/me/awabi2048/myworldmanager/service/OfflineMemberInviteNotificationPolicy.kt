package me.awabi2048.myworldmanager.service

import java.util.UUID
import me.awabi2048.myworldmanager.model.PendingInteraction
import me.awabi2048.myworldmanager.model.PendingInteractionType

data class OfflineMemberInviteNotification(
    val inviteId: UUID,
    val worldUuid: UUID,
    val worldName: String,
    val actorUuid: UUID,
    val actionCode: String,
)

/**
 * オフライン中メンバー招待のログイン時通知対象を判定する純粋ポリシーです。
 *
 * Bukkitや言語基盤から切り離し、送信済みIDの除外・重複IDの抑止・
 * ワールド名欠落時のスキップを同じ規則で検証できるようにします。
 */
object OfflineMemberInviteNotificationPolicy {
    fun selectUnnotified(
        pendingInteractions: Iterable<PendingInteraction>,
        targetUuid: UUID,
        notifiedInviteIds: Set<UUID>,
        worldNameResolver: (UUID) -> String?,
    ): List<OfflineMemberInviteNotification> {
        val seen = mutableSetOf<UUID>()
        return pendingInteractions.mapNotNull { interaction ->
            if (interaction.targetUuid != targetUuid) return@mapNotNull null
            if (interaction.type != PendingInteractionType.MEMBER_INVITE) return@mapNotNull null
            if (!interaction.targetOnlineAtCreation) {
                // オフライン中作成のみ想起対象とします。
            } else {
                return@mapNotNull null
            }
            if (interaction.id in notifiedInviteIds || !seen.add(interaction.id)) return@mapNotNull null
            val worldName = worldNameResolver(interaction.worldUuid)?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            OfflineMemberInviteNotification(
                inviteId = interaction.id,
                worldUuid = interaction.worldUuid,
                worldName = worldName,
                actorUuid = interaction.actorUuid,
                actionCode = interaction.actionCode,
            )
        }
    }
}
