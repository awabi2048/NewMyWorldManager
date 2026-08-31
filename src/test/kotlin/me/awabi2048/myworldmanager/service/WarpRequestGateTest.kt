package me.awabi2048.myworldmanager.service

import java.util.UUID
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** ワールドロード待ち中の再入場を一度だけ受け付ける契約を検証します。 */
class WarpRequestGateTest {
    @Test
    fun `同じプレイヤーの二重要求を拒否し古い解放で新しい要求を消さない`() {
        val gate = WarpRequestGate()
        val player = UUID.randomUUID()
        val firstWorld = UUID.randomUUID()
        val secondWorld = UUID.randomUUID()

        val first = requireNotNull(gate.tryAcquire(player, firstWorld))
        assertNull(gate.tryAcquire(player, firstWorld))

        gate.release(first)
        val second = requireNotNull(gate.tryAcquire(player, secondWorld))

        // Leaseにトークンがあるため、遅れて到着したfirstの解放はsecondを消しません。
        gate.release(first)
        assertNull(gate.tryAcquire(player, firstWorld))

        gate.release(second)
        assertNotNull(gate.tryAcquire(player, firstWorld))
    }

    @Test
    fun `別プレイヤーの要求は同時に受け付ける`() {
        val gate = WarpRequestGate()
        val world = UUID.randomUUID()

        assertNotNull(gate.tryAcquire(UUID.randomUUID(), world))
        assertNotNull(gate.tryAcquire(UUID.randomUUID(), world))
    }
}
