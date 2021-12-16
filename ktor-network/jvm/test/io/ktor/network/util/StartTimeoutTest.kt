/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.util

import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.*
import kotlin.time.Duration.Companion.milliseconds

@ExperimentalTime
class StartTimeoutTest {

    private data class TestClock(var timeMs: Long)

    private val timeoutDuration = 100.milliseconds

    @Test
    fun testTimeoutInvocation() = runBlocking {
        val testTimeSource = TestTimeSource()
        val clock = testTimeSource.toGMTClock()
        var timeoutInvoked = false

        val timeout = createTimeout("test", timeoutDuration, clock) { timeoutInvoked = true }
        timeout.start()
        yield()

        testTimeSource += timeoutDuration
        delay(timeoutDuration)
        yield()
        assertTrue(timeoutInvoked)
    }

    @Test
    fun testTimeoutCancellation() = runBlocking {
        val testTimeSource = TestTimeSource()
        val clock = testTimeSource.toGMTClock()

        var timeoutInvoked = false

        val timeout = createTimeout("test", timeoutDuration, clock) { timeoutInvoked = true }
        timeout.start()
        yield()

        testTimeSource += timeoutDuration
        timeout.finish()
        delay(timeoutDuration)
        yield()
        assertFalse(timeoutInvoked)
    }

    @Test
    fun testTimeoutUpdateActivityTime() = runBlocking {
        val testTimeSource = TestTimeSource()
        val clock = testTimeSource.toGMTClock()

        var timeoutInvoked = false
        val timeout = createTimeout("test", timeoutDuration, clock) { timeoutInvoked = true }
        timeout.start()
        yield()

        testTimeSource += timeoutDuration
        timeout.start()
        delay(timeoutDuration)
        yield()
        assertFalse(timeoutInvoked)

        testTimeSource += timeoutDuration
        timeout.start()
        delay(timeoutDuration)
        yield()
        assertFalse(timeoutInvoked)

        testTimeSource += timeoutDuration
        delay(timeoutDuration)
        yield()
        assertTrue(timeoutInvoked)
    }

    @Test
    fun testTimeoutDoesNotTriggerWhenStopped() = runBlocking {
        val testTimeSource = TestTimeSource()
        val clock = testTimeSource.toGMTClock()

        var timeoutInvoked = false
        val timeout = createTimeout("test", timeoutDuration, clock) { timeoutInvoked = true }
        timeout.start()
        yield()

        testTimeSource += timeoutDuration
        timeout.start()
        delay(timeoutDuration)
        yield()
        assertFalse(timeoutInvoked)

        timeout.stop()
        testTimeSource += timeoutDuration
        delay(timeoutDuration)
        yield()
        assertFalse(timeoutInvoked)

        timeout.start()
        testTimeSource += timeoutDuration / 2
        delay(timeoutDuration / 2)
        yield()
        assertFalse(timeoutInvoked)

        testTimeSource += timeoutDuration / 2
        delay(timeoutDuration / 2)
        yield()
        assertTrue(timeoutInvoked)
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testTimeoutCancelsWhenParentScopeCancels() = runBlocking {
        val testTimeSource = TestTimeSource()
        val clock = testTimeSource.toGMTClock()

        var timeoutInvoked = false
        val scope = CoroutineScope(GlobalScope.coroutineContext)
        val timeout = scope.createTimeout("test", timeoutDuration, clock) { timeoutInvoked = true }
        timeout.start()

        runCatching { scope.cancel(CancellationException()) }
        testTimeSource += timeoutDuration

        delay(timeoutDuration)
        assertFalse(timeoutInvoked)
    }
}
