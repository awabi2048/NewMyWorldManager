package me.awabi2048.myworldmanager.api

import me.awabi2048.myworldmanager.api.extension.DiscoveryRouteCapability
import me.awabi2048.myworldmanager.api.extension.DiscoveryRouteRequest
import me.awabi2048.myworldmanager.api.extension.FavoriteListRouteCapability
import me.awabi2048.myworldmanager.api.extension.FavoriteListRouteRequest
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class MenuRouteCapabilityTest {
    @Test
    fun `latest discovery capability wins and unregister restores fallback`() {
        val player = player()
        val first = DiscoveryRouteCapability { _, _ -> route("first") }
        val second = DiscoveryRouteCapability { _, _ -> route("second") }
        try {
            MyWorldManagerApi.registerDiscoveryRouteCapability(first)
            MyWorldManagerApi.registerDiscoveryRouteCapability(second)
            assertEquals("second", MyWorldManagerApi.resolveDiscoveryRouteOverride(player, DiscoveryRouteRequest())?.id)
        } finally {
            MyWorldManagerApi.unregisterDiscoveryRouteCapability(second)
            MyWorldManagerApi.unregisterDiscoveryRouteCapability(first)
        }
        assertNull(MyWorldManagerApi.resolveDiscoveryRouteOverride(player, DiscoveryRouteRequest()))
    }

    @Test
    fun `favorite capability may decline and later capability is selected`() {
        val player = player()
        val declined = FavoriteListRouteCapability { _, _ -> null }
        val selected = FavoriteListRouteCapability { _, _ -> route("favorite") }
        try {
            MyWorldManagerApi.registerFavoriteListRouteCapability(selected)
            MyWorldManagerApi.registerFavoriteListRouteCapability(declined)
            assertEquals("favorite", MyWorldManagerApi.resolveFavoriteListRouteOverride(player, FavoriteListRouteRequest())?.id)
        } finally {
            MyWorldManagerApi.unregisterFavoriteListRouteCapability(declined)
            MyWorldManagerApi.unregisterFavoriteListRouteCapability(selected)
        }
        assertNull(MyWorldManagerApi.resolveFavoriteListRouteOverride(player, FavoriteListRouteRequest()))
    }

    private fun route(id: String) = com.awabi2048.ccsystem.api.gui.MenuRoute("test", id)

    private fun player(): Player = Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java),
    ) { _, method, _ ->
        when (method.returnType) {
            Boolean::class.javaPrimitiveType -> false
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            else -> null
        }
    } as Player
}
