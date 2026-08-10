package me.awabi2048.myworldmanager.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class WorldInviteCancellationMatchTest {
    private val decisionId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private val worldId = UUID.randomUUID()

    @Test
    fun `送信時に記録した招待だけが取消対象になる`() {
        assertTrue(matches(decisionId, actorId, worldId))
        assertFalse(matches(UUID.randomUUID(), actorId, worldId))
        assertFalse(matches(decisionId, UUID.randomUUID(), worldId))
        assertFalse(matches(decisionId, actorId, UUID.randomUUID()))
    }

    private fun matches(actualDecision: UUID, actualActor: UUID, actualWorld: UUID): Boolean =
        worldInviteCancellationMatches(
            actualDecision,
            actualActor,
            actualWorld,
            decisionId,
            actorId,
            worldId,
        )
}
