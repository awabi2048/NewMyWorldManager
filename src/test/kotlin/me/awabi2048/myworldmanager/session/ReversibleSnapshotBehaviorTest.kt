package me.awabi2048.myworldmanager.session

import com.awabi2048.ccsystem.api.gui.GuiCycleDirection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
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
}
