/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Represents a test case with associated data and retry attempt information.
 *
 * @property data The input data for the test case.
 * @property retry The current retry attempt number for this test case. `0` means the initial test run before retries.
 */
data class TestCase<T>(val data: T, val retry: Int)

/**
 * Represents a failure that occurred during the execution of a test case, along with the associated test data.
 *
 * @param cause The exception that caused the test to fail.
 * @param data The data associated with the test case that failed.
 */
data class TestFailure<T>(val cause: Throwable, val data: T)

/**
 * Executes multiple test cases with retry capabilities and timeout control.
 * Timeout is independent for each attempt in each test case.
 *
 * Example usage:
 * ```
 * @Test
 * fun dataDrivenTest() = runTestWithData(
 *     testCases = listOf("test1", "test2"),
 *     timeout = 10.seconds,
 *     retries = 2,
 *     afterEach = { (data, retry), error ->
 *         println("Test case $data attempt $retry ${if (error != null) "failed" else "succeeded"}")
 *     },
 *     afterAll = { println("All tests completed") },
 *     handleFailures = { failures ->
 *         failures.forEach { (cause, data) -> println("Test $data failed: $cause") }
 *     },
 * ) { (data, retry) ->
 *     // test implementation
 * }
 * ```
 *
 * @param testCases Data to be used in tests. Each element represents a separate test case.
 * @param context Optional coroutine context for test execution. Defaults to [EmptyCoroutineContext].
 * @param timeout Maximum duration allowed for each test attempt. Defaults to 1 minute.
 * @param retries Number of additional attempts after initial failure (`0` means no retries).
 * @param afterEach Called after each test case attempt, regardless of success or failure.
 *  Receives the test case and error (if any occurred).
 * @param handleFailures Called after all tests finished if any failures occurred.
 *  Receives a list of all failed test cases with their last failure cause.
 *  By default, throws [AssertionError] with an aggregated error message.
 * @param afterAll Runs after all tests finished, but before [handleFailures].
 * @param test Test execution block. Receives [TestCase] containing both test data and current retry number.
 *
 * @return [TestResult] representing the completion of all test cases.
 */
fun <T> runTestWithData(
    testCases: Iterable<T>,
    context: CoroutineContext = EmptyCoroutineContext,
    timeout: Duration = 1.minutes,
    retries: Int = 1,
    afterEach: (TestCase<T>, Throwable?) -> Unit = { _, _ -> },
    handleFailures: (List<TestFailure<T>>) -> Unit = ::defaultAggregatedError,
    afterAll: () -> Unit = {},
    test: suspend TestScope.(TestCase<T>) -> Unit,
): TestResult {
    check(retries >= 0) { "Retries count shouldn't be negative but it is $retries" }

    val failures = mutableListOf<TestFailure<T>>()
    return runTestForEach(testCases) { data ->
        retryTest(retries) { retry ->
            val testCase = TestCase(data, retry)

            testWithRecover(
                recover = { cause ->
                    afterEach(testCase, cause)
                    // Don't rethrow the exception on the last retry,
                    // save it instead to pass in handleFailures later
                    if (retry == retries) failures += TestFailure(cause, data) else throw cause
                }
            ) {
                runTest(context, timeout = timeout) {
                    test(testCase)
                    afterEach(testCase, null)
                }
            }
        }
    }.andThen {
        afterAll()
        if (failures.isNotEmpty()) handleFailures(failures)
    }
}

private fun defaultAggregatedError(failures: List<TestFailure<*>>): Nothing {
    val message = buildString {
        appendLine("Test execution failed:")
        for ((cause, data) in failures) {
            appendLine("  Test case '$data' failed:")
            appendLine(cause.stackTraceToString().prependIndent("    "))
        }
    }
    throw AssertionError(message)
}
