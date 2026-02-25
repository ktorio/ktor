/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class InMemorySamlReplayCacheTest {

    @Test
    fun `test basic replay detection`() = runTest {
        InMemorySamlReplayCache().use { cache ->
            val assertionId = "test-assertion-id"
            cache.recordAssertion(assertionId, expirationTime = Clock.System.now() + 300.seconds)
            assertTrue(cache.isReplayed(assertionId))

            val expiredId = "expired-assertion-id"
            cache.recordAssertion(assertionId = expiredId, expirationTime = Clock.System.now() - 10.seconds)
            assertFalse(cache.isReplayed(expiredId))

            assertFalse(cache.isReplayed("new-assertion-id"))
        }
    }

    @Test
    fun `test max size enforcement`() = runTest {
        val maxSize = 10
        InMemorySamlReplayCache(maxSize = maxSize).use { cache ->
            val expirationTime = Clock.System.now() + 300.seconds

            // Add more than maxSize assertions
            for (i in 1..15) {
                cache.recordAssertion("assertion-$i", expirationTime)
            }

            val isRecentReplayed = cache.isReplayed("assertion-15")
            assertTrue(isRecentReplayed, "Recent assertion should still be in cache")
        }
    }

    @Test
    fun `test concurrent access safety`() = runTest {
        InMemorySamlReplayCache().use { cache ->
            val expirationTime = Clock.System.now() + 300.seconds
            val coroutineCount = 50
            val assertionsPerCoroutine = 100

            val jobs = (0 until coroutineCount).map { coroutineIndex ->
                launch {
                    repeat(assertionsPerCoroutine) { assertionIndex ->
                        val assertionId = "coroutine-$coroutineIndex-assertion-$assertionIndex"
                        cache.recordAssertion(assertionId, expirationTime)

                        // Verify it's now replayed
                        val isReplayed = cache.isReplayed(assertionId)
                        assertTrue(isReplayed, "Assertion should be replayed after recording")
                    }
                }
            }

            jobs.joinAll()
        }
    }

    @Test
    fun `test cleanup removes expired entries`() = runTest {
        InMemorySamlReplayCache().use { cache ->
            val expiredTime = Clock.System.now() - 10.seconds
            val validTime = Clock.System.now() + 300.seconds

            cache.recordAssertion("expired-assertion", expiredTime)
            cache.recordAssertion("valid-assertion", validTime)

            delay(100)

            assertFalse(cache.isReplayed("expired-assertion"))
            assertTrue(cache.isReplayed("valid-assertion"))
        }
    }

    @Test
    fun `test recording same assertion ID updates expiration`() = runTest {
        InMemorySamlReplayCache().use { cache ->
            val assertionId = "test-assertion"
            val firstExpiration = Clock.System.now() + 100.seconds
            val secondExpiration = Clock.System.now() + 300.seconds

            cache.recordAssertion(assertionId, firstExpiration)
            assertTrue(cache.isReplayed(assertionId))

            cache.recordAssertion(assertionId, secondExpiration)
            assertTrue(cache.isReplayed(assertionId))
        }
    }
}
