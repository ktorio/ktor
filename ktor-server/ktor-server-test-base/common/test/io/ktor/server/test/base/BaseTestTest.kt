/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.test.base

import io.ktor.util.*
import kotlinx.coroutines.test.TestResult
import kotlin.test.Test
import kotlin.test.fail

class BaseTestTest : BaseTest() {

    @Test
    fun `runTest - retry test by default on non-JVM platform`(): TestResult {
        var retryCount = 0
        return runTest {
            if (!PlatformUtils.IS_JVM && retryCount++ < 1) fail("This test should be retried")
        }
    }

    @Test
    fun `runTest - don't retry test by default on JVM platform`(): TestResult {
        var retryCount = 0
        return runTest {
            if (PlatformUtils.IS_JVM && retryCount++ > 0) fail("This test should not be retried")
        }
    }

    @Test
    fun `runTest - more than one retry`(): TestResult {
        var retryCount = 0
        return runTest(retries = 3) {
            if (retryCount++ < 3) fail("This test should be retried")
        }
    }

    @Test
    fun `runTest - retry should work with collected exceptions`(): TestResult {
        var retryCount = 0
        return runTest(retries = 1) {
            if (retryCount++ < 1) collectUnhandledException(Exception("This test should be retried"))
        }
    }
}
