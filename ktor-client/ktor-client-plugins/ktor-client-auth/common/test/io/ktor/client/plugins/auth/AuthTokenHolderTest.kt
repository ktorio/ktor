/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth

import io.ktor.client.plugins.auth.providers.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.*

class AuthTokenHolderTest {

    private val testScope = TestScope()

    @Test
    fun testOnlyOneSetTokenCallComputesBlock() = testScope.runTest {
        val holder = AuthTokenHolder<Int> { fail() }

        // First job starts immediately, but takes some time to complete its block
        var firstCalled = false
        val firstJob = launch {
            holder.setToken {
                firstCalled = true
                delay(100)
                1
            }
        }

        // Second job starts with delay, but completes quickly
        var secondCalled = false
        val secondJob = launch {
            delay(50)
            holder.setToken {
                secondCalled = true
                2
            }
        }

        firstJob.join()
        secondJob.join()

        assertEquals(1, holder.loadToken())
        assertTrue(firstCalled, "setToken from the first job should be called")
        assertFalse(secondCalled, "setToken from the second job should not be called")
    }

    @Test
    fun testLoadTokenWaitsUntilTokenIsLoaded() = testScope.runTest {
        val loadingCompletionTrigger = Job()

        val holder = AuthTokenHolder {
            loadingCompletionTrigger.join()
            BearerTokens("1", "2")
        }

        // Start two concurrent loadToken operations
        val firstLoadJob = async { holder.loadToken() }
        val secondLoadJob = async { holder.loadToken() }

        // Allow the token loading to complete
        loadingCompletionTrigger.complete()

        val firstResult = firstLoadJob.await()
        val secondResult = secondLoadJob.await()

        assertNotNull(firstResult)
        assertNotNull(secondResult)
        assertEquals(firstResult, secondResult)
    }

    @Test
    fun testClearCalledWhileLoadingTokens() = testScope.runTest {
        val loadStarted = Job()
        val clearDone = Job()

        val holder = AuthTokenHolder {
            loadStarted.complete()
            // Suspend until clearToken is called
            clearDone.join()
            1
        }

        val loadJob = async {
            holder.loadToken()
        }

        // Wait for loading start
        loadStarted.join()
        // And then clear the token
        holder.clearToken(testScope)
        clearDone.complete()

        assertEquals(1, loadJob.await())
        assertNull(holder.get())
    }

    @Test
    fun testClearCalledWhileSettingTokens() = testScope.runTest {
        val setTokenStarted = Job()
        val clearDone = Job()

        val holder = AuthTokenHolder<Int> {
            fail("loadTokens argument function shouldn't be invoked")
        }

        val setTokenJob = async {
            holder.setToken {
                setTokenStarted.complete()
                clearDone.join()
                1
            }
        }

        setTokenStarted.join()
        holder.clearToken(testScope)
        clearDone.complete()

        assertEquals(1, setTokenJob.await())
        assertNull(holder.get())
    }

    @Test
    fun testExceptionInLoadTokens() = testScope.runTest {
        var firstCall = true
        val holder = AuthTokenHolder {
            if (firstCall) {
                firstCall = false
                throw IllegalStateException("First call failed")
            }
            "token"
        }
        assertFailsWith<IllegalStateException> { holder.loadToken() }
        assertEquals("token", holder.loadToken())
    }

    @Test
    fun testExceptionInSetTokens() = testScope.runTest {
        val holder = AuthTokenHolder<String> {
            fail("loadTokens argument function shouldn't be invoked")
        }
        assertFailsWith<IllegalStateException> { holder.setToken { throw IllegalStateException("First call") } }
        assertEquals("token", holder.setToken { "token" })
    }

    internal class ResultContextElement(val value: Int) : CoroutineContext.Element {
        override val key: CoroutineContext.Key<*>
            get() = ResultContextElement

        companion object : CoroutineContext.Key<ResultContextElement>
    }

    @Test
    fun firstLoadTokenCallComputesBlockAndSetsValue() = testScope.runTest {
        val holder = AuthTokenHolder {
            val result = assertNotNull(currentCoroutineContext()[ResultContextElement])
            result.value
        }

        // First job starts with a delay
        val first = async {
            delay(50)
            withContext(ResultContextElement(1)) {
                holder.loadToken()
            }
        }

        // Second job starts immediately
        val second = async {
            withContext(ResultContextElement(2)) {
                holder.loadToken()
            }
        }

        assertEquals(2, first.await())
        assertEquals(2, second.await())
        assertEquals(2, holder.get())
        assertEquals(2, holder.loadToken())
    }

    @Test
    fun firstSetTokenCallComputesBlockAndSetsValue() = testScope.runTest {
        val holder = AuthTokenHolder<Int> {
            fail("loadTokens shouldn't be called in this test")
        }

        // First job starts with a delay, but completes its block quickly
        val firstJob = async {
            delay(50)
            holder.setToken { 1 }
        }

        // Second job starts first, but takes longer to complete its block
        val secondJob = async {
            holder.setToken {
                delay(100)
                2
            }
        }

        assertEquals(2, firstJob.await())
        assertEquals(2, secondJob.await())

        assertEquals(2, holder.get())
        assertEquals(2, holder.loadToken())
    }

    @Test
    fun testClearCoroutineResetsCachedValue() = testScope.runTest {
        val holder = AuthTokenHolder {
            // Simulate loading delay
            delay(200)
            1
        }

        val loadTokenLob = async {
            holder.loadToken()
        }

        val setTokenJob = async {
            delay(50)
            holder.setToken {
                delay(100)
                2
            }
        }

        val clearJob = launch {
            delay(100)
            holder.clearToken(testScope)
        }

        assertEquals(1, loadTokenLob.await())
        assertEquals(2, setTokenJob.await())
        clearJob.join()
        assertNull(holder.get())
    }

    @Test
    fun lockedSetTokenByLoadTokenSetsValue() = testScope.runTest {
        val holder = AuthTokenHolder {
            delay(200)
            1
        }

        val loadTokenJob = async {
            holder.loadToken()
        }

        val setTokenJob = async {
            delay(100)
            holder.setToken { 2 }
        }

        assertEquals(1, loadTokenJob.await())
        assertEquals(2, setTokenJob.await())
        assertEquals(2, holder.loadToken())
    }

    @Test
    fun loadTokensCanBeCalledInSetTokenBlock() = testScope.runTest {
        val holder = AuthTokenHolder { 1 }
        val result = holder.setToken { 1 + holder.loadToken()!! }

        assertEquals(2, result)
        assertEquals(2, holder.loadToken())
    }
}
