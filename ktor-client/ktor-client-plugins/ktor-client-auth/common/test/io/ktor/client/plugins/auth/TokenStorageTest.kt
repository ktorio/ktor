/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth

import io.ktor.client.plugins.auth.providers.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TokenStorageTest {
    private val testScope = TestScope()

    @Test
    fun testCachingTokenStorage() = testScope.runTest {
        var loadCount = 0

        val storage = TokenStorageFactory.withCache<String> {
            loadCount++
            "token-$loadCount"
        }

        // First call should load the token
        assertEquals("token-1", storage.loadToken())
        assertEquals(1, loadCount)

        // Second call should use cached token
        assertEquals("token-1", storage.loadToken())
        assertEquals(1, loadCount)

        // Clear should reset the stored token
        storage.clearToken()

        // Next call should load a new token
        assertEquals("token-2", storage.loadToken())
        assertEquals(2, loadCount)
    }

    @Test
    fun testNonCachingTokenStorage() = testScope.runTest {
        var loadCount = 0

        val storage = TokenStorageFactory.nonCaching<String> {
            loadCount++
            "token-$loadCount"
        }

        // Every call should load a fresh token
        assertEquals("token-1", storage.loadToken())
        assertEquals(1, loadCount)

        assertEquals("token-2", storage.loadToken())
        assertEquals(2, loadCount)

        assertEquals("token-3", storage.loadToken())
        assertEquals(3, loadCount)
    }

    @Test
    fun testUpdateTokenWithCaching() = testScope.runTest {
        var loadCount = 0

        val storage = TokenStorageFactory.withCache<String> {
            loadCount++
            "token-$loadCount"
        }

        // Initial token
        assertEquals("token-1", storage.loadToken())
        assertEquals(1, loadCount)

        // Update token
        val updatedToken = storage.updateToken { "updated-token" }
        assertEquals("updated-token", updatedToken)

        // Should use the updated token
        assertEquals("updated-token", storage.loadToken())
        assertEquals(1, loadCount)
    }

    @Test
    fun testUpdateTokenWithNonCaching() = testScope.runTest {
        var loadCount = 0

        val storage = TokenStorageFactory.nonCaching<String> {
            loadCount++
            "token-$loadCount"
        }

        // Initial token
        assertEquals("token-1", storage.loadToken())
        assertEquals(1, loadCount)

        // Update token - for non-caching this only affects the temporary token
        val updatedToken = storage.updateToken { "updated-token" }
        assertEquals("updated-token", updatedToken)

        // Still returns the updated token for this request cycle
        assertEquals("updated-token", storage.loadToken())
        assertEquals(1, loadCount)

        // Clear tokens
        storage.clearToken()

        // Should load fresh token
        assertEquals("token-2", storage.loadToken())
        assertEquals(2, loadCount)
    }

    @Test
    fun testConcurrentUpdates() = testScope.runTest {
        var loadCount = 0

        val storage = TokenStorageFactory.withCache<String> {
            loadCount++
            delay(50)
            "token-$loadCount"
        }

        // Launch multiple concurrent token loads
        val results = (1..3).map {
            async {
                storage.loadToken()
            }
        }

        // All should get the same token and only one load should happen
        results.forEach { assertEquals("token-1", it.await()) }
        assertEquals(1, loadCount)
    }

    @Test
    fun testConcurrentUpdatesWithNonCaching() = testScope.runTest {
        var loadCount = 0

        val storage = TokenStorageFactory.nonCaching<String> {
            loadCount++
            "token-$loadCount"
        }

        // Update with delayed computation
        val updateJob = async {
            storage.updateToken {
                delay(100)
                "updated-token"
            }
        }

        // Load token while update is in progress
        delay(50)
        assertEquals("updated-token", storage.loadToken())

        // Finish update
        assertEquals("updated-token", updateJob.await())
    }

    @Test
    fun testNullTokens() = testScope.runTest {
        var shouldReturnNull = true
        val storage = TokenStorageFactory.withCache<String> {
            if (shouldReturnNull) null else "token"
        }

        // Initial load returns null
        assertNull(storage.loadToken())

        // Change to return token and clear cached null
        shouldReturnNull = false
        storage.clearToken()

        // Now should return token
        assertEquals("token", storage.loadToken())
    }
}
