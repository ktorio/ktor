/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth

import io.ktor.client.plugins.auth.providers.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AuthTokenHolderTest {

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun testFirstResumedSetTokenBlockWins() = runTest {
        val holder = AuthTokenHolder<Int> { fail() }

        val monitor = Job()
        val first = GlobalScope.launch(Dispatchers.Unconfined) {
            holder.setToken {
                monitor.join()
                1
            }
        }

        val second = GlobalScope.launch(Dispatchers.Unconfined) {
            holder.setToken {
                2
            }
        }

        monitor.complete()
        first.join()
        second.join()

        val token = holder.loadToken()
        assertEquals(token, 2)
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
        assertNull(first.await())
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
        assertNull(first.await())
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

    private val value = atomic(0)

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun firstResumedLoadTokenCoroutineWins() = runTest {
        val holder = AuthTokenHolder {
            val v = value.incrementAndGet()
            delay(v * 50L)
            v
        }

        val first = GlobalScope.async(Dispatchers.Unconfined) {
            holder.loadToken()
        }

        val second = GlobalScope.async(Dispatchers.Unconfined) {
            holder.loadToken()
        }

        assertEquals(1, first.await())
        assertEquals(1, second.await())
        assertEquals(1, holder.loadToken())
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun firstResumedSetTokenCoroutineWins() = runTest {
        val holder = AuthTokenHolder<Int> {
            fail()
        }

        val first = GlobalScope.async(Dispatchers.Unconfined) {
            holder.setToken {
                delay(50)
                1
            }
        }

        val second = GlobalScope.async(Dispatchers.Unconfined) {
            holder.setToken {
                delay(100)
                2
            }
        }

        assertEquals(1, first.await())
        assertEquals(1, second.await())
        assertEquals(1, holder.loadToken())
    }

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun testFirstResumedClearCoroutineCancelsLoadsAndSets() = runTest {
        val holder = AuthTokenHolder {
            delay(200)
            1
        }

        val loadToken = GlobalScope.async(Dispatchers.Unconfined) {
            holder.loadToken()
        }

        val setToken = GlobalScope.async(Dispatchers.Unconfined) {
            holder.setToken {
                delay(100)
                2
            }
        }

        val clear = GlobalScope.async(Dispatchers.Unconfined) {
            delay(50)
            holder.clearToken()
        }

        assertNull(loadToken.await())
        assertNull(setToken.await())
        clear.await()
    }

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun firstLoadTokenCoroutineDoesNotCancelSetToken() = runTest {
        val holder = AuthTokenHolder {
            delay(50)
            1
        }

        val loadToken = GlobalScope.async(Dispatchers.Unconfined) {
            delay(50)
            holder.loadToken()
        }

        val setToken = GlobalScope.async(Dispatchers.Unconfined) {
            holder.setToken {
                delay(200)
                2
            }
        }

        assertEquals(1, loadToken.await())
        assertEquals(2, setToken.await())
        assertEquals(2, holder.loadToken())
    }

}
