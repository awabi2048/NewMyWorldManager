package me.awabi2048.myworldmanager.service

import com.awabi2048.ccsystem.api.gui.MenuSnapshotCodec
import org.bukkit.Color
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class MwmReversibleStateCodecTest {
    @Test
    fun `portal state uses versioned deterministic opaque evidence and round trips`() {
        val state = PortalState(UUID.randomUUID(), false, true, Color.RED, Color.BLUE)

        val first = snapshot(state)
        val second = snapshot(state)
        assertEquals(first, second)
        assertEquals(setOf("schemaVersion", "providerId", "stateType", "payload"), first.keys)
        assertEquals(state, MwmReversibleStateCodec.decode(first, MwmReversibleContracts.PORTAL_STATE_PROVIDER))
    }

    @Test
    fun `display order token is detached from mutable source collections`() {
        val mutableBefore = mutableListOf(UUID.randomUUID(), UUID.randomUUID())
        val state = DisplayOrderState(UUID.randomUUID(), mutableBefore, mutableBefore.reversed())
        val captured = MenuSnapshotCodec.snapshot(state.snapshot()).value

        mutableBefore.clear()

        val decoded = MwmReversibleStateCodec.decode(captured, MwmReversibleContracts.DISPLAY_ORDER_PROVIDER)
        assertEquals(2, (decoded as DisplayOrderState).before.size)
    }

    @Test
    fun `unknown version provider type and malformed root are rejected`() {
        val valid = snapshot(PortalState(UUID.randomUUID(), false, true, Color.RED, Color.BLUE))

        listOf(
            valid - "schemaVersion",
            valid + ("extra" to true),
            valid + ("schemaVersion" to 2),
            valid + ("schemaVersion" to "1"),
            valid + ("providerId" to "unknown"),
            valid + ("stateType" to "unknown"),
        ).forEach { malformed ->
            assertThrows(MwmStateDecodeException::class.java) {
                MwmReversibleStateCodec.decode(malformed, MwmReversibleContracts.PORTAL_STATE_PROVIDER)
            }
        }
    }

    @Test
    fun `missing extra and invalid payload fields are rejected`() {
        val valid = snapshot(PortalState(UUID.randomUUID(), false, true, Color.RED, Color.BLUE))
        @Suppress("UNCHECKED_CAST")
        val payload = valid["payload"] as Map<String, Any?>
        val cases = listOf(
            valid + ("payload" to (payload - "portalId")),
            valid + ("payload" to (payload + ("extra" to true))),
            valid + ("payload" to (payload + ("beforeText" to "false"))),
        )
        cases.forEach { malformed ->
            assertThrows(MwmStateDecodeException::class.java) {
                MwmReversibleStateCodec.decode(malformed, MwmReversibleContracts.PORTAL_STATE_PROVIDER)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun snapshot(state: MwmOpaqueProviderState): Map<String, Any?> =
        state.snapshot() as Map<String, Any?>
}
