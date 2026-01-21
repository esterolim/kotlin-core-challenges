package challenges.cache

import challenges.cache.CacheResult.Expired
import challenges.cache.CacheResult.Hit
import challenges.cache.CacheResult.Miss
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@DisplayName("InMemoryCache")
class InMemoryCacheTest {

    private lateinit var cache: InMemoryCache<String, String>

    @BeforeEach
    fun setup() {
        cache = InMemoryCache()
    }

    @Nested
    @DisplayName("Basic Operations")
    inner class BasicOperations {

        @Test
        @DisplayName("should store and retrieve value without TTL")
        fun storeAndRetrieveWithoutTtl() {
            // Arrange
            val key = "key1"
            val value = "value1"

            // Act
            cache.put(key, value)
            val result = cache.get(key)

            // Assert
            assertTrue(result is Hit)
            assertEquals(value, (result as Hit).value)
        }

        @Test
        @DisplayName("should return Miss for non-existent key")
        fun returnMissForNonExistentKey() {
            // Act
            val result = cache.get("nonexistent")

            // Assert
            assertTrue(result is Miss)
        }

        @Test
        @DisplayName("should overwrite existing key")
        fun overwriteExistingKey() {
            // Arrange
            cache.put("key1", "value1")

            // Act
            cache.put("key1", "value2")
            val result = cache.getOrNull("key1")

            // Assert
            assertEquals("value2", result)
        }
    }

    @Nested
    @DisplayName("TTL Behavior")
    inner class TtlBehavior {

        @Test
        @DisplayName("should return Expired for expired entry")
        fun returnExpiredForExpiredEntry() {
            // Arrange
            val ttl = Duration.ofMillis(50)
            cache.put("key1", "value1", ttl)

            // Act
            Thread.sleep(100)
            val result = cache.get("key1")

            // Assert
            assertTrue(result is Expired)
        }

        @Test
        @DisplayName("should return Hit before TTL expires")
        fun returnHitBeforeTtlExpires() {
            // Arrange
            val ttl = Duration.ofSeconds(10)

            // Act
            cache.put("key1", "value1", ttl)
            val result = cache.get("key1")

            // Assert
            assertTrue(result is Hit)
            assertEquals("value1", (result as Hit).value)
        }

        @Test
        @DisplayName("getOrNull should return null for expired entries")
        fun getOrNullReturnsNullForExpiredEntries() {
            // Arrange
            cache.put("key1", "value1", Duration.ofMillis(50))

            // Act
            Thread.sleep(100)

            // Assert
            assertNull(cache.getOrNull("key1"))
        }

        @Test
        @DisplayName("should handle very short TTL")
        fun handleVeryShortTtl() {
            // Arrange
            cache.put("key1", "value1", Duration.ofMillis(1))

            // Act
            Thread.sleep(10)

            // Assert
            assertTrue(cache.get("key1") is Expired)
        }
    }

    @Nested
    @DisplayName("getOrNull / getOrElse")
    inner class GetOrNullGetOrElse {

        @Test
        @DisplayName("getOrNull should return null for missing key")
        fun getOrNullReturnsNullForMissingKey() {
            // Act
            val result = cache.getOrNull("missing")

            // Assert
            assertNull(result)
        }

        @Test
        @DisplayName("getOrElse should return default for missing key")
        fun getOrElseReturnsDefaultForMissingKey() {
            // Act
            val result = cache.getOrElse("missing") { "default" }

            // Assert
            assertEquals("default", result)
        }

        @Test
        @DisplayName("getOrElse should return cached value when present")
        fun getOrElseReturnsCachedValueWhenPresent() {
            // Arrange
            cache.put("key1", "cached")

            // Act
            val result = cache.getOrElse("key1") { "default" }

            // Assert
            assertEquals("cached", result)
        }

        @Test
        @DisplayName("getOrElse should return default for expired key")
        fun getOrElseReturnsDefaultForExpiredKey() {
            // Arrange
            cache.put("key1", "value", Duration.ofMillis(50))

            // Act
            Thread.sleep(100)
            val result = cache.getOrElse("key1") { "default" }

            // Assert
            assertEquals("default", result)
        }
    }

    @Nested
    @DisplayName("Remove Operation")
    inner class RemoveOperation {

        @Test
        @DisplayName("remove should delete key and return value")
        fun removeDeletesKeyAndReturnsValue() {
            // Arrange
            cache.put("key1", "value1")

            // Act
            val removed = cache.remove("key1")

            // Assert
            assertEquals("value1", removed)
            assertTrue(cache.get("key1") is Miss)
        }

        @Test
        @DisplayName("remove should return null for non-existent key")
        fun removeReturnsNullForNonExistentKey() {
            // Act
            val result = cache.remove("nonexistent")

            // Assert
            assertNull(result)
        }

        @Test
        @DisplayName("remove should return null for expired key")
        fun removeReturnsNullForExpiredKey() {
            // Arrange
            cache.put("key1", "value", Duration.ofMillis(50))

            // Act
            Thread.sleep(100)
            val result = cache.remove("key1")

            // Assert
            assertNull(result)
        }
    }

    @Nested
    @DisplayName("Cleanup Operation")
    inner class CleanupOperation {

        @Test
        @DisplayName("cleanup should remove expired entries")
        fun cleanupRemovesExpiredEntries() {
            // Arrange
            cache.put("key1", "value1", Duration.ofMillis(50))
            cache.put("key2", "value2") // No TTL
            cache.put("key3", "value3", Duration.ofSeconds(10))

            // Act
            Thread.sleep(100) // key1 expires
            val removedCount = cache.cleanup()

            // Assert
            assertEquals(1, removedCount)
            assertEquals(2, cache.size())
            assertTrue(cache.get("key1") is Miss)     // Removed
            assertTrue(cache.get("key2") is Hit)      // Still there
            assertTrue(cache.get("key3") is Hit)      // Still there
        }

        @Test
        @DisplayName("cleanup should return zero when no expired entries")
        fun cleanupReturnsZeroWhenNoExpiredEntries() {
            // Arrange
            cache.put("key1", "value1")

            // Act
            val removedCount = cache.cleanup()

            // Assert
            assertEquals(0, removedCount)
        }
    }

    @Nested
    @DisplayName("Clear Operation")
    inner class ClearOperation {

        @Test
        @DisplayName("clear should remove all entries")
        fun clearRemovesAllEntries() {
            // Arrange
            cache.put("key1", "value1")
            cache.put("key2", "value2")

            // Act
            cache.clear()

            // Assert
            assertEquals(0, cache.size())
            assertTrue(cache.get("key1") is Miss)
        }
    }

    @Nested
    @DisplayName("Size Operation")
    inner class SizeOperation {

        @Test
        @DisplayName("size should include expired entries until cleanup")
        fun sizeIncludesExpiredEntriesUntilCleanup() {
            // Arrange
            cache.put("key1", "value1", Duration.ofMillis(50))
            cache.put("key2", "value2")

            // Act
            Thread.sleep(100)
            val sizeBeforeCleanup = cache.size()
            cache.cleanup()
            val sizeAfterCleanup = cache.size()

            // Assert
            assertEquals(2, sizeBeforeCleanup)
            assertEquals(1, sizeAfterCleanup)
        }
    }

    @Nested
    @DisplayName("getOrPut Extension Function")
    inner class GetOrPutExtension {

        @Test
        @DisplayName("getOrPut should return existing value without computing")
        fun getOrPutReturnsExistingValueWithoutComputing() {
            // Arrange
            cache.put("key1", "existing")
            var computed = false

            // Act
            val result = cache.getOrPut("key1") {
                computed = true
                "new"
            }

            // Assert
            assertEquals("existing", result)
            assertFalse(computed, "Should not compute when value exists")
        }

        @Test
        @DisplayName("getOrPut should compute and cache on miss")
        fun getOrPutComputesAndCachesOnMiss() {
            // Act
            val result = cache.getOrPut("key1") { "computed" }

            // Assert
            assertEquals("computed", result)
            assertEquals("computed", cache.getOrNull("key1"))
        }

        @Test
        @DisplayName("getOrPut should recompute for expired entries")
        fun getOrPutRecomputesForExpiredEntries() {
            // Arrange
            cache.put("key1", "old", Duration.ofMillis(50))

            // Act
            Thread.sleep(100)
            val result = cache.getOrPut("key1") { "new" }

            // Assert
            assertEquals("new", result)
            assertEquals("new", cache.getOrNull("key1"))
        }

        @Test
        @DisplayName("getOrPut should respect TTL when computing")
        fun getOrPutRespectsTtlWhenComputing() {
            // Act
            cache.getOrPut("key1", Duration.ofMillis(50)) { "computed" }

            // Assert
            assertTrue(cache.get("key1") is Hit)

            // Act
            Thread.sleep(100)

            // Assert
            assertTrue(cache.get("key1") is Expired)
        }
    }

    @Nested
    @DisplayName("Thread Safety")
    inner class ThreadSafety {

        @Test
        @DisplayName("should handle concurrent reads and writes")
        fun handleConcurrentReadsAndWrites() {
            // Arrange
            val threadCount = 100
            val latch = CountDownLatch(threadCount)

            // Act
            repeat(threadCount) { i ->
                thread {
                    cache.put("key$i", "value$i")
                    cache.get("key$i")
                    latch.countDown()
                }
            }

            // Assert
            assertTrue(latch.await(5, TimeUnit.SECONDS))
            assertEquals(threadCount, cache.size())
        }

        @Test
        @DisplayName("should handle concurrent updates to same key")
        fun handleConcurrentUpdatesToSameKey() {
            // Arrange
            val threadCount = 100
            val executor = Executors.newFixedThreadPool(10)
            val latch = CountDownLatch(threadCount)

            // Act
            repeat(threadCount) { i ->
                executor.submit {
                    cache.put("shared", "value$i")
                    latch.countDown()
                }
            }

            // Assert
            assertTrue(latch.await(5, TimeUnit.SECONDS))
            assertNotNull(cache.getOrNull("shared"))

            executor.shutdown()
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCases {

        @Test
        @DisplayName("should handle null values")
        fun handleNullValues() {
            // Arrange
            val nullableCache = InMemoryCache<String, String?>()

            // Act
            nullableCache.put("key1", null)
            val result = nullableCache.getOrNull("key1")

            // Assert
            assertNull(result)
            assertTrue(nullableCache.get("key1") is Hit)
        }

        @Test
        @DisplayName("should handle complex value types")
        fun handleComplexValueTypes() {
            // Arrange
            data class User(val id: Int, val name: String)

            val userCache = InMemoryCache<Int, User>()
            val user = User(1, "Alice")

            // Act
            userCache.put(1, user)

            // Assert
            assertEquals(user, userCache.getOrNull(1))
        }

        @Test
        @DisplayName("should distinguish between null value and miss")
        fun distinguishBetweenNullValueAndMiss() {
            // Arrange
            val nullableCache = InMemoryCache<String, String?>()

            // Act
            nullableCache.put("existsWithNull", null)

            // Assert
            val existsResult = nullableCache.get("existsWithNull")
            val missingResult = nullableCache.get("nonexistent")

            assertTrue(existsResult is Hit)
            assertTrue(missingResult is Miss)
        }
    }
}
