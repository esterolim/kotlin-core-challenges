@file:Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package challenges.cache

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

sealed class CacheResult<out V> {

    data class Hit<V>(val value: V) : CacheResult<V>()
    data object Miss : CacheResult<Nothing>()
    data object Expired : CacheResult<Nothing>()
}

private data class CacheEntry<V>(
    val value: V,
    val expiresAt: Instant?,
) {

    fun isExpired(now: Instant): Boolean =
        expiresAt?.let { now.isAfter(it) } ?: false
}

class InMemoryCache<K, V> {

    private val storage = ConcurrentHashMap<K, CacheEntry<V>>()

    fun put(key: K, value: V, ttl: Duration? = null) {
        val expiresAt = ttl?.let { Instant.now().plus(it) }
        storage[key] = CacheEntry(value, expiresAt)
    }

    fun get(key: K): CacheResult<V> {
        val entry = storage[key] ?: return CacheResult.Miss

        return if (entry.isExpired(Instant.now())) {
            CacheResult.Expired
        } else {
            CacheResult.Hit(entry.value)
        }
    }

    fun getOrNull(key: K): V? = when (val result = get(key)) {
        is CacheResult.Hit -> result.value
        is CacheResult.Miss, is CacheResult.Expired -> null
    }

    fun getOrElse(key: K, default: () -> V): V = when (val result = get(key)) {
        is CacheResult.Hit -> result.value
        is CacheResult.Miss, is CacheResult.Expired -> default()
    }

    fun remove(key: K): V? {
        val entry = storage.remove(key) ?: return null
        return if (entry.isExpired(Instant.now())) null else entry.value
    }

    fun cleanup(): Int {
        val now = Instant.now()
        val expiredKeys = storage.entries
            .filter { it.value.isExpired(now) }
            .map { it.key }

        expiredKeys.forEach { storage.remove(it) }
        return expiredKeys.size
    }

    fun clear() {
        storage.clear()
    }

    fun size(): Int = storage.size
}

fun <K, V> InMemoryCache<K, V>.getOrPut(
    key: K,
    ttl: Duration? = null,
    compute: () -> V,
): V {
    // Fast path: try to get existing value first
    getOrNull(key)?.let { return it }

    // Slow path: compute and store
    val value = compute()
    put(key, value, ttl)
    return value
}