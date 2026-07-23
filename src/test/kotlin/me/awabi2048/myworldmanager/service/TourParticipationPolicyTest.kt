package me.awabi2048.myworldmanager.service

import java.util.UUID
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TourParticipationPolicyTest {
    @Test
    fun `members can start tours while owners and moderators remain blocked`() {
        val owner = UUID.randomUUID()
        val moderator = UUID.randomUUID()
        val member = UUID.randomUUID()
        val visitor = UUID.randomUUID()
        val worldData = WorldData(
            uuid = UUID.randomUUID(),
            name = "tour-world",
            description = "",
            icon = Material.GRASS_BLOCK,
            sourceWorld = "template",
            expireDate = "",
            owner = owner,
            members = mutableListOf(member),
            moderators = mutableListOf(moderator)
        )
        assertTrue(TourParticipationPolicy.cannotStartTour(worldData, owner))
        assertTrue(TourParticipationPolicy.cannotStartTour(worldData, moderator))
        assertFalse(TourParticipationPolicy.cannotStartTour(worldData, member))
        assertFalse(TourParticipationPolicy.cannotStartTour(worldData, visitor))
    }
}
