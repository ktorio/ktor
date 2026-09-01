/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class EventuallyTest {

    @Test
    fun `returns without waiting when the condition already holds`() = runTest {
        var evaluations = 0

        assertEventually("an already satisfied condition") { evaluations++ >= 0 }

        assertEquals(1, evaluations, "The condition should be evaluated once and not polled again")
    }

    @Test
    fun `polls until the condition becomes true`() = runTest {
        var remaining = 3

        assertEventually("the counter to reach zero", timeout = 5.seconds, interval = 1.milliseconds) {
            remaining-- == 0
        }

        assertEquals(-1, remaining, "Polling should stop on the attempt that satisfies the condition")
    }

    @Test
    fun `fails with the description when the condition never holds`() = runTest {
        val error = assertFailsWith<EventuallyTimeoutException> {
            assertEventually("something that never happens", timeout = 50.milliseconds, interval = 1.milliseconds) {
                false
            }
        }

        assertContains(error.message.orEmpty(), "something that never happens")
    }

    @Test
    fun `evaluates the condition at least once with a zero timeout`() = runTest {
        var evaluations = 0

        assertEventually("an immediately satisfied condition", timeout = Duration.ZERO) { evaluations++ >= 0 }

        assertEquals(1, evaluations)
    }

    @Test
    fun `honors the timeout even when the interval is larger`() = runTest {
        val start = TimeSource.Monotonic.markNow()

        assertFailsWith<EventuallyTimeoutException> {
            assertEventually("never", timeout = 100.milliseconds, interval = 5.seconds) { false }
        }

        // Without clamping the poll to the time remaining, the first delay would run the full 5s
        // interval and blow through the 100ms upper bound this helper documents.
        val elapsed = start.elapsedNow()
        assertTrue(elapsed < 1.seconds, "Expected the wait to stop near the timeout, but $elapsed elapsed")
    }

    @Test
    fun `timing out stays retryable rather than being classified deterministic`() {
        val guard = DeterministicFailureGuard()

        guard.record(EventuallyTimeoutException("Timed out after 1s waiting for something"))

        // Waiting for a condition and running out of time is load-dependent, so the retry loop must
        // get another attempt. An AssertionError here would make the guard replay it immediately.
        assertFalse(guard.hasFailure, "An assertEventually timeout should not stop the retry loop")
    }

    @Test
    fun `waits in real time so the virtual clock does not skip the poll interval`() = runTest {
        val start = TimeSource.Monotonic.markNow()

        val error = assertFailsWith<EventuallyTimeoutException> {
            assertEventually("never", timeout = 100.milliseconds, interval = 20.milliseconds) { false }
        }

        // Inside `runTest` a virtual `delay` would return instantly, making this a busy loop that
        // still eventually fails. Asserting real elapsed time is what distinguishes the two.
        val elapsed = start.elapsedNow()
        assertContains(error.message.orEmpty(), "never")
        assertTrue(elapsed >= 100.milliseconds, "Expected a real wait, but only $elapsed elapsed")
    }
}
