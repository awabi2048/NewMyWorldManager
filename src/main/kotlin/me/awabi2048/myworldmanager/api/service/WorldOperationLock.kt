package me.awabi2048.myworldmanager.api.service

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

enum class WorldOperation {
    LOAD,
    UNLOAD,
    CREATE,
    DELETE,
    BACKUP,
    RESTORE,
    ARCHIVE,
    MIGRATE,
    EXPAND
}

class WorldOperationLease internal constructor(
    val worldUuid: UUID,
    val operation: WorldOperation,
    internal val leaseId: UUID,
    private val releaseAction: (WorldOperationLease) -> Unit
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) releaseAction(this)
    }
}

internal object WorldOperationLocks {
    private val active = ConcurrentHashMap<UUID, WorldOperationLease>()

    fun tryAcquire(worldUuid: UUID, operation: WorldOperation): WorldOperationLease? {
        val lease = WorldOperationLease(worldUuid, operation, UUID.randomUUID(), ::release)
        return if (active.putIfAbsent(worldUuid, lease) == null) lease else null
    }

    fun isActive(lease: WorldOperationLease): Boolean =
        active[lease.worldUuid]?.leaseId == lease.leaseId

    fun current(worldUuid: UUID): WorldOperation? = active[worldUuid]?.operation

    fun clear() = active.clear()

    private fun release(lease: WorldOperationLease) {
        active.computeIfPresent(lease.worldUuid) { _, current ->
            current.takeUnless { it.leaseId == lease.leaseId }
        }
    }
}
