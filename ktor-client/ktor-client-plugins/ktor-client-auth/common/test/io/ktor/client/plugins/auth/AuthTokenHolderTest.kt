/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth

import io.ktor.client.plugins.auth.providers.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.*

class AuthTokenHolderTest {

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun testOnlyOneSetTokenCallComputesBlock() = runTest {
        val holder = AuthTokenHolder<Int> { fail() }

        var firstCalled = false
        val first = GlobalScope.launch(Dispatchers.Unconfined) {
            holder.setToken {
                firstCalled = true
                delay(100)
                1
            }
        }

        var secondCalled = false
        val second = GlobalScope.launch(Dispatchers.Unconfined) {
            delay(50)
            holder.setToken {
                secondCalled = true
                2
            }
        }

        first.join()
        second.join()

        val token = holder.loadToken()
        assertEquals(token, 1)
        assertTrue { firstCalled }
        assertFalse { secondCalled }
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
            1
        }

        val first = GlobalScope.async(Dispatchers.Unconfined) {
            holder.loadToken()
        }

        val second = GlobalScope.async(Dispatchers.Unconfined) {
            holder.clearToken()
            clearTokenCalled = true
        }

        monitor.complete()
        assertEquals(1, first.await())
        second.await()
        assertTrue(clearTokenCalled)
        assertNull(holder.get())
    }

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun testClearCalledWhileSettingTokens() = runTest {
        val monitor = Job()

        var clearTokenCalled = false
        val holder = AuthTokenHolder<Int> {
            fail("loadTokens argument function shouldn't be invoked")
        }

        val first = GlobalScope.async(Dispatchers.Unconfined) {
            holder.setToken {
                // suspend until clearToken is called
                while (!clearTokenCalled) {
                    delay(10)
                }
                monitor.join()
                1
            }
        }

        val second = GlobalScope.async(Dispatchers.Unconfined) {
            holder.clearToken()
            clearTokenCalled = true
        }

        monitor.complete()
        assertEquals(1, first.await())
        second.await()
        assertTrue(clearTokenCalled)
        assertNull(holder.get())
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

    internal class MyContext(val value: Int) : CoroutineContext.Element {
        override val key: CoroutineContext.Key<*>
            get() = MyContext

        companion object : CoroutineContext.Key<MyContext>
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun firstLoadTokenCallComputesBlockAndSetsValue() = runTest {
        val holder = AuthTokenHolder {
            coroutineScope {
                val context = coroutineContext[MyContext]
                assertNotNull(context)
                context.value
            }
        }
        val first = GlobalScope.async(Dispatchers.Unconfined) {
            delay(50)
            withContext(MyContext(1)) {
                holder.loadToken()
            }
        }

        val second = GlobalScope.async(Dispatchers.Unconfined) {
            withContext(MyContext(2)) {
                holder.loadToken()
            }
        }

        assertEquals(2, first.await())
        assertEquals(2, second.await())
        assertEquals(2, holder.get())
        assertEquals(2, holder.loadToken())
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun firstSetTokenCallComputesBlockAndSetsValue() = runTest {
        val holder = AuthTokenHolder<Int> {
            fail()
        }

        val first = GlobalScope.async(Dispatchers.Unconfined) {
            delay(50)
            holder.setToken {
                1
            }
        }

        val second = GlobalScope.async(Dispatchers.Unconfined) {
            holder.setToken {
                delay(100)
                2
            }
        }

        assertEquals(2, first.await())
        assertEquals(2, second.await())
        assertEquals(2, holder.get())
        assertEquals(2, holder.loadToken())
    }

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun testClearCoroutineResetsCachedValue() = runTest {
        val holder = AuthTokenHolder {
            delay(200)
            1
        }

        val loadToken = GlobalScope.async(Dispatchers.Unconfined) {
            holder.loadToken()
        }

        val setToken = GlobalScope.async(Dispatchers.Unconfined) {
            delay(50)
            holder.setToken {
                delay(100)
                2
            }
        }

        val clear = GlobalScope.async(Dispatchers.Unconfined) {
            delay(100)
            holder.clearToken()
        }

        assertEquals(1, loadToken.await())
        assertEquals(2, setToken.await())
        clear.await()
        assertNull(holder.get())
    }

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun lockedSetTokenByLoadTokenSetsValue() = runTest {
        val holder = AuthTokenHolder {
            delay(200)
            1
        }

        val loadToken = GlobalScope.async(Dispatchers.Unconfined) {
            holder.loadToken()
        }

        val setToken = GlobalScope.async(Dispatchers.Unconfined) {
            delay(100)
            holder.setToken {
                2
            }
        }

        assertEquals(1, loadToken.await())
        assertEquals(2, setToken.await())
        assertEquals(2, holder.loadToken())
    }

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun loadTokensCanBeCalledInSetTokenBlock() = runTest {
        val holder = AuthTokenHolder {
            1
        }

        val setToken = GlobalScope.async(Dispatchers.Unconfined) {
            holder.setToken {
                1 + holder.loadToken()!!
            }
        }

        assertEquals(2, setToken.await())
        assertEquals(2, holder.loadToken())
    }
}
