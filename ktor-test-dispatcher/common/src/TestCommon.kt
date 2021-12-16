/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.test.dispatcher

import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.time.*

/**
 * Test runner for common suspend tests.
 */
public expect fun testSuspend(
    context: CoroutineContext = EmptyCoroutineContext,
    dispatchTimeoutMs: Long = 60_000L,
    testBody: suspend TestScope.() -> Unit
): TestResult

// see [kotlinx.coroutines.test.TestResult]
@Suppress("NO_ACTUAL_FOR_EXPECT")
public expect class TestResult

// coroutines-test:1.6.0 provides a testTimeSource: TimeSource. Until ktor-test-dispatcher is replaced with this
// dispatcher, the TimeSource is provided via testSuspend
// currently blocked by https://github.com/Kotlin/kotlinx.coroutines/issues/3218
@OptIn(ExperimentalTime::class)
public class TestScope internal constructor(
    scope: CoroutineScope,
    public val testTimeSource: TimeSource = GMTClock.System.toTimeSource()
) : CoroutineScope by scope

internal fun CoroutineScope.TestScope() = TestScope(this)

@ExperimentalTime
private fun GMTClock.toTimeSource(): TimeSource = object : TimeSource {
    override fun markNow(): TimeMark = GMTTimeMark(now(), this@toTimeSource)
}

@ExperimentalTime
private class GMTTimeMark(private val timestamp: GMTDate, private val clock: GMTClock) : TimeMark() {
    override fun elapsedNow() = clock.now() - timestamp
    override fun plus(duration: Duration) = GMTTimeMark(timestamp + duration, clock)
}
