package me.awabi2048.myworldmanager.api.event

import java.lang.reflect.Proxy
import java.util.UUID
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MwmPortalSettingsAccessEventTest {
    @Test fun `アクセス拒否とハンドラー一覧を公開する`() {
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            error("想定外の呼び出し: ${method.name}")
        } as Player
        val event = MwmPortalSettingsAccessEvent(player, UUID.randomUUID())
        assertFalse(event.isCancelled)
        event.isCancelled = true
        assertTrue(event.isCancelled)
        assertSame(MwmPortalSettingsAccessEvent.getHandlerList(), event.handlers)
    }
}
