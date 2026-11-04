/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
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

        assertEventually { evaluations++ >= 0 }

        assertEquals(1, evaluations, "The condition should be evaluated once and not polled again")
    }

    @Test
    fun `polls until the condition becomes true`() = runTest {
        var remaining = 3

        assertEventually(timeout = 5.seconds, interval = 1.milliseconds) {
            remaining-- == 0
        }

        assertEquals(-1, remaining, "Polling should stop on the attempt that satisfies the condition")
    }

    @Test
    fun `fails when the condition never holds`() = runTest {
        assertFailsWith<TimeoutCancellationException> {
            assertEventually(timeout = 50.milliseconds, interval = 1.milliseconds) {
                false
            }
        }
    }

    @Test
    fun `evaluates the condition at least once with a zero timeout`() = runTest {
        var evaluations = 0

        assertEventually(timeout = Duration.ZERO) { evaluations++ >= 0 }

        assertEquals(1, evaluations)
    }

    @Test
    fun `honors the timeout even when the interval is larger`() = runTest {
        val start = TimeSource.Monotonic.markNow()

        assertFailsWith<TimeoutCancellationException> {
            assertEventually(timeout = 100.milliseconds, interval = 5.seconds) { false }
        }

        val elapsed = start.elapsedNow()
        assertTrue(elapsed < 1.seconds, "Expected the wait to stop near the timeout, but $elapsed elapsed")
    }

    @Test
    fun `timing out stays retryable rather than being classified deterministic`() = runTest {
        val guard = DeterministicFailureGuard()

        val error = assertFailsWith<TimeoutCancellationException> {
            assertEventually(timeout = Duration.ZERO) { false }
        }
        guard.record(error)

        assertFalse(guard.hasFailure, "An assertEventually timeout should not stop the retry loop")
    }

    @Test
    fun `waits in real time so the virtual clock does not skip the poll interval`() = runTest {
        val start = TimeSource.Monotonic.markNow()

        assertFailsWith<TimeoutCancellationException> {
            assertEventually(timeout = 100.milliseconds, interval = 20.milliseconds) { false }
        }

        val elapsed = start.elapsedNow()
        assertTrue(elapsed >= 100.milliseconds, "Expected a real wait, but only $elapsed elapsed")
    }
}
