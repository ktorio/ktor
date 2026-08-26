/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Thrown by [assertEventually] when [assertEventually]'s condition doesn't hold in time.
 *
 * Deliberately **not** an [AssertionError]: [DeterministicFailureGuard] classifies those as
 * deterministic and stops the retry loop, which is right for an assertion the test body evaluated
 * and wrong here. Running out of time waiting for a condition is the transient, load-dependent
 * failure that retrying exists for, so this stays an ordinary exception and remains retryable.
 *
 * It is also not a `CancellationException`, which the coroutine machinery would treat as normal
 * cancellation and swallow instead of failing the test.
 */
class EventuallyTimeoutException(message: String) : Exception(message)

/**
 * Waits until [condition] holds, polling it every [interval], and fails if it doesn't hold within
 * [timeout].
 *
 * @param description What is being waited for, phrased to read after "Timed out waiting for".
 * @param timeout Upper bound on the total wait, honored even if [interval] is larger.
 * @param interval How often [condition] is evaluated.
 * @param condition The awaited condition. Evaluated at least once, even with a zero [timeout].
 * @throws EventuallyTimeoutException if [condition] doesn't hold within [timeout]. Retryable on
 *  purpose — see the exception's own documentation.
 */
suspend fun assertEventually(
    description: String,
    timeout: Duration = DEFAULT_EVENTUALLY_TIMEOUT,
    interval: Duration = (timeout / 20).coerceIn(1.milliseconds, 100.milliseconds),
    condition: suspend () -> Boolean,
) {
    val deadline = TimeSource.Monotonic.markNow() + timeout
    while (true) {
        if (condition()) return
        val remaining = -deadline.elapsedNow()
        if (remaining <= Duration.ZERO) {
            throw EventuallyTimeoutException("Timed out after $timeout waiting for $description")
        }
        // Never sleep past the deadline: `timeout` is documented as the upper bound on the wait, so
        // an interval larger than what is left would overshoot it (`interval = 1s` with a 100ms
        // timeout would fail after a second).
        realTimeDelay(minOf(interval, remaining))
    }
}

/** Delays in real time, so the wait isn't skipped by the virtual clock of `runTest`. */
private suspend fun realTimeDelay(duration: Duration) {
    withContext(Dispatchers.Default) { delay(duration) }
}
