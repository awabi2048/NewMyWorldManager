package me.awabi2048.myworldmanager.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class BoundedReversiblePlanRegistryTest {
    private data class Plan(val id: UUID = UUID.randomUUID(), val sequence: Int)

    @Test
    fun `unused plans remain bounded across one thousand registrations`() {
        val discarded = ConcurrentLinkedQueue<Plan>()
        val registry = registry(capacity = 64, onDiscard = discarded::add)

        repeat(1_000) { registry.register("key-$it", Plan(sequence = it)) }

        assertEquals(64, registry.size())
        assertEquals(936, discarded.size)
    }

    @Test
    fun `expired plan is purged lazily on access`() {
        val clock = MutableClock(Instant.parse("2026-08-01T00:00:00Z"))
        val discarded = mutableListOf<Plan>()
        val registry = registry(ttl = Duration.ofMinutes(6), clock = clock, onDiscard = discarded::add)
        val plan = Plan(sequence = 1)
        registry.register("player", plan)

        clock.advance(Duration.ofMinutes(6))

        assertNull(registry.get("player"))
        assertNull(registry.consume(plan.id))
        assertEquals(listOf(plan), discarded)
    }

    @Test
    fun `consume removes both indexes without discarding transferred plan`() {
        val discarded = mutableListOf<Plan>()
        val registry = registry(onDiscard = discarded::add)
        val plan = Plan(sequence = 1)
        registry.register("player", plan)

        assertSame(plan, registry.consume(plan.id))
        assertNull(registry.get("player"))
        assertEquals(0, registry.size())
        assertEquals(emptyList<Plan>(), discarded)
    }

    @Test
    fun `same key replacement discards old plan atomically`() {
        val discarded = mutableListOf<Plan>()
        val registry = registry(onDiscard = discarded::add)
        val old = Plan(sequence = 1)
        val latest = Plan(sequence = 2)

        registry.register("player", old)
        registry.register("player", latest)

        assertNull(registry.consume(old.id))
        assertSame(latest, registry.get("player"))
        assertEquals(listOf(old), discarded)
    }

    @Test
    fun `clear removes all indexes and discards retained plans`() {
        val discarded = mutableListOf<Plan>()
        val registry = registry(onDiscard = discarded::add)
        val plans = List(3) { Plan(sequence = it) }
        plans.forEachIndexed { index, plan -> registry.register("key-$index", plan) }

        registry.clear()

        assertEquals(0, registry.size())
        plans.forEach { assertNull(registry.consume(it.id)) }
        assertEquals(plans.toSet(), discarded.toSet())
    }

    @Test
    fun `parallel registration lookup and consumption keep indexes bounded`() {
        val registry = registry(capacity = 128)
        val executor = Executors.newFixedThreadPool(8)
        repeat(1_000) { sequence ->
            executor.submit {
                val key = "player-${sequence % 32}"
                val plan = Plan(sequence = sequence)
                registry.register(key, plan)
                registry.get(key)
                if (sequence % 3 == 0) registry.consume(plan.id)
            }
        }
        executor.shutdown()

        assertEquals(true, executor.awaitTermination(10, TimeUnit.SECONDS))
        assertEquals(true, registry.size() <= 128)
    }

    @Test
    fun `discard callback runs outside registry lock`() {
        val registryRef = AtomicReference<BoundedReversiblePlanRegistry<String, Plan>>()
        val executor = Executors.newSingleThreadExecutor()
        val registry = registry(capacity = 1) {
            val sizeFromAnotherThread = executor.submit<Int> { registryRef.get().size() }.get(2, TimeUnit.SECONDS)
            assertEquals(1, sizeFromAnotherThread)
        }
        registryRef.set(registry)
        registry.register("first", Plan(sequence = 1))

        registry.register("second", Plan(sequence = 2))

        executor.shutdownNow()
    }

    private fun registry(
        ttl: Duration = Duration.ofMinutes(6),
        capacity: Int = 256,
        clock: Clock = Clock.systemUTC(),
        onDiscard: (Plan) -> Unit = {},
    ) = BoundedReversiblePlanRegistry<String, Plan>(ttl, capacity, clock, Plan::id, onDiscard)

    private class MutableClock(private var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = current
        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }
}
