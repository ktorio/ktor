/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.test.base

import io.ktor.test.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestResult
import kotlin.time.Duration

expect abstract class BaseTest() {
    open val timeout: Duration

    open fun beforeTest()
    open fun afterTest()

    fun collectUnhandledException(error: Throwable) // TODO: better name?

    fun runTest(
        timeout: Duration = DEFAULT_TEST_TIMEOUT,
        retries: Int = DEFAULT_RETRIES,
        block: suspend CoroutineScope.() -> Unit
    ): TestResult
}

/**
 * The retry, guard and lifecycle scaffolding shared by the [BaseTest.runTest] actuals.
 *
 * Only the coroutine context differs between targets, so that part stays in the actual and arrives
 * here as [runAttempt] — the platform's `runTestWithRealTime` call. Everything else is identical:
 * an assertion failure thrown by [block] is recorded and ends the retry loop, so a deterministic
 * failure is reported by the attempt that produced it instead of being masked as flakiness.
 * `runTest`'s own timeout error is an `AssertionError` too, but stays retryable because it is thrown
 * around [block] rather than by it, so the guard never sees it.
 */
internal fun BaseTest.runTestAttempts(
    retries: Int,
    runAttempt: (block: suspend CoroutineScope.() -> Unit) -> TestResult,
    block: suspend CoroutineScope.() -> Unit,
): TestResult {
    val guard = DeterministicFailureGuard()
    return retryTest(retries, shouldRetry = { !guard.hasFailure }) { retry ->
        runAttempt {
            if (retry > 0) println("[Retry $retry/$retries]")
            beforeTest()
            try {
                block()
            } catch (cause: Throwable) {
                guard.record(cause)
                throw cause
            } finally {
                afterTest()
            }
        }
    }
}
