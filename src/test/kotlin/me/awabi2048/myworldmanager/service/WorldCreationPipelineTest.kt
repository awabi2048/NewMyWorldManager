package me.awabi2048.myworldmanager.service

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Bukkit に触れない直列化中核の検証。スレッド切替えフックをテスト用に差し替える。
 */
class WorldCreationPipelineTest {
    private fun testPipeline(maxQueueSize: Int = 10): WorldCreationPipeline =
        WorldCreationPipeline(
            isMainThread = { true },
            canDispatch = { true },
            dispatchOnMain = { runnable -> runnable.run() },
            maxQueueSize = maxQueueSize,
        )

    @Test
    fun `空きがあれば即時実行し解放で非占有に戻る`() {
        val pipeline = testPipeline()
        var ran = false
        val result = pipeline.submit(UUID.randomUUID(), { _ -> error("queued") }, { _ -> ran = true }, {})
        assertEquals(WorldCreationPipeline.SubmitResult.STARTED, result)
        assertTrue(ran)
    }

    @Test
    fun `使用中は待ち行列に入り解放後にFIFOで実行される`() {
        val pipeline = testPipeline()
        val order = mutableListOf<String>()
        val first = pipeline.tryAcquire() ?: error("acquire")
        val positions = mutableListOf<Int>()
        repeat(3) { index ->
            val result = pipeline.submit(
                UUID.randomUUID(),
                { position -> positions.add(position) },
                { permit ->
                    order.add("task$index")
                    permit.release()
                },
                { error("cancelled") },
            )
            assertEquals(WorldCreationPipeline.SubmitResult.QUEUED, result)
        }
        assertEquals(listOf(1, 2, 3), positions)
        first.release()
        assertEquals(listOf("task0", "task1", "task2"), order)
    }

    @Test
    fun `許可の二重解放は次の実行を重複させない`() {
        val pipeline = testPipeline()
        var runs = 0
        val first = pipeline.tryAcquire() ?: error("acquire")
        pipeline.submit(UUID.randomUUID(), {}, { permit ->
            runs++
            permit.release()
        }, {})
        first.release()
        first.release()
        assertEquals(1, runs)
    }

    @Test
    fun `複数スレッドからの同時提出でも実行は1件ずつになる`() {
        val pipeline = testPipeline()
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val threads = 8
        val started = CountDownLatch(1)
        val done = CountDownLatch(threads)
        repeat(threads) {
            Thread {
                started.await(5, TimeUnit.SECONDS)
                pipeline.submit(
                    UUID.randomUUID(),
                    {},
                    { permit ->
                        val current = active.incrementAndGet()
                        maxActive.updateAndGet { max -> max.coerceAtLeast(current) }
                        Thread.sleep(5)
                        active.decrementAndGet()
                        permit.release()
                        done.countDown()
                    },
                    { done.countDown() },
                )
            }.also { it.isDaemon = true; it.start() }
        }
        started.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS))
        assertEquals(1, maxActive.get())
    }

    @Test
    fun `待ち上限を超えた提出は拒否される`() {
        val pipeline = testPipeline(maxQueueSize = 1)
        val first = pipeline.tryAcquire() ?: error("acquire")
        val queued = pipeline.submit(UUID.randomUUID(), {}, { permit -> permit.release() }, {})
        assertEquals(WorldCreationPipeline.SubmitResult.QUEUED, queued)
        val full = pipeline.submit(UUID.randomUUID(), { _ -> error("queued") }, { _ -> error("ran") }, {})
        assertEquals(WorldCreationPipeline.SubmitResult.QUEUE_FULL, full)
        first.release()
    }

    @Test
    fun `シャットダウンは待機中を破棄し新規を拒否する`() {
        val pipeline = testPipeline()
        val first = pipeline.tryAcquire() ?: error("acquire")
        var cancelled = 0
        pipeline.submit(UUID.randomUUID(), {}, { permit -> permit.release() }, { cancelled++ })
        pipeline.shutdown()
        assertEquals(1, cancelled)
        val rejected = pipeline.submit(UUID.randomUUID(), { _ -> error("queued") }, { _ -> error("ran") }, {})
        assertEquals(WorldCreationPipeline.SubmitResult.REJECTED_SHUTDOWN, rejected)
        assertNull(pipeline.tryAcquire())
        first.release()
    }
}
