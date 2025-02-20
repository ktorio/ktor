/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import io.ktor.util.*
import kotlinx.coroutines.test.TestResult

internal expect val DummyTestResult: TestResult

/**
 * Executes the provided [block] after the test.
 * It is the only way to execute something **after** test on JS/WasmJS targets.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.test.andThen)
 *
 * @see TestResult
 */
expect inline fun TestResult.andThen(crossinline block: () -> Any): TestResult

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
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.test.retryTest)
 *
 * @param retries The number of retries to attempt after an initial failure. Must be a non-negative integer.
 * @param test A test to execute, which accepts the current retry attempt (starting at 0) as an argument.
 * @return A [TestResult] representing the outcome of the test after all attempts.
 */
expect inline fun retryTest(retries: Int, crossinline test: (Int) -> TestResult): TestResult

/**
 * Defaults to `1` on all platforms except for JVM.
 * On JVM retries are disabled as we use test-retry Gradle plugin instead.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.test.DEFAULT_RETRIES)
 */
val DEFAULT_RETRIES: Int = if (PlatformUtils.IS_JVM) 0 else 1
