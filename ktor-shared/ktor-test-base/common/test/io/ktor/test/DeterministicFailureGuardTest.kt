/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeterministicFailureGuardTest {

    @Test
    fun `assertion failure stops the retry loop`() {
        val guard = DeterministicFailureGuard()

        guard.record(AssertionError("deterministic"))

        assertTrue(guard.hasFailure)
    }

    @Test
    fun `transient failure stays retryable`() {
        val guard = DeterministicFailureGuard()

        guard.record(IllegalStateException("connection reset"))

        assertFalse(guard.hasFailure)
    }
}
