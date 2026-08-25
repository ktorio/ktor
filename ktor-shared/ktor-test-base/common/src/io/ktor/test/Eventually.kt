/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Waits until [condition] holds, polling it every [interval], and fails if it doesn't hold within
 * [timeout].
 *
 * @param description What is being waited for, phrased to read after "Timed out waiting for".
 * @param timeout Upper bound on the total wait.
 * @param interval How often [condition] is evaluated.
 * @param condition The awaited condition. Evaluated at least once, even with a zero [timeout].
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
        if (deadline.hasPassedNow()) fail("Timed out after $timeout waiting for $description")
        realTimeDelay(interval)
    }
}

/** Delays in real time, so the wait isn't skipped by the virtual clock of `runTest`. */
private suspend fun realTimeDelay(duration: Duration) {
    withContext(Dispatchers.Default) { delay(duration) }
}
