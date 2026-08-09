package me.awabi2048.myworldmanager.service

import java.util.UUID
import me.awabi2048.myworldmanager.api.extension.PendingOfflineMemberInvite
import me.awabi2048.myworldmanager.model.PendingInteraction
import me.awabi2048.myworldmanager.model.PendingInteractionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PendingOfflineMemberInviteQueryTest {
    @Test
    fun returnsOnlyUnresolvedInvitesCreatedWhileTargetWasOffline() {
        val targetUuid = UUID.randomUUID()
        val worldUuid = UUID.randomUUID()
        val offlineInviteId = UUID.randomUUID()
        val onlineInviteId = UUID.randomUUID()

        val result = pendingOfflineMemberInvites(
            interactions = listOf(
                PendingInteraction(
                    id = offlineInviteId,
                    type = PendingInteractionType.MEMBER_INVITE,
                    targetUuid = targetUuid,
                    worldUuid = worldUuid,
                    actorUuid = UUID.randomUUID(),
                    createdAt = 123L,
                    actionCode = "0123",
                    targetOnlineAtCreation = false,
                ),
                PendingInteraction(
                    id = onlineInviteId,
                    type = PendingInteractionType.MEMBER_INVITE,
                    targetUuid = targetUuid,
                    worldUuid = UUID.randomUUID(),
                    actorUuid = UUID.randomUUID(),
                    createdAt = 456L,
                    actionCode = "0456",
                    targetOnlineAtCreation = true,
                ),
                PendingInteraction(
                    id = UUID.randomUUID(),
                    type = PendingInteractionType.MEMBER_REQUEST,
                    targetUuid = targetUuid,
                    worldUuid = UUID.randomUUID(),
                    actorUuid = UUID.randomUUID(),
                    createdAt = 789L,
                    actionCode = "0789",
                    targetOnlineAtCreation = false,
                ),
                PendingInteraction(
                    id = UUID.randomUUID(),
                    type = PendingInteractionType.MEMBER_INVITE,
                    targetUuid = UUID.randomUUID(),
                    worldUuid = UUID.randomUUID(),
                    actorUuid = UUID.randomUUID(),
                    createdAt = 999L,
                    actionCode = "0999",
                    targetOnlineAtCreation = false,
                ),
            ),
            targetUuid = targetUuid,
        )

        assertEquals(
            listOf(
                PendingOfflineMemberInvite(
                    id = offlineInviteId,
                    worldUuid = worldUuid,
                    createdAt = 123L,
                ),
            ),
            result,
        )
    }

    @Test
    fun oldConstructorDefaultDoesNotClassifyExistingRecordAsOfflineInvite() {
        val interaction = PendingInteraction(
            id = UUID.randomUUID(),
            type = PendingInteractionType.MEMBER_INVITE,
            targetUuid = UUID.randomUUID(),
            worldUuid = UUID.randomUUID(),
            actorUuid = UUID.randomUUID(),
            createdAt = 123L,
            actionCode = "0123",
        )

        assertTrue(interaction.targetOnlineAtCreation)
        assertTrue(pendingOfflineMemberInvites(listOf(interaction), interaction.targetUuid).isEmpty())
    }
}
