/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth

import io.ktor.client.plugins.auth.providers.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AuthTokenHolderTest {

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun testSetTokenCalledOnce() = runTest {
        val holder = AuthTokenHolder<BearerTokens> { TODO() }

        val monitor = Job()
        var firstExecuted = false
        var secondExecuted = false
        val first = GlobalScope.launch(Dispatchers.Unconfined) {
            holder.setToken {
                firstExecuted = true
                monitor.join()
                BearerTokens("1", "2")
            }
        }

        val second = GlobalScope.launch(Dispatchers.Unconfined) {
            holder.setToken {
                secondExecuted = true
                BearerTokens("1", "2")
            }
        }

        monitor.complete()
        first.join()
        second.join()

        assertTrue(firstExecuted)
        assertFalse(secondExecuted)
    }

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun testLoadTokenWaitsUntilTokenIsLoaded() = runTest {
        val monitor = Job()
        val holder = AuthTokenHolder {
            monitor.join()
            BearerTokens("1", "2")
        }

        val first = GlobalScope.async(Dispatchers.Unconfined) {
            holder.loadToken()
        }

        val second = GlobalScope.async(Dispatchers.Unconfined) {
            holder.loadToken()
        }

        monitor.complete()
        assertNotNull(first.await())
        assertNotNull(second.await())
    }

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun testClearCalledWhileLoadingTokens() = runTest {
        val monitor = Job()

        var clearTokenCalled = false
        val holder = AuthTokenHolder {
            // suspend until clearToken is called
            while (!clearTokenCalled) {
                delay(10)
            }

            monitor.join()
            BearerTokens("1", "2")
        }

        val first = GlobalScope.async(Dispatchers.Unconfined) {
            holder.loadToken()
        }

        val second = GlobalScope.async(Dispatchers.Unconfined) {
            holder.clearToken()
            clearTokenCalled = true
        }

        monitor.complete()
        assertNotNull(first.await())
        assertNotNull(second.await())
        assertTrue(clearTokenCalled)
    }

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun testClearCalledWhileSettingTokens() = runTest {
        val monitor = Job()

        var clearTokenCalled = false
        val holder = AuthTokenHolder<BearerTokens> {
            fail("loadTokens argument function shouldn't be invoked")
        }

        val first = GlobalScope.async(Dispatchers.Unconfined) {
            holder.setToken {
                // suspend until clearToken is called
                while (!clearTokenCalled) {
                    delay(10)
                }
                monitor.join()
                BearerTokens("1", "2")
            }
        }

        val second = GlobalScope.async(Dispatchers.Unconfined) {
            holder.clearToken()
            clearTokenCalled = true
        }

        monitor.complete()
        assertNotNull(first.await())
        assertNotNull(second.await())
        assertTrue(clearTokenCalled)
    }

    @Test
    fun testExceptionInLoadTokens() = runTest {
        var firstCall = true
        val holder = AuthTokenHolder {
            if (firstCall) {
                firstCall = false
                throw IllegalStateException("First call")
            }
            "token"
        }
        assertFailsWith<IllegalStateException> { holder.loadToken() }
        assertEquals("token", holder.loadToken())
    }

    @Test
    fun testExceptionInSetTokens() = runTest {
        val holder = AuthTokenHolder<String> {
            fail("loadTokens argument function shouldn't be invoked")
        }
        assertFailsWith<IllegalStateException> { holder.setToken { throw IllegalStateException("First call") } }
        assertEquals("token", holder.setToken { "token" })
    }
}
