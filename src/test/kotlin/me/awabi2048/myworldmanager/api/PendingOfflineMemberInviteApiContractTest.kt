package me.awabi2048.myworldmanager.api

import me.awabi2048.myworldmanager.api.extension.PendingOfflineMemberInvite
import me.awabi2048.myworldmanager.model.PendingInteraction
import me.awabi2048.myworldmanager.model.PendingInteractionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class PendingOfflineMemberInviteApiContractTest {

    @Test
    fun `legacy PendingInteraction construction remains online by default`() {
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
    }

    @Test
    fun `offline invite API data exposes only the login notification fields`() {
        val invite = PendingOfflineMemberInvite(
            id = UUID.randomUUID(),
            worldUuid = UUID.randomUUID(),
            createdAt = 456L,
        )

        assertEquals(456L, invite.createdAt)
        assertFalse(invite.worldUuid == UUID(0L, 0L))
    }
}
