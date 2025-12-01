/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.test.dispatcher

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Test runner for common suspend tests.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.test.dispatcher.testSuspend)
 */
@Deprecated(
    "testSuspend is deprecated, use runTest function instead",
    replaceWith = ReplaceWith(
        "runTest { block() }",
        "kotlinx.coroutines.test.runTest",
    ),
    level = DeprecationLevel.WARNING
)
public expect fun testSuspend(
    context: CoroutineContext = EmptyCoroutineContext,
    timeoutMillis: Long = 60L * 1000L,
    block: suspend CoroutineScope.() -> Unit
): TestResult

// kotlinx.coroutines.test.runTest uses `virtual` time by default, which is not what we want sometimes
// probably in almost all places it should be fine to use virtual time
// and change dispatcher in those places where needed
public fun runTestWithRealTime(
    context: CoroutineContext = EmptyCoroutineContext,
    timeout: Duration = 60.seconds,
    testBody: suspend CoroutineScope.() -> Unit
): TestResult {
    val context = if (context[CoroutineDispatcher] is TestDispatcher) context else EmptyCoroutineContext
    return runTest(context, timeout) {
        withContext(Dispatchers.Default.limitedParallelism(1), testBody)
    }
}
