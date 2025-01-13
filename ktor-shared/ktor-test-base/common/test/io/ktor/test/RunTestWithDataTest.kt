/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RunTestWithDataTest {

    //region Test Cases
    @Test
    fun testBasicSuccess() = runTestWithData(
        singleTestCase,
        test = { /* simple successful operation */ },
    )

    @Test
    fun testMultipleTestCases(): TestResult {
        val executedItems = mutableSetOf<Int>()
        return runTestWithData(
            testCases = 1..3,
            test = { (item, _) -> executedItems.add(item) },
            afterAll = { assertEquals(setOf(1, 2, 3), executedItems) },
        )
    }

    @Test
    fun testEmptyTestCases() = runTestWithData(
        testCases = emptyList<Int>(),
        test = { fail("Should not be called") },
        afterEach = { fail("Should not be called") },
        handleFailures = { fail("Should not be called") },
    )
    //endregion

    //region Retries
    @Test
    fun testMultipleRetriesUntilSuccess(): TestResult {
        var successfulRetry = 0
        return runTestWithData(
            singleTestCase,
            retries = 3,
            test = { (_, retry) ->
                if (retry < 3) fail("Retry #$retry")
                successfulRetry = retry
            },
            afterAll = { assertEquals(3, successfulRetry) },
        )
    }

    @Test
    fun testZeroRetries() = runTestWithData(
        singleTestCase,
        retries = 0,
        test = { fail("Should fail") },
        handleFailures = { assertEquals(1, it.size) }
    )

    @Test
    fun testExhaustedRetries() = runTestWithData(
        singleTestCase,
        retries = 2,
        test = { fail("Always fail") },
        handleFailures = { assertEquals(1, it.size) }
    )
    //endregion

    //region Timeout
    @Test
    fun testFailByTimeout() = runTestWithData(
        singleTestCase,
        timeout = 10.milliseconds,
        test = { realTimeDelay(1.seconds) },
        handleFailures = { assertEquals(1, it.size) },
    )

    @Test
    fun testRetryAfterTimeout(): TestResult {
        var successfulRetry = 0
        return runTestWithData(
            singleTestCase,
            retries = 2,
            timeout = 15.milliseconds,
            test = { (_, retry) ->
                if (retry < 2) realTimeDelay(1.seconds)
                successfulRetry = retry
            },
            afterAll = { assertEquals(2, successfulRetry) },
        )
    }

    @Test
    fun testRetriesHaveIndependentTimeout() = runTestWithData(
        singleTestCase,
        retries = 1,
        timeout = 50.milliseconds,
        test = { (_, retry) ->
            realTimeDelay(30.milliseconds)
            if (retry == 0) fail("Try again, please")
        },
    )

    @Test
    fun testDifferentItemsHaveIndependentTimeout() = runTestWithData(
        testCases = 1..2,
        timeout = 50.milliseconds,
        test = { realTimeDelay(30.milliseconds) },
    )

    @Test
    fun testSuccessAfterTimeoutItem(): TestResult {
        var successfulItem = 0
        return runTestWithData(
            testCases = 1..2,
            retries = 0,
            timeout = 15.milliseconds,
            test = { (item, _) ->
                if (item == 1) realTimeDelay(1.seconds)
                successfulItem = item
            },
            handleFailures = {
                assertEquals(1, it.size)
                assertEquals(1, it.single().testCase.data)
                assertEquals(2, successfulItem)
            },
        )
    }
    //endregion

    @Test
    fun testExecutionOrderPreserved(): TestResult {
        val executionLog = mutableListOf<String>()
        return runTestWithData(
            testCases = 1..3,
            retries = 1,
            timeout = 15.milliseconds,
            afterEach = { result ->
                val status = if (result is TestFailure) "failed: ${result.cause.message}" else "succeeded"
                executionLog.add("Test ${result.testCase.data}: attempt ${result.testCase.retry} $status")
            },
            test = { (id, retry) ->
                println("${id}x$retry")
                when (id) {
                    1 -> if (retry == 0) fail("First attempt failed")
                    2 -> if (retry == 0) realTimeDelay(1.seconds)
                }
            },
            afterAll = {
                assertEquals(
                    listOf(
                        "Test 1: attempt 0 failed: First attempt failed",
                        "Test 1: attempt 1 succeeded",
                        "Test 2: attempt 0 failed: After waiting for 15ms, the test body did not run to completion",
                        "Test 2: attempt 1 succeeded",
                        "Test 3: attempt 0 succeeded",
                    ),
                    executionLog,
                )
            }
        )
    }

    @Test
    fun testContextPropagation() = runTestWithData(
        singleTestCase,
        context = CoroutineName("TestContext"),
        test = { assertEquals("TestContext", currentCoroutineContext()[CoroutineName]?.name) },
    )
}

private val singleTestCase = listOf(1)

private suspend fun realTimeDelay(duration: Duration) {
    withContext(Dispatchers.Default.limitedParallelism(1)) { delay(duration) }
}
