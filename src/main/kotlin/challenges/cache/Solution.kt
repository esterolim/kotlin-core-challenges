@file:Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package challenges.cache

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Sealed class representing the result of a cache operation.
 *
 * This design uses sealed classes to provide exhaustive when expressions,
 * ensuring all cases are handled at compile time.
 *
 * @param V the type of cached value
 *
 * @see CacheResult.Hit
 * @see CacheResult.Miss
 * @see CacheResult.Expired
 */
sealed class CacheResult<out V> {

    /**
     * Represents a successful cache hit with a valid (non-expired) value.
     *
     * @param V the type of the cached value (covariant)
     * @property value the cached value
     */
    data class Hit<V>(val value: V) : CacheResult<V>()

    /**
     * Represents a cache miss - the key does not exist in the cache.
     */
    data object Miss : CacheResult<Nothing>()

    /**
     * Represents an expired cache entry - the key existed but the TTL has passed.
     */
    data object Expired : CacheResult<Nothing>()
}

/**
 * Internal representation of a cache entry with optional expiration time.
 *
 * This is an immutable data structure that pairs a value with its expiration instant.
 * Using immutable data classes ensures thread-safe read operations without locking.
 *
 * @param V the type of the cached value
 * @property value the cached value
 * @property expiresAt optional instant when the entry expires (null = never expires)
 */
private data class CacheEntry<V>(
    val value: V,
    val expiresAt: Instant?,
) {

    /**
     * Checks if this entry has expired at the given instant.
     *
     * An entry without an expiration time (TTL = null) never expires.
     *
     * @param now the instant to check expiration against
     * @return true if the entry is expired, false otherwise
     */
    fun isExpired(now: Instant): Boolean =
        expiresAt?.let { now.isAfter(it) } ?: false
}

/**
 * A generic, thread-safe, in-memory cache with optional TTL (Time-To-Live) support.
 *
 * **Design Principles:**
 * - **Immutability**: Cache entries are immutable; updates replace entries, not modify them.
 * - **Thread Safety**: Uses [ConcurrentHashMap] for safe concurrent access without locking.
 * - **Functional Style**: Methods return sealed types ([CacheResult]) instead of throwing exceptions.
 * - **Covariance**: [CacheResult] is covariant in V, allowing safe upcasting.
 *
 * **Key Features:**
 * - Store any key-value pair with optional expiration time
 * - Query entries with exhaustive result handling ([CacheResult])
 * - Automatic lazy expiration on access
 * - Optional cleanup of expired entries
 *
 * **Example Usage:**
 * ```kotlin
 * val cache = InMemoryCache<String, String>()
 *
 * // Store with 5-minute TTL
 * cache.put("userId:123", "Alice", Duration.ofMinutes(5))
 *
 * // Safe result handling with sealed class
 * when (val result = cache.get("userId:123")) {
 *     is CacheResult.Hit -> println("User: ${result.value}")
 *     is CacheResult.Miss -> println("Key not found")
 *     is CacheResult.Expired -> println("Entry expired, need to recompute")
 * }
 *
 * // Convenience methods
 * val user = cache.getOrNull("userId:123") // null if missing or expired
 * val cached = cache.getOrElse("userId:123") { "Default User" }
 * ```
 *
 * @param K the type of cache keys (must be hashable)
 * @param V the type of cached values
 *
 * @see CacheResult
 * @see CacheEntry
 * @see getOrPut
 */
class InMemoryCache<K, V> {

    private val storage = ConcurrentHashMap<K, CacheEntry<V>>()

    /**
     * Stores a key-value pair in the cache with optional expiration.
     *
     * If the key already exists, its value is overwritten.
     *
     * @param key the cache key
     * @param value the value to store (may be null)
     * @param ttl optional time-to-live duration; null means no expiration
     */
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