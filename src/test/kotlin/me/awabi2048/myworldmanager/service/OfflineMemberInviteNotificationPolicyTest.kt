package me.awabi2048.myworldmanager.service

import java.util.UUID
import me.awabi2048.myworldmanager.model.PendingInteraction
import me.awabi2048.myworldmanager.model.PendingInteractionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OfflineMemberInviteNotificationPolicyTest {
    private val target = UUID.randomUUID()
    private val world = UUID.randomUUID()
    private val actor = UUID.randomUUID()

    private fun invite(
        id: UUID = UUID.randomUUID(),
        targetUuid: UUID = target,
        onlineAtCreation: Boolean = false,
        worldUuid: UUID = world,
    ) = PendingInteraction(
        id = id,
        type = PendingInteractionType.MEMBER_INVITE,
        targetUuid = targetUuid,
        worldUuid = worldUuid,
        actorUuid = actor,
        createdAt = 1L,
        actionCode = "code",
        targetOnlineAtCreation = onlineAtCreation,
    )

    @Test
    fun `offline invite with world name is notified`() {
        val id = UUID.randomUUID()
        val result = OfflineMemberInviteNotificationPolicy.selectUnnotified(
            pendingInteractions = listOf(invite(id = id)),
            targetUuid = target,
            notifiedInviteIds = emptySet(),
            worldNameResolver = { world.toString() },
        )
        assertEquals(listOf(id), result.map { it.inviteId })
    }

    @Test
    fun `online creation invite is skipped`() {
        val result = OfflineMemberInviteNotificationPolicy.selectUnnotified(
            pendingInteractions = listOf(invite(onlineAtCreation = true)),
            targetUuid = target,
            notifiedInviteIds = emptySet(),
            worldNameResolver = { "world" },
        )
        assertEquals(0, result.size)
    }

    @Test
    fun `notified and duplicated invites are suppressed and missing world is skipped`() {
        val notified = UUID.randomUUID()
        val dup = UUID.randomUUID()
        val missingWorld = UUID.randomUUID()
        val missing = UUID.randomUUID()
        val result = OfflineMemberInviteNotificationPolicy.selectUnnotified(
            pendingInteractions = listOf(
                invite(id = notified),
                invite(id = dup),
                invite(id = dup),
                invite(id = missing, worldUuid = missingWorld),
            ),
            targetUuid = target,
            notifiedInviteIds = setOf(notified),
            worldNameResolver = { uuid -> if (uuid == world) "world" else null },
        )
        assertEquals(listOf(dup), result.map { it.inviteId })
    }
}
