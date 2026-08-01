package me.awabi2048.myworldmanager.session

import com.awabi2048.ccsystem.api.gui.GuiCycleDirection
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.service.StandardWorldPublishCyclePlan
import me.awabi2048.myworldmanager.service.WorldPublishMetadataSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.bukkit.entity.Player
import java.lang.reflect.Proxy
import java.util.UUID

class ReversibleSnapshotBehaviorTest {
    @Test
    fun `admin menu snapshot restores the complete state without aliasing`() {
        val playerId = UUID.randomUUID()
        val manager = AdminGuiSessionManager()
        val session = manager.getSession(playerId)
        session.currentPage = 4
        session.playerFilter = UUID.randomUUID()
        val before = manager.snapshot(playerId)

        manager.cycleArchiveFilter(playerId, GuiCycleDirection.NEXT)
        val expectedAfter = manager.snapshot(playerId)
        assertEquals(0, expectedAfter.currentPage)

        manager.restore(playerId, before)
        val restored = manager.snapshot(playerId)
        assertEquals(before, restored)
        assertNotSame(before, restored)
    }

    @Test
    fun `creation snapshot deep copies supported extras and rejects unknown mutable values`() {
        val session = WorldCreationSession(UUID.randomUUID())
        session.extras["flag"] = true
        val snapshot = session.immutableSnapshot()
        session.extras["flag"] = false
        assertEquals(true, snapshot.extras["flag"])
        assertEquals(snapshot, snapshot.restore().immutableSnapshot())

        session.extras["mutable"] = mutableListOf("value")
        assertThrows(IllegalArgumentException::class.java, session::immutableSnapshot)
    }

    @Test
    fun `tour edit snapshot deep copies nested collections`() {
        val playerId = UUID.randomUUID()
        val manager = TourSessionManager()
        val session = manager.openNewEdit(playerId, UUID.randomUUID(), "name", "description")
        val before = manager.snapshotEdit(playerId)!!
        session.draft.startedPlayerUuids += UUID.randomUUID()
        session.awaitingIconPick = true

        manager.restoreEdit(playerId, before)
        assertEquals(before, manager.snapshotEdit(playerId))
        assertEquals(before.draft.startedPlayers, manager.getEdit(playerId)!!.draft.startedPlayerUuids)
    }

    @Test
    fun `settings session capture domain mutation restore rejects stale and missing sessions`() {
        val playerId = UUID.randomUUID()
        val player = player(playerId)
        val manager = SettingsSessionManager()
        manager.startSession(player, UUID.randomUUID(), SettingsAction.VIEW_SETTINGS)
        val before = manager.snapshot(playerId)!!

        val session = manager.getSession(player)!!
        session.tempWeather = "THUNDER"
        session.isAdminFlow = true
        session.expansionCost = 14
        val expectedAfter = manager.snapshot(playerId)!!

        assertTrue(manager.restoreIfCurrent(playerId, expectedAfter, before))
        assertEquals(before, manager.snapshot(playerId))

        manager.getSession(player)!!.tempWeather = "CLEAR"
        assertFalse(manager.restoreIfCurrent(playerId, expectedAfter, before))

        manager.endSession(playerId)
        assertFalse(manager.restoreIfCurrent(playerId, expectedAfter, before))
    }

    @Test
    fun `settings session snapshots preserve all non server backed state for complete CAS comparison`() {
        val session = SettingsSession(
            playerUuid = UUID.randomUUID(),
            worldUuid = UUID.randomUUID(),
            action = SettingsAction.SET_SPAWN_GUEST,
            tempWeather = "RAIN",
            expansionDirection = org.bukkit.block.BlockFace.NORTH,
            showBackButton = true,
            isAdminFlow = true,
            isPlayerWorldFlow = true,
            parentShowBackButton = true,
            externalInput = MenuExternalInput.SET_SPAWN,
            externalInputExpiresAt = 123L,
            tempBiomeId = "minecraft:plains",
            expansionCost = 12,
        )
        val snapshot = session.immutableSnapshot()
        session.tempWeather = "CLEAR"
        session.expansionCost = 13

        assertEquals("RAIN", snapshot.tempWeather)
        assertEquals(12, snapshot.expansionCost)
        assertEquals(snapshot, snapshot.restore().immutableSnapshot())
        assertNotSame(snapshot, snapshot.restore())
    }

    @Test
    fun `bedrock creation start plan records the actual non dialog after state once`() {
        val playerId = UUID.randomUUID()
        val plan = WorldCreationStartPlan(null)
        val after = WorldCreationSession(playerId, isDialogMode = false).immutableSnapshot()

        plan.complete(after)

        assertEquals(after, plan.expectedAfter)
        assertFalse(plan.expectedAfter!!.isDialogMode)
        assertThrows(IllegalStateException::class.java) { plan.complete(after) }
    }

    @Test
    fun `standard publish plan preserves complete publish metadata as its CAS after state`() {
        val playerId = UUID.randomUUID()
        val worldId = UUID.randomUUID()
        val before = WorldPublishMetadataSnapshot(PublishLevel.PRIVATE, null)
        val after = WorldPublishMetadataSnapshot(PublishLevel.PUBLIC, "2026-08-01 12:34:56")
        val plan = StandardWorldPublishCyclePlan(playerId, worldId, before)

        plan.complete(after)

        assertEquals(before, plan.before)
        assertEquals(after, plan.expectedAfter)
        assertThrows(IllegalStateException::class.java) { plan.complete(after) }
    }

    private fun player(uuid: UUID): Player = Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "getUniqueId" -> uuid
            "toString" -> "test-player-$uuid"
            else -> primitiveDefault(method.returnType)
        }
    } as Player

    private fun primitiveDefault(type: Class<*>): Any? = when (type) {
        Boolean::class.javaPrimitiveType -> false
        Byte::class.javaPrimitiveType -> 0.toByte()
        Short::class.javaPrimitiveType -> 0.toShort()
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        Char::class.javaPrimitiveType -> '\u0000'
        else -> null
    }
}
