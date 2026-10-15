/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * A single attempt of one test case run by [runTestWithData].
 *
 * @property data The test case's input data, as supplied via [runTestWithData]'s `testCases`.
 * @property retry Which attempt this is: `0` for the first attempt, incrementing on each retry.
 */
data class TestCase<T>(val data: T, val retry: Int)

/**
 * A [TestCase] attempt that failed, run by [runTestWithData].
 *
 * @property testCase The test case and retry number this result belongs to.
 * @property cause The throwable that failed the attempt.
 * @property duration How long the attempt took before it failed.
 */
data class TestFailure<T>(
    override val testCase: TestCase<T>,
    val cause: Throwable,
    override val duration: Duration,
) : TestExecutionResult<T>

/**
 * A [TestCase] attempt that completed without throwing, run by [runTestWithData].
 *
 * @property testCase The test case and retry number this result belongs to.
 * @property duration How long the attempt took to complete.
 */
data class TestSuccess<T>(
    override val testCase: TestCase<T>,
    override val duration: Duration,
) : TestExecutionResult<T>

/**
 * The outcome of a single [TestCase] attempt run by [runTestWithData]: either a [TestSuccess] or a
 * [TestFailure].
 *
 * @property testCase The test case and retry number this result belongs to.
 * @property duration How long the attempt took.
 */
sealed interface TestExecutionResult<T> {
    val testCase: TestCase<T>
    val duration: Duration
}

/**
 * Executes multiple test cases with retry capabilities and timeout control.
 * Timeout is independent for each attempt in each test case.
 *
 * @param testCases Data to be used in tests. Each element represents a separate test case.
 * @param context Optional coroutine context for test execution. Defaults to [EmptyCoroutineContext].
 * @param timeout Maximum duration allowed for each test attempt. Defaults to 1 minute.
 * @param retries Number of additional attempts after initial failure (`0` means no retries).
 *  Only transient failures are retried: an assertion failure thrown by the test body is
 *  deterministic, so it is reported immediately instead of being retried and masked.
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
@OptIn(InternalAPI::class)
fun <T> runTestWithData(
    testCases: Iterable<T>,
    context: CoroutineContext = EmptyCoroutineContext,
    timeout: Duration = DEFAULT_TEST_TIMEOUT,
    retries: Int = DEFAULT_RETRIES,
    afterEach: (TestExecutionResult<T>) -> Unit = {},
    handleFailures: (List<TestFailure<T>>) -> Unit = ::defaultAggregatedError,
    afterAll: () -> Unit = {},
    test: suspend TestScope.(TestCase<T>) -> Unit,
): TestResult {
    val timeSource = TimeSource.Monotonic
    var start: TimeMark? = null

    val failures = mutableListOf<TestFailure<T>>()
    return runTestForEach(testCases) { data ->
        val guard = DeterministicFailureGuard()

        retryTest(retries) { retry ->
            val testCase = TestCase(data, retry)

            testWithRecover(
                recover = { cause ->
                    val failure = TestFailure(testCase, cause, start?.elapsedNow() ?: Duration.ZERO)
                    afterEach(failure)
                    if (retry == retries || guard.hasFailure) failures += failure else throw cause
                }
            ) {
                runTest(context, timeout = timeout) {
                    val mark = timeSource.markNow()
                    start = mark
                    try {
                        test(testCase)
                    } catch (cause: Throwable) {
                        guard.record(cause)
                        throw cause
                    }
                    afterEach(TestSuccess(testCase, mark.elapsedNow()))
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
