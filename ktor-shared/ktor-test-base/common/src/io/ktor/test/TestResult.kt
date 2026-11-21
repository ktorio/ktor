/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import kotlinx.coroutines.test.TestResult
import kotlin.time.Duration

internal expect val DummyTestResult: TestResult

/**
 * Executes the provided [block] after the test.
 * It is the only way to execute something **after** test on JS/WasmJS targets.
 *
 * @see TestResult
 */
expect inline fun TestResult.andThen(crossinline block: () -> Any): TestResult

/**
 * Fails the test if it doesn't complete within [timeout] plus a grace period, measured by a platform timer.
 * A backstop for the case when `runTest` can't enforce its own timeout. Only implemented on JS and WasmJS.
 */
internal expect fun TestResult.withWatchdog(timeout: Duration): TestResult

internal expect inline fun testWithRecover(noinline recover: (Throwable) -> Unit, test: () -> TestResult): TestResult

internal expect inline fun <T> runTestForEach(items: Iterable<T>, crossinline test: (T) -> TestResult): TestResult

/**
 * Executes a test function with retry capabilities.
 *
 * ```
 * retryTest(retires = 2) { retry ->
 *     runTest {
 *         println("This test passes only on second retry. Current retry is $retry")
 *         assertEquals(2, retry)
 *     }
 * }
 * ```
 *
 * @param retries The number of retries to attempt after an initial failure. Must be a non-negative integer.
 * @param test A test to execute, which accepts the current retry attempt (starting at 0) as an argument.
 * @return A [TestResult] representing the outcome of the test after all attempts.
 */
expect inline fun retryTest(retries: Int, crossinline test: (Int) -> TestResult): TestResult
