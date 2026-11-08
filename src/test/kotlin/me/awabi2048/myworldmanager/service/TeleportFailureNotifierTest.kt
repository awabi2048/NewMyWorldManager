package me.awabi2048.myworldmanager.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class TeleportFailureNotifierTest {
    @Test
    fun `同一の移動先と理由は10秒間抑制し他の通知は抑制しない`() {
        var time = 0L
        val limiter = TeleportFailureNotifier { time }
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        assertTrue(limiter.shouldSend(first, "world-a", "locked"))
        assertFalse(limiter.shouldSend(first, "world-a", "locked"))
        assertTrue(limiter.shouldSend(first, "world-b", "locked"))
        assertTrue(limiter.shouldSend(first, "world-a", "denied"))
        assertTrue(limiter.shouldSend(second, "world-a", "locked"))
        time = 9_999_999_999L
        assertFalse(limiter.shouldSend(first, "world-a", "locked"))
        time = 10_000_000_000L
        assertTrue(limiter.shouldSend(first, "world-a", "locked"))
        limiter.clear(first)
        assertTrue(limiter.shouldSend(first, "world-a", "locked"))
    }
}
