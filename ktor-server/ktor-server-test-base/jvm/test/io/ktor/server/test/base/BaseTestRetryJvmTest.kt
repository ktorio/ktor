/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.test.base

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

/**
 * Verifies how [BaseTest.runTest] classifies failures for retries.
 *
 * These assertions live in the JVM source set because there `TestResult` is `Unit`, so a failing
 * test propagates its failure synchronously and can be asserted on. On Web the same call returns
 * a `Promise`, which would reject asynchronously instead.
 */
class BaseTestRetryJvmTest {

    private class Fixture : BaseTest()

    @Test
    fun `assertion failure is not retried`() {
        var attempts = 0

        assertFailsWith<AssertionError> {
            Fixture().runTest(retries = 3) {
                attempts++
                fail("Deterministic failure")
            }
        }

        assertEquals(1, attempts, "An assertion failure is deterministic and must not be retried")
    }

    @Test
    fun `transient failure is retried`() {
        var attempts = 0

        Fixture().runTest(retries = 3) {
            if (attempts++ < 3) throw IllegalStateException("Transient failure")
        }

        assertEquals(4, attempts, "Transient failures should be retried until they pass")
    }
}
