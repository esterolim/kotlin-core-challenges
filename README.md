# 🚀 Kotlin Core Challenges

> A **production-ready** demonstration of mid to senior-level Kotlin expertise, following Google
> Developers best practices, Clean Code principles, and SOLID design patterns.

---

## 📋 Overview

This repository showcases **idiomatic Kotlin solutions** to real-world challenges. Each solution
emphasizes:

✨ **Immutability** — Preferring `val` over `var`  
🔒 **Thread Safety** — Concurrent-safe data structures without unnecessary locks  
🎯 **Type Safety** — Sealed classes, exhaustive `when` expressions  
📝 **Clean Code** — Self-documenting, minimal, and focused functions  
🧪 **Testability** — Comprehensive tests with edge cases and concurrency scenarios

---

## 🎯 Repository Goals

This is **NOT** another Android UI tutorial. This is about:

- ✅ **Kotlin Language Mastery** — Sealed classes, data classes, inline functions, extension
  functions
- ✅ **Functional Programming** — Immutability, pure functions, lazy evaluation
- ✅ **Concurrent Systems** — Thread-safe collections, avoiding locks when possible
- ✅ **Clean Architecture** — SOLID principles, separation of concerns
- ✅ **Enterprise Patterns** — Design patterns used in production systems

---

## 📁 Project Structure

```
kotlin-core-challenges/
├── src/main/kotlin/challenges/
│   └── cache/
│       ├── Solution.kt           # Main implementation
│       └── tests/
│           └── InMemoryCacheTest.kt
├── build.gradle.kts              # Gradle configuration
└── README            # Problem & approach
```

Each challenge lives in its own package with:

- 📄 **README.md** — Problem description, design decisions, trade-offs
- 📝 **Solution.kt** — Clean implementation with KDoc
- 🧪 **Test.kt** — Comprehensive unit tests (JUnit 5)

---

## 🏆 Challenge 1: InMemoryCache

### Problem Statement

Implement a **generic, thread-safe, in-memory cache** with TTL (Time-To-Live) support.

**Requirements:**

- ✅ Generic cache: `Cache<K, V>`
- ✅ Optional TTL support per entry
- ✅ Expired values should not be returned
- ✅ Thread-safe concurrent access
- ✅ No global mutable state
- ✅ Pure Kotlin (no Android, no Coroutines)

### Why This Challenge?

This challenge tests:

1. **Generic type system** — Understanding covariance/contravariance
2. **Concurrent programming** — ConcurrentHashMap vs synchronized
3. **Functional design** — Sealed classes, when expressions
4. **Time handling** — Java Time API
5. **Testing** — Concurrency tests, edge cases, TTL behavior

---

## 🏗️ Design Decisions & Trade-offs

### 1️⃣ Sealed Class for Results (NOT null, NOT exceptions)

```kotlin
sealed class CacheResult<out V> {
    data class Hit<V>(val value: V) : CacheResult<V>()
    data object Miss : CacheResult<Nothing>()
    data object Expired : CacheResult<Nothing>()
}
```

**Why?**

- ✅ **Type-safe**: Compiler enforces exhaustive checking with `when`
- ✅ **Distinguishes cases**: Differentiates between missing vs expired
- ✅ **Covariant**: `CacheResult<Dog>` safely becomes `CacheResult<Animal>`
- ✅ **No surprises**: No exceptions on normal control flow

**Alternatives considered:**
| Approach | Pros | Cons |
|----------|------|------|
| Sealed class ✅ | Type-safe, exhaustive | Slightly more verbose |
| `Result<V>` | Functional pattern | Loses Miss vs Expired distinction |
| `V?` (nullable) | Simple | Ambiguous semantics |
| Exceptions | Familiar | Anti-pattern in Kotlin |

### 2️⃣ Immutable Entries

```kotlin
data class CacheEntry<V>(
    val value: V,
    val expiresAt: Instant?
)
```

**Why?**

- ✅ **Lock-free reads**: Immutable objects are inherently thread-safe
- ✅ **GC-friendly**: Immutable objects can be optimized
- ✅ **Predictable**: No surprises from concurrent mutations

### 3️⃣ ConcurrentHashMap (NOT HashMap + synchronized)

```kotlin
private val storage = ConcurrentHashMap<K, CacheEntry<V>>()
```

**Why?**

- ✅ **Lock striping**: Each bucket has its own lock, not the entire map
- ✅ **Read-heavy**: Multiple threads can read simultaneously
- ✅ **Better performance**: Fine-grained concurrency vs global locks

**Benchmark impact:**

- ❌ `synchronized(map) { get(key) }` — Blocks all operations while locked
- ✅ `ConcurrentHashMap.get(key)` — Only blocks the specific bucket

### 4️⃣ Lazy Expiration with Explicit Cleanup

```kotlin
// On read: returns Expired without removing
fun get(key: K): CacheResult<V> {
    val entry = storage[key] ?: return CacheResult.Miss
    return if (entry.isExpired(Instant.now())) {
        CacheResult.Expired  // ← Doesn't modify cache
    } else {
        CacheResult.Hit(entry.value)
    }
}

// Explicit cleanup: removes expired entries
fun cleanup(): Int {
    val expiredKeys = storage.entries
        .filter { it.value.isExpired(Instant.now()) }
        .map { it.key }
    expiredKeys.forEach { storage.remove(it) }
    return expiredKeys.size
}
```

**Why?**

- ✅ **Fast reads**: O(1) without additional I/O
- ✅ **Asymmetric operations**: `get()` observes state, doesn't modify it
- ✅ **Explicit control**: Application decides when to clean up

**Alternative (Auto-remove):**

```kotlin
fun get(key: K): V? {
    val entry = storage[key] ?: return null
    if (entry.isExpired()) {
        storage.remove(key)  // ← Every read might trigger removal!
    }
    return entry.value
}
```

### 5️⃣ Extension Function for getOrPut

```kotlin
fun <K, V> InMemoryCache<K, V>.getOrPut(
    key: K,
    ttl: Duration? = null,
    compute: () -> V,
): V {
    getOrNull(key)?.let { return it }  // Fast path
    val value = compute()               // Slow path
    put(key, value, ttl)
    return value
}
```

**Why?**

- ✅ **Separation of concerns**: Core cache stays focused
- ✅ **Kotlin idiom**: Extension functions extend without modifying
- ✅ **Lazy computation**: Only computes on cache miss
- ✅ **Optional**: Apps that don't need it pay no cost

---

## 🚀 API Reference

| Method                         | Returns          | Purpose                                      |
|--------------------------------|------------------|----------------------------------------------|
| `put(key, value, ttl?)`        | `Unit`           | Store value with optional TTL                |
| `get(key)`                     | `CacheResult<V>` | Retrieve with exhaustive result              |
| `getOrNull(key)`               | `V?`             | Retrieve or null (ignores Miss vs Expired)   |
| `getOrElse(key, default)`      | `V`              | Retrieve or compute default                  |
| `remove(key)`                  | `V?`             | Remove and return value                      |
| `cleanup()`                    | `Int`            | Remove expired entries, return count         |
| `clear()`                      | `Unit`           | Remove all entries                           |
| `size()`                       | `Int`            | Entry count (includes expired until cleanup) |
| `getOrPut(key, ttl?, compute)` | `V`              | Get or compute and cache                     |

### Usage Example

```kotlin
// Create cache
val cache = InMemoryCache<String, String>()

// Store with 5-minute TTL
cache.put("user:123", "Alice", Duration.ofMinutes(5))

// Safe, exhaustive result handling
when (val result = cache.get("user:123")) {
    is CacheResult.Hit -> println("User: ${result.value}")
    is CacheResult.Miss -> println("Not found")
    is CacheResult.Expired -> println("Expired, recompute")
}

// Convenience methods
val user = cache.getOrNull("user:123")           // String? or null
val cached = cache.getOrElse("user:123") { "" }  // Always String

// Compute and cache pattern (very common)
val user = cache.getOrPut("user:123", Duration.ofMinutes(5)) {
    fetchUserFromDatabase(123)  // Only called on miss
}

// Periodic cleanup
val removed = cache.cleanup()
logger.info("Removed $removed expired entries")
```

---

## 📊 Performance Analysis

| Operation | Time     | Space | Notes                              |
|-----------|----------|-------|------------------------------------|
| `put`     | **O(1)** | O(1)  | HashMap insertion                  |
| `get`     | **O(1)** | O(1)  | HashMap lookup, no removal         |
| `remove`  | **O(1)** | O(-1) | Deletion                           |
| `cleanup` | **O(n)** | O(1)  | n = all entries, called explicitly |
| `size`    | **O(1)** | O(1)  | ConcurrentHashMap delegation       |

**Note**: O(1) is *average case*. Worst case is O(n) with hash collisions, but extremely rare with
modern hash functions.

---

## 🧵 Thread Safety Guarantees

✅ **Concurrent reads** — Multiple threads read simultaneously without locks  
✅ **Concurrent writes to different keys** — Each bucket has independent lock  
✅ **Atomic operations** — `put`, `get`, `remove` are indivisible  
✅ **Visibility** — Changes are immediately visible to other threads

**Implementation:**

```kotlin
// ConcurrentHashMap uses:
// 1. Lock striping: Only one bucket locked at a time
// 2. Volatile fields: Ensures cross-thread visibility
// 3. Atomic operations: get-then-act patterns are safe
```

**Concurrency test:**

```kotlin
@Test
fun handleConcurrentReadsAndWrites() {
    val threadCount = 100
    val latch = CountDownLatch(threadCount)

    repeat(threadCount) { i ->
        thread {
            cache.put("key$i", "value$i")
            cache.get("key$i")
            latch.countDown()
        }
    }

    assertTrue(latch.await(5, TimeUnit.SECONDS))
    assertEquals(threadCount, cache.size())
}
```

---

## 🧪 Testing Strategy

Tests are organized with **JUnit 5** `@Nested` classes for clarity:

```
InMemoryCacheTest
├── BasicOperations
│   ├── should store and retrieve value without TTL
│   ├── should return Miss for non-existent key
│   └── should overwrite existing key
├── TtlBehavior
│   ├── should return Expired for expired entry
│   ├── should return Hit before TTL expires
│   └── should handle very short TTL
├── ThreadSafety
│   ├── should handle concurrent reads and writes
│   └── should handle concurrent updates to same key
└── EdgeCases
    ├── should handle null values
    ├── should handle complex value types
    └── should distinguish null value from miss
```

**Test patterns:**

- 🟢 **Happy paths** — Basic functionality
- 🟡 **Edge cases** — Null values, complex types, very short TTLs
- 🔴 **Failure scenarios** — Expired entries, missing keys
- 🔵 **Concurrency** — Race conditions, thread safety
- ⏱️ **Timing** — TTL accuracy, expiration behavior

---

## 📚 What's Appreciated in This Solution

### ✅ For Nubank / Senior Interviews

1. **Sealed classes for domain modeling**
    - Type-safe, exhaustive checking
    - Better than exceptions for control flow

2. **Immutability + functional style**
    - Reduces bugs
    - Enables fearless concurrency
    - Better for testing

3. **Proper abstractions**
    - `CacheEntry` hides expiration logic
    - `CacheResult` makes states explicit

4. **Extension functions**
    - `getOrPut` separates concerns
    - Idiomatically Kotlin
    - Composable

5. **Comprehensive documentation**
    - KDoc explains "why", not just "what"
    - Includes trade-offs and alternatives
    - Shows architectural thinking

6. **Production mindset**
    - ConcurrentHashMap over synchronized
    - Lazy cleanup, not eager
    - Thread safety without raw locks

7. **Testing discipline**
    - Concurrency tests
    - Edge cases
    - Clear test structure

### ❌ What's Avoided

❌ Using `null` for miss/expired  
❌ Throwing exceptions in normal flow  
❌ `synchronized` blocks on entire map  
❌ Mutable cache entries  
❌ Auto-removal on access  
❌ Global mutable state

---

## 🎯 Interview Talking Points

### "Why sealed class instead of enum?"

```kotlin
// Enum can't attach different data per case
enum class CacheResult { HIT, MISS, EXPIRED }  // How to store value in HIT?

// Sealed class can
sealed class CacheResult<out V> {
    data class Hit<V>(val value: V) : CacheResult<V>()
    data object Miss : CacheResult<Nothing>()
    data object Expired : CacheResult<Nothing>()
}
```

### "Why ConcurrentHashMap not HashMap + synchronized?"

Because `ConcurrentHashMap` uses **lock striping** — only the affected bucket is locked, allowing
multiple threads to write to different buckets simultaneously.

```kotlin
// ❌ Blocks entire map
synchronized(map) { map.get(key) }

// ✅ Only blocks one bucket
concurrentMap.get(key)
```

### "Why lazy expiration not auto-remove?"

Because reading shouldn't have side effects (remove I/O). Explicit cleanup allows the application to
decide *when* to run this O(n) operation.

---

## 🔄 Potential Extensions

Ideas to expand this challenge:

- 🔄 **LRU eviction** — LinkedHashMap-based capacity limits
- 📊 **Metrics** — Hit rate, eviction count, avg age
- 📡 **Remote invalidation** — Listeners for cache changes
- 🔐 **Value types** — Inline classes for UserId, CacheKey
- 🚀 **Multi-level** — L1 (in-memory) + L2 (persistent)

---

## 🛠️ Tech Stack

- **Language**: Kotlin 2.2.21
- **JVM**: Java 17 LTS
- **Build**: Gradle 8.14 (Kotlin DSL)
- **Testing**: JUnit 5.14.0
- **Style**: Google Kotlin Guide + Clean Code + SOLID

---

## 📖 Further Reading

- 📘 [Effective Kotlin](https://kotlinlang.org/docs/idioms.html) — Language best practices
- 📘 [Google Kotlin Style Guide](https://android.github.io/kotlin-guidelines/) — Naming, formatting
- 📘 [Clean Code](https://www.oreilly.com/library/view/clean-code-a/9780136083238/) — Principles
  applied
- 📘 [Java Concurrency in Practice](https://jcip.net/) — Concurrent collections
-
🎥 [Kotlin Best Practices - Google Developers](https://www.youtube.com/playlist?list=PLQkwcJG4YTCSYJ13G82serFxWDnoPbn7p)

---

## 📖 Documentation Files

- **[src/main/kotlin/challenges/cache/README.md](./src/main/kotlin/challenges/cache/README.md)** —
  Challenge-specific deep dive

---

## ✅ Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests InMemoryCacheTest

# Run with detailed output
./gradlew test --info
```

All tests pass ✅

---

## 🎓 For Mid-Level Engineers Aiming for Senior Roles

This repository demonstrates:

1. **Deep language knowledge** — Not just "Kotlin basics"
2. **Systems thinking** — Trade-offs, performance, concurrency
3. **Production mindset** — Thread safety, monitoring, extensibility
4. **Communication** — Clear docs, design decisions explained
5. **Testing discipline** — Not just happy paths

Use this as:

- 📝 **Interview talking point** — "I built a production-ready cache..."
- 🎯 **Learning reference** — Study the decisions
- 🔍 **Code review template** — How to write clean Kotlin
- 💼 **Portfolio project** — Show what you're capable of

---

## 📝 License

This project is educational and open source.

---

**Ready for your next role? This repository shows you understand** ✨

- Functional programming in Kotlin
- Concurrent systems
- Clean code principles
- Enterprise design patterns
- Production-ready thinking

**Let's ship great code! 🚀**
