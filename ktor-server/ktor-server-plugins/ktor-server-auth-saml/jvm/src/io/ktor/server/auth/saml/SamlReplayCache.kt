/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Interface for SAML assertion replay protection.
 *
 * Implementations of this interface track processed SAML assertion IDs to prevent
 * replay attacks where an attacker intercepts a valid assertion and reuses it.
 *
 * ## Security Consideration
 *
 * According to OWASP SAML Security Cheat Sheet, Service Providers MUST track
 * assertion IDs for at least the validity period of the assertion to prevent replay attacks.
 *
 * ## Implementation Requirements
 *
 * **Cache TTL**: The cache must retain assertion IDs for at least as long as the assertion's
 * validity period (typically defined by the `NotOnOrAfter` condition). If entries are evicted
 * too early, replay attacks become possible within the remaining validity window.
 *
 * @see <a href="https://cheatsheetseries.owasp.org/cheatsheets/SAML_Security_Cheat_Sheet.html">OWASP SAML Security</a>
 * @see InMemorySamlReplayCache
 */
@OptIn(ExperimentalTime::class)
public interface SamlReplayCache : AutoCloseable {
    /**
     * Checks if an assertion ID has already been processed.
     *
     * @param assertionId The unique assertion ID to check
     * @return true if this is a replay (ID already seen), false if this is the first time
     */
    public suspend fun isReplayed(assertionId: String): Boolean

    /**
     * Records an assertion ID as processed.
     *
     * @param assertionId The unique assertion ID to record
     * @param expirationTime The expiration time of the assertion (used for cache eviction)
     */
    public suspend fun recordAssertion(assertionId: String, expirationTime: Instant)

    /**
     * Atomically checks if an assertion ID has been seen and records it if not.
     * This method combines [isReplayed] and [recordAssertion] into a single atomic operation.
     *
     * `@param` assertionId The unique assertion ID to check and record
     * `@param` expirationTime The expiration time of the assertion (used for cache eviction)
     * `@return` true if the assertion was not seen before and was recorded; false if replay detected
     */
    public suspend fun tryRecordAssertion(assertionId: String, expirationTime: Instant): Boolean
}

/**
 * In-memory implementation of SamlReplayCache using coroutines.
 *
 * This implementation uses a Mutex-protected map to store assertion IDs in memory.
 *
 * ## Important Limitations
 *
 * **Single-Instance Only**: This cache stores data in-process memory and is NOT shared
 * across multiple application instances. In clustered or load-balanced deployments,
 * each instance maintains its own independent cache, which means:
 * - An assertion processed by Instance A can be replayed to Instance B
 * - Replay protection is effectively disabled in multi-instance environments
 *
 * For production deployments with multiple instances, implement a distributed cache
 * using Redis, Memcached, a database, or another shared storage mechanism.
 * See [SamlReplayCache] documentation for implementation examples.
 *
 * **Memory Considerations**: The cache grows with each unique assertion until entries
 * expire or the [maxSize] limit is reached. Ensure adequate memory for your expected
 * authentication volume.
 *
 * ## Eviction Policy
 *
 * Assertion IDs are automatically evicted after their expiration time to prevent
 * unbounded memory growth. A background coroutine removes expired entries
 * every 5 minutes. The cache also respects the [maxSize] limit by evicting
 * the oldest entries when capacity is reached.
 *
 * ## Cache TTL
 *
 * Entries are stored until their `expirationTime` (derived from the assertion's
 * `NotOnOrAfter` condition). This ensures replay protection remains effective
 * for the entire validity period of each assertion.
 *
 * @property maxSize Maximum number of assertion IDs to cache (default: 10,000).
 *                   When this limit is reached, the oldest entries are evicted.
 * @param parentScope Optional parent scope for cleanup coroutine lifecycle management
 */
@OptIn(ExperimentalTime::class)
public class InMemorySamlReplayCache(
    private val maxSize: Int = 10_000,
    parentScope: CoroutineScope? = null
) : SamlReplayCache {

    private val cache = mutableMapOf<String, Instant>()
    private val mutex = Mutex()
    private val scope = parentScope ?: CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val cleanupJob: Job

    init {
        require(maxSize > 0) { "maxSize must be positive, got: $maxSize" }

        cleanupJob = scope.launch {
            while (isActive) {
                delay(5.minutes)
                cleanupExpiredEntries()
            }
        }
    }

    /**
     * Removes expired assertion IDs from the cache.
     */
    private suspend fun cleanupExpiredEntries() = mutex.withLock {
        val now = Clock.System.now()
        val expiredKeys = cache.entries
            .filter { it.value < now }
            .map { it.key }
        expiredKeys.forEach { cache.remove(it) }
    }

    /**
     * Closes the replay cache and cancels the cleanup coroutine.
     * Should be called when the cache is no longer needed.
     */
    public override fun close() {
        cleanupJob.cancel()
    }

    override suspend fun isReplayed(assertionId: String): Boolean =
        mutex.withLock { isReplayedImpl(assertionId) }

    override suspend fun recordAssertion(assertionId: String, expirationTime: Instant): Unit =
        mutex.withLock { recordAssertionImpl(assertionId, expirationTime) }

    override suspend fun tryRecordAssertion(assertionId: String, expirationTime: Instant): Boolean = mutex.withLock {
        if (isReplayedImpl(assertionId)) return false
        recordAssertionImpl(assertionId, expirationTime)
        return true
    }

    private fun isReplayedImpl(assertionId: String): Boolean {
        val cachedExpiration = cache[assertionId] ?: return false
        return cachedExpiration > Clock.System.now()
    }

    private fun recordAssertionImpl(assertionId: String, expirationTime: Instant) {
        // Enforce maximum cache size
        if (cache.size >= maxSize) {
            // Remove some expired entries first
            val now = Clock.System.now()
            val expiredKeys = cache.entries
                .filter { it.value < now }
                .map { it.key }
            expiredKeys.forEach { cache.remove(it) }

            // If still too large, remove the oldest entries
            if (cache.size >= maxSize) {
                val toRemove = cache.entries
                    .sortedBy { it.value.toEpochMilliseconds() }
                    .take((maxSize / 10).coerceAtLeast(1))
                    .map { it.key }
                toRemove.forEach { cache.remove(it) }
            }
        }
        cache[assertionId] = expirationTime
    }
}
