package me.awabi2048.myworldmanager.api

import java.lang.reflect.Proxy
import java.util.UUID
import me.awabi2048.myworldmanager.api.extension.WorldPlayerStateDecision
import me.awabi2048.myworldmanager.api.extension.WorldPlayerStatePolicy
import me.awabi2048.myworldmanager.api.extension.WorldRuntimePolicy
import me.awabi2048.myworldmanager.model.ManagedDimension
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StandaloneEnvironmentFlightTest {
    private val world = WorldData(
        uuid = UUID.randomUUID(), dimension = ManagedDimension.OVERWORLD,
        name = "test", description = "", icon = Material.GRASS_BLOCK,
        sourceWorld = "NORMAL", expireDate = "", owner = UUID.randomUUID(),
        allowFlight = false,
    )

    private class FlightPlayer(val mode: GameMode = GameMode.SURVIVAL) {
        val uuid = UUID.randomUUID()
        var worldId = UUID.randomUUID()
        val bukkitWorld = Proxy.newProxyInstance(org.bukkit.World::class.java.classLoader, arrayOf(org.bukkit.World::class.java)) { _, method, _ ->
            when (method.name) {
                "getUID" -> worldId
                else -> error("Unexpected world access: ${method.name}")
            }
        } as org.bukkit.World
        var allowed = true
        var flying = true
        var writes = 0
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "getUniqueId" -> uuid
                "getWorld" -> bukkitWorld
                "getGameMode" -> mode
                "getAllowFlight" -> allowed
                "isFlying" -> flying
                "setAllowFlight" -> { allowed = args!![0] as Boolean; writes++; null }
                "setFlying" -> { flying = args!![0] as Boolean; writes++; null }
                else -> error("Unexpected player access: ${method.name}")
            }
        } as Player
    }

    @Test
    fun `standalone preserves externally granted flight despite saved false`() {
        val state = FlightPlayer()
        repeat(3) { MyWorldManagerApi.syncWorldPlayerState(state.player, world) }
        assertEquals(true, state.allowed)
        assertEquals(true, state.flying)
        assertEquals(0, state.writes)
    }

    @Test
    fun `standalone does not grant flight to a player without it`() {
        val state = FlightPlayer().apply { allowed = false; flying = false }
        MyWorldManagerApi.syncWorldPlayerState(state.player, world.copy(allowFlight = true))
        assertEquals(false, state.allowed)
        assertEquals(0, state.writes)
    }

    @Test
    fun `enabled environment still enforces saved flight restriction`() {
        val runtime = object : WorldRuntimePolicy {
            override fun getId() = "test.environment.enabled"
            override fun isWorldEnvironmentEnabled() = true
        }
        MyWorldManagerApi.registerWorldRuntimePolicy(runtime)
        try {
            val state = FlightPlayer()
            MyWorldManagerApi.syncWorldPlayerState(state.player, world)
            assertEquals(false, state.allowed)
            assertEquals(false, state.flying)
        } finally {
            MyWorldManagerApi.unregisterWorldRuntimePolicy(runtime)
        }
    }

    @Test
    fun `explicit extension restriction is respected without environment`() {
        val policy = object : WorldPlayerStatePolicy {
            override fun getId() = "test.flight.denied"
            override fun evaluate(player: Player, worldData: WorldData) = WorldPlayerStateDecision(flightAllowed = false)
        }
        MyWorldManagerApi.registerWorldPlayerStatePolicy(policy)
        try {
            val state = FlightPlayer()
            MyWorldManagerApi.syncWorldPlayerState(state.player, world)
            assertEquals(false, state.allowed)
            assertEquals(false, state.flying)
        } finally {
            MyWorldManagerApi.unregisterWorldPlayerStatePolicy(policy)
        }
    }

    @Test
    fun `creative flight remains allowed`() {
        val state = FlightPlayer(GameMode.CREATIVE)
        MyWorldManagerApi.syncWorldPlayerState(state.player, world)
        assertEquals(true, state.allowed)
        assertEquals(true, state.flying)
    }

    @Test
    fun `local flight request overrides enabled world restriction`() {
        val state = FlightPlayer()
        val runtime = object : WorldRuntimePolicy {
            override fun getId() = "test.local.environment"
            override fun isWorldEnvironmentEnabled() = true
        }
        MyWorldManagerApi.registerWorldRuntimePolicy(runtime)
        try {
            MyWorldManagerApi.setLocalFlightRequest(state.player, "test.local", true)
            MyWorldManagerApi.syncWorldPlayerState(state.player, world)
            assertEquals(true, state.allowed)
            assertEquals(true, MyWorldManagerApi.isFlightAllowed(state.player, world))
            MyWorldManagerApi.setLocalFlightRequest(state.player, "test.local", false)
            MyWorldManagerApi.syncWorldPlayerState(state.player, world)
            assertEquals(false, state.allowed)
        } finally {
            MyWorldManagerApi.clearLocalFlightRequest(state.uuid, "test.local")
            MyWorldManagerApi.unregisterWorldRuntimePolicy(runtime)
        }
    }

    @Test
    fun `local flight request does not apply after moving to another world`() {
        val state = FlightPlayer()
        try {
            MyWorldManagerApi.setLocalFlightRequest(state.player, "test.expired", true)
            state.worldId = UUID.randomUUID()
            state.allowed = false
            state.flying = false
            state.writes = 0
            MyWorldManagerApi.syncWorldPlayerState(state.player, world)
            assertEquals(false, state.allowed)
            assertEquals(0, state.writes)
        } finally {
            MyWorldManagerApi.clearLocalFlightRequest(state.uuid, "test.expired")
        }
    }

    @Test
    fun `explicit extension grant still enables flight without environment`() {
        val policy = object : WorldPlayerStatePolicy {
            override fun getId() = "test.flight.granted"
            override fun evaluate(player: Player, worldData: WorldData) = WorldPlayerStateDecision(flightAllowed = true)
        }
        MyWorldManagerApi.registerWorldPlayerStatePolicy(policy)
        try {
            val state = FlightPlayer().apply { allowed = false; flying = false }
            MyWorldManagerApi.syncWorldPlayerState(state.player, world)
            assertEquals(true, state.allowed)
        } finally {
            MyWorldManagerApi.unregisterWorldPlayerStatePolicy(policy)
        }
    }
}
