package me.awabi2048.myworldmanager.api.service

import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WorldOperationLocksTest {
    @AfterEach
    fun clearLocks() = WorldOperationLocks.clear()

    @Test
    fun serializesOperationsPerWorldAndReleasesIdempotently() {
        val world = UUID.randomUUID()
        val first = WorldOperationLocks.tryAcquire(world, WorldOperation.LOAD)
        assertNotNull(first)
        assertEquals(WorldOperation.LOAD, WorldOperationLocks.current(world))
        assertNull(WorldOperationLocks.tryAcquire(world, WorldOperation.BACKUP))

        first!!.close()
        first.close()

        assertFalse(WorldOperationLocks.isActive(first))
        val second = WorldOperationLocks.tryAcquire(world, WorldOperation.BACKUP)
        assertNotNull(second)
        assertEquals(WorldOperation.BACKUP, WorldOperationLocks.current(world))
    }

    @Test
    fun allowsIndependentWorldsConcurrently() {
        val first = WorldOperationLocks.tryAcquire(UUID.randomUUID(), WorldOperation.MIGRATE)
        val second = WorldOperationLocks.tryAcquire(UUID.randomUUID(), WorldOperation.RESTORE)
        assertNotNull(first)
        assertNotNull(second)
    }
}
