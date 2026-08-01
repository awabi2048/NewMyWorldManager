package me.awabi2048.myworldmanager.service

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * 復元tokenに紐づくplanを、期限と容量を限定して保持します。
 *
 * key/id索引の更新はlock内で一括して行い、破棄callbackはplugin処理とのlock反転を避けるため
 * 必ずlock外で呼び出します。
 */
internal class BoundedReversiblePlanRegistry<K : Any, V : Any>(
    private val ttl: Duration = Duration.ofMinutes(6),
    private val capacity: Int = 256,
    private val clock: Clock = Clock.systemUTC(),
    private val idOf: (V) -> UUID,
    private val onDiscard: (V) -> Unit = {},
) {
    private data class Entry<K, V>(val key: K, val value: V, val expiresAt: Instant)

    private val lock = Any()
    private val byId = linkedMapOf<UUID, Entry<K, V>>()
    private val idByKey = hashMapOf<K, UUID>()

    init {
        require(!ttl.isNegative && !ttl.isZero) { "reversible plan ttl must be positive" }
        require(capacity > 0) { "reversible plan capacity must be positive" }
    }

    fun register(key: K, value: V) {
        val discarded = synchronized(lock) {
            val removed = purgeExpiredLocked(clock.instant())
            val id = idOf(value)
            removeByIdLocked(id)?.let(removed::add)
            idByKey[key]?.let(::removeByIdLocked)?.let(removed::add)
            byId[id] = Entry(key, value, clock.instant().plus(ttl))
            idByKey[key] = id
            while (byId.size > capacity) {
                removeByIdLocked(byId.entries.first().key)?.let(removed::add)
            }
            removed
        }
        discardAll(discarded)
    }

    fun get(key: K): V? {
        val result = synchronized(lock) {
            val discarded = purgeExpiredLocked(clock.instant())
            val value = idByKey[key]?.let(byId::get)?.value
            value to discarded
        }
        discardAll(result.second)
        return result.first
    }

    /** key索引だけを外し、tokenからのconsumeに備えてid索引は保持します。 */
    fun detachKey(key: K): V? {
        val result = synchronized(lock) {
            val discarded = purgeExpiredLocked(clock.instant())
            val id = idByKey.remove(key)
            id?.let(byId::get)?.value to discarded
        }
        discardAll(result.second)
        return result.first
    }

    fun removeKey(key: K): V? {
        val result = synchronized(lock) {
            val discarded = purgeExpiredLocked(clock.instant())
            val value = idByKey[key]?.let(::removeByIdLocked)
            value?.let(discarded::add)
            value to discarded
        }
        discardAll(result.second)
        return result.first
    }

    fun removeById(id: UUID): V? {
        val result = synchronized(lock) {
            val discarded = purgeExpiredLocked(clock.instant())
            val value = removeByIdLocked(id)
            value?.let(discarded::add)
            value to discarded
        }
        discardAll(result.second)
        return result.first
    }

    /** 所有権を呼び出し元へ移すため、破棄callbackは実行しません。 */
    fun consume(id: UUID): V? {
        val result = synchronized(lock) {
            val discarded = purgeExpiredLocked(clock.instant())
            removeByIdLocked(id) to discarded
        }
        discardAll(result.second)
        return result.first
    }

    fun purgeExpired(now: Instant = clock.instant()): Int {
        val discarded = synchronized(lock) { purgeExpiredLocked(now) }
        discardAll(discarded)
        return discarded.size
    }

    fun removeWhere(predicate: (V) -> Boolean): Int {
        val snapshot = synchronized(lock) {
            val expired = purgeExpiredLocked(clock.instant())
            byId.map { (id, entry) -> id to entry.value } to expired
        }
        discardAll(snapshot.second)
        val matching = snapshot.first.filter { (_, value) -> predicate(value) }
        val removed = synchronized(lock) {
            val discarded = purgeExpiredLocked(clock.instant())
            matching.forEach { (id, expected) ->
                if (byId[id]?.value === expected) removeByIdLocked(id)?.let(discarded::add)
            }
            discarded
        }
        discardAll(removed)
        return snapshot.second.size + removed.size
    }

    fun clear() {
        val discarded = synchronized(lock) {
            val values = byId.values.map { it.value }
            byId.clear()
            idByKey.clear()
            values
        }
        discardAll(discarded)
    }

    internal fun size(): Int {
        val result = synchronized(lock) {
            val discarded = purgeExpiredLocked(clock.instant())
            byId.size to discarded
        }
        discardAll(result.second)
        return result.first
    }

    private fun purgeExpiredLocked(now: Instant): MutableList<V> {
        val discarded = mutableListOf<V>()
        val expiredIds = byId.entries.asSequence()
            .filter { it.value.expiresAt <= now }
            .map { it.key }
            .toList()
        expiredIds.forEach { id -> removeByIdLocked(id)?.let(discarded::add) }
        return discarded
    }

    private fun removeByIdLocked(id: UUID): V? {
        val entry = byId.remove(id) ?: return null
        idByKey.remove(entry.key, id)
        return entry.value
    }

    private fun discardAll(values: Collection<V>) {
        values.forEach(onDiscard)
    }
}
