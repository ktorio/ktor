/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Represents a test case with associated data and retry attempt information.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.test.TestCase)
 *
 * @property data The input data for the test case.
 * @property retry The current retry attempt number for this test case. `0` means the initial test run before retries.
 */
data class TestCase<T>(val data: T, val retry: Int)

/**
 * Represents a failed test execution with the [cause] of failure.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.test.TestFailure)
 */
data class TestFailure<T>(
    override val testCase: TestCase<T>,
    val cause: Throwable,
    override val duration: Duration,
) : TestExecutionResult<T>

/**
 * Represents a successful test execution.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.test.TestSuccess)
 */
data class TestSuccess<T>(
    override val testCase: TestCase<T>,
    override val duration: Duration,
) : TestExecutionResult<T>

/**
 * The result of a test execution. Can be [TestFailure] or [TestSuccess].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.test.TestExecutionResult)
 *
 * @property testCase The test case associated with this execution.
 * @property duration The duration of the test execution.
 */
sealed interface TestExecutionResult<T> {
    val testCase: TestCase<T>
    val duration: Duration
}

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
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.test.runTestWithData)
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
    afterEach: (TestExecutionResult<T>) -> Unit = {},
    handleFailures: (List<TestFailure<T>>) -> Unit = ::defaultAggregatedError,
    afterAll: () -> Unit = {},
    test: suspend TestScope.(TestCase<T>) -> Unit,
): TestResult {
    val timeSource = TimeSource.Monotonic
    var start: TimeMark? = null

    val failures = mutableListOf<TestFailure<T>>()
    return runTestForEach(testCases) { data ->
        retryTest(retries) { retry ->
            val testCase = TestCase(data, retry)

            testWithRecover(
                recover = { cause ->
                    val failure = TestFailure(testCase, cause, start?.elapsedNow() ?: Duration.ZERO)
                    afterEach(failure)
                    // Don't rethrow the exception on the last retry,
                    // save it instead to pass in handleFailures later
                    if (retry == retries) failures += failure else throw cause
                }
            ) {
                runTest(context, timeout = timeout) {
                    start = timeSource.markNow()
                    test(testCase)
                    afterEach(TestSuccess(testCase, start?.elapsedNow() ?: Duration.ZERO))
                }
            }
        }
    }.andThen {
        afterAll()
        if (failures.isNotEmpty()) handleFailures(failures)
    }
}

private fun defaultAggregatedError(failures: List<TestFailure<*>>): Nothing {
    if (failures.size == 1) throw failures.first().cause

    val message = buildString {
        appendLine("Test execution failed:")
        for ((testCase, cause) in failures) {
            appendLine("  Test case '${testCase.data}' failed:")
            appendLine(cause.stackTraceToString().prependIndent("    "))
        }
    }
    throw AssertionError(message)
}
