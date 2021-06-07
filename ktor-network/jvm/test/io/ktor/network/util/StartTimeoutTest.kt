/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.util

import kotlinx.coroutines.*
import kotlin.test.*

class StartTimeoutTest {

    private data class TestClock(var timeMs: Long)

    private val timeoutMs: Long = 100
    private val clock = TestClock(2000)

    @Test
    fun testTimeoutInvocation() = runBlocking {
        var timeoutInvoked = false

        val timeout = createTimeout("test", timeoutMs, clock::timeMs) { timeoutInvoked = true }
        timeout.start()
        yield()

        clock.timeMs += timeoutMs
        delay(timeoutMs)
        yield()
        assertTrue(timeoutInvoked)
    }

    @Test
    fun testTimeoutCancellation() = runBlocking {
        var timeoutInvoked = false

        val timeout = createTimeout("test", timeoutMs, clock::timeMs) { timeoutInvoked = true }
        timeout.start()
        yield()

        clock.timeMs += timeoutMs
        timeout.finish()
        delay(timeoutMs)
        yield()
        assertFalse(timeoutInvoked)
    }

    @Test
    fun testTimeoutUpdateActivityTime() = runBlocking {
        var timeoutInvoked = false
        val timeout = createTimeout("test", timeoutMs, clock::timeMs) { timeoutInvoked = true }
        timeout.start()
        yield()

        clock.timeMs += timeoutMs
        timeout.start()
        delay(timeoutMs)
        yield()
        assertFalse(timeoutInvoked)

        clock.timeMs += timeoutMs
        timeout.start()
        delay(timeoutMs)
        yield()
        assertFalse(timeoutInvoked)

        clock.timeMs += timeoutMs
        delay(timeoutMs)
        yield()
        assertTrue(timeoutInvoked)
    }

    @Test
    fun testTimeoutDoesNotTriggerWhenStopped() = runBlocking {
        var timeoutInvoked = false
        val timeout = createTimeout("test", timeoutMs, clock::timeMs) { timeoutInvoked = true }
        timeout.start()
        yield()

        clock.timeMs += timeoutMs
        timeout.start()
        delay(timeoutMs)
        yield()
        assertFalse(timeoutInvoked)

        timeout.stop()
        clock.timeMs += timeoutMs
        delay(timeoutMs)
        yield()
        assertFalse(timeoutInvoked)

        timeout.start()
        clock.timeMs += timeoutMs / 2
        delay(timeoutMs / 2)
        yield()
        assertFalse(timeoutInvoked)

        clock.timeMs += timeoutMs / 2
        delay(timeoutMs / 2)
        yield()
        assertTrue(timeoutInvoked)
    }

    @Test
    fun testTimeoutCancelsWhenParentScopeCancels() = runBlocking {
        var timeoutInvoked = false
        val scope = CoroutineScope(GlobalScope.coroutineContext)
        val timeout = scope.createTimeout("test", timeoutMs, clock::timeMs) { timeoutInvoked = true }
        timeout.start()

        runCatching { scope.cancel(CancellationException()) }
        clock.timeMs += timeoutMs

        delay(timeoutMs)
        assertFalse(timeoutInvoked)
    }
}
