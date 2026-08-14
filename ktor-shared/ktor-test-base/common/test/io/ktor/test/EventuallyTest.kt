/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class EventuallyTest {

    @Test
    fun `returns without waiting when the condition already holds`(): TestResult = runTest {
        var evaluations = 0

        assertEventually("an already satisfied condition") { evaluations++ >= 0 }

        assertEquals(1, evaluations, "The condition should be evaluated once and not polled again")
    }

    @Test
    fun `polls until the condition becomes true`(): TestResult = runTest {
        var remaining = 3

        assertEventually("the counter to reach zero", timeout = 5.seconds, interval = 1.milliseconds) {
            remaining-- == 0
        }

        assertEquals(-1, remaining, "Polling should stop on the attempt that satisfies the condition")
    }

    @Test
    fun `fails with the description when the condition never holds`(): TestResult = runTest {
        val error = assertFailsWith<AssertionError> {
            assertEventually("something that never happens", timeout = 50.milliseconds, interval = 1.milliseconds) {
                false
            }
        }

        assertContains(error.message.orEmpty(), "something that never happens")
    }

    @Test
    fun `evaluates the condition at least once with a zero timeout`(): TestResult = runTest {
        var evaluations = 0

        assertEventually("an immediately satisfied condition", timeout = Duration.ZERO) { evaluations++ >= 0 }

        assertEquals(1, evaluations)
    }

    @Test
    fun `waits in real time so the virtual clock does not skip the poll interval`(): TestResult = runTest {
        val start = TimeSource.Monotonic.markNow()

        val error = assertFailsWith<AssertionError> {
            assertEventually("never", timeout = 100.milliseconds, interval = 20.milliseconds) { false }
        }

        // Inside `runTest` a virtual `delay` would return instantly, making this a busy loop that
        // still eventually fails. Asserting real elapsed time is what distinguishes the two.
        val elapsed = start.elapsedNow()
        assertContains(error.message.orEmpty(), "never")
        assertTrue(elapsed >= 100.milliseconds, "Expected a real wait, but only $elapsed elapsed")
    }
}
