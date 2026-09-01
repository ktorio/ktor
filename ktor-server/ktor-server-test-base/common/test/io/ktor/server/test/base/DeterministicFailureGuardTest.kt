/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.test.base

import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertSame

class DeterministicFailureGuardTest {

    @Test
    fun `assertion failure is replayed instead of retried`() {
        val guard = DeterministicFailureGuard()
        val cause = AssertionError("deterministic")

        guard.record(cause)

        val replayed = assertFails { guard.failFast() }
        assertSame(cause, replayed, "The recorded failure should be re-thrown as is")
    }

    @Test
    fun `transient failure stays retryable`() {
        val guard = DeterministicFailureGuard()

        guard.record(IllegalStateException("connection reset"))

        guard.failFast() // Doesn't throw, so the next attempt runs the test body again.
    }
}
