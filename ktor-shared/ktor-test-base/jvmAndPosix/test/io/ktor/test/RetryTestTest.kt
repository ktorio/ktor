/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertSame

/**
 * Covers `retryTest`'s loop control on the targets whose actual runs the attempts synchronously.
 *
 * The web actual chains promises instead, so a test lambda that throws synchronously — the only way
 * to provoke a failure without a real `runTest` — never reaches its `catch` links and would prove
 * nothing there. Its behavior is exercised through `BaseTest` in the server test suites.
 */
class RetryTestTest {

    @Test
    fun `stops on the attempt that recorded a deterministic failure`() {
        val guard = DeterministicFailureGuard()
        val cause = AssertionError("deterministic")
        var attempts = 0

        val thrown = assertFails {
            retryTest(retries = 3, shouldRetry = { !guard.hasFailure }) { _ ->
                attempts++
                guard.record(cause)
                throw cause
            }
        }

        // Without the predicate the loop would run all four attempts to reach a verdict the first
        // one already produced.
        assertEquals(1, attempts, "A deterministic failure should not be attempted again")
        assertSame(cause, thrown, "The recorded failure should surface as is")
    }

    @Test
    fun `keeps retrying transient failures`() {
        var attempts = 0

        retryTest(retries = 3) { _ ->
            attempts++
            if (attempts < 3) throw IllegalStateException("connection reset")
            DummyTestResult
        }

        assertEquals(3, attempts, "Transient failures should be retried until one succeeds")
    }

    @Test
    fun `exhausts the retries and rethrows the last failure`() {
        var attempts = 0

        val thrown = assertFails {
            retryTest(retries = 2) { attempt ->
                attempts++
                throw IllegalStateException("attempt $attempt")
            }
        }

        assertEquals(3, attempts, "One initial attempt plus two retries")
        assertEquals("attempt 2", thrown.message)
    }
}
