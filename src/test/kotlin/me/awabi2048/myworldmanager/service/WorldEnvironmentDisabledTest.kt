package me.awabi2048.myworldmanager.service

import java.util.UUID
import me.awabi2048.myworldmanager.MyWorldManager


import me.awabi2048.myworldmanager.model.ManagedDimension
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.listener.EnvironmentLogicListener


import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class WorldEnvironmentDisabledTest {
    private val plugin = mock(MyWorldManager::class.java)
    private val service = WorldEnvironmentService(plugin)
    private val world = mock(World::class.java)
    private val data = WorldData(
        uuid = UUID.randomUUID(), dimension = ManagedDimension.OVERWORLD,
        name = "external", description = "", icon = Material.GRASS_BLOCK,
        sourceWorld = "CONVERT", expireDate = "", owner = UUID.randomUUID(),
        fixedTime = 6000, fixedWeather = "RAIN", fixedScale = 2.0, gravityValue = 0.02,
    )

    @Test
    fun `standalone all does not alter time weather attributes or flight`() {
        // Paper 26 game rules are registry-backed; supply registries without a server.
        val access = mock(io.papermc.paper.registry.RegistryAccess::class.java, RETURNS_DEEP_STUBS)
        val bridge = mock(io.papermc.paper.InternalAPIBridge::class.java)
        mockStatic(io.papermc.paper.InternalAPIBridge::class.java).use { bridgeStatic ->
            bridgeStatic.`when`<io.papermc.paper.InternalAPIBridge> {
                io.papermc.paper.InternalAPIBridge.get()
            }.thenReturn(bridge)
            mockStatic(io.papermc.paper.registry.RegistryAccess::class.java).use { registry ->
            registry.`when`<io.papermc.paper.registry.RegistryAccess> {
                io.papermc.paper.registry.RegistryAccess.registryAccess()
            }.thenReturn(access)
            val gameRulesRegistry = org.bukkit.Registry.GAME_RULE
            doAnswer { mock(org.bukkit.GameRule::class.java) }.`when`(gameRulesRegistry)
                .getOrThrow(any(net.kyori.adventure.key.Key::class.java))
            `when`(world.name).thenReturn("external")
            assertTrue(service.applyAll(world, data))
            data.fixedTime = null
            repeat(3) { assertTrue(service.applyAll(world, data)) }
            verify(world, never()).setTime(anyLong())
            verify(world, never()).getEntities()
            verify(world, never()).getPlayers()
            verify(world, never()).setStorm(anyBoolean())
            verifyNoInteractions(plugin)
        }
        }
    }

    @Test
    fun `standalone individual apis do not resolve or mutate worlds`() {
        val player = mock(Player::class.java)
        assertFalse(service.applyWeather(data.uuid))
        assertFalse(service.applyTime(data.uuid))
        assertFalse(service.applyAttributes(data.uuid))
        assertFalse(service.applyFlight(data.uuid))
        assertFalse(service.applyWeather(world))
        assertFalse(service.applyTime(world))
        assertFalse(service.applyFlight(player, "external"))
        assertNull(service.computeFlightAllowed(player, "external"))
        service.applyAttributes(player, "external")
        verifyNoInteractions(plugin, player, world)
    }

    @Test
    fun `join world change and chunk load do not enter environment processing`() {
        `when`(plugin.worldEnvironmentService).thenReturn(service)
        val listener = EnvironmentLogicListener(plugin)
        val join = mock(org.bukkit.event.player.PlayerJoinEvent::class.java)
        val change = mock(org.bukkit.event.player.PlayerChangedWorldEvent::class.java)
        val chunk = mock(org.bukkit.event.world.ChunkLoadEvent::class.java)
        listener.onPlayerJoin(join)
        listener.onPlayerChangedWorld(change)
        listener.onChunkLoad(chunk)
        verifyNoInteractions(join, change, chunk)
    }

}
