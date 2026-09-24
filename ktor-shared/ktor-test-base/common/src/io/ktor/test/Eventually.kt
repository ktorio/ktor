/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Waits until [condition] holds, polling it every [interval], and fails if it doesn't hold within
 * [timeout].
 *
 * @param description What is being waited for; attached as the coroutine name so it reads inside the
 *  resulting [TimeoutCancellationException]'s message (e.g. `Coroutine "description" timed out...`).
 * @param timeout Upper bound on the total wait, honored even if [interval] is larger.
 * @param interval How often [condition] is evaluated.
 * @param condition The awaited condition. Evaluated at least once, even with a zero [timeout]. Not
 *  `suspend`: a suspending condition could itself hang indefinitely with nothing to bound it.
 * @throws TimeoutCancellationException if [condition] doesn't hold within [timeout]. Left as the
 *  native coroutines exception, rather than wrapped in a custom type, so `runTest` and
 *  `kotlinx-coroutines-debug` recognize it and attach a coroutine dump to the failure. It still stays
 *  retryable: [DeterministicFailureGuard] only treats [AssertionError] as deterministic, and a plain
 *  `CancellationException` thrown this way — by normal suspend-call propagation rather than by a
 *  discarded child coroutine completing — fails the enclosing test instead of being swallowed.
 */
suspend fun assertEventually(
    description: String,
    timeout: Duration = DEFAULT_EVENTUALLY_TIMEOUT,
    interval: Duration = (timeout / 20).coerceIn(1.milliseconds, 100.milliseconds),
    condition: () -> Boolean,
) {
    if (condition()) return
    // Real dispatcher for both the timeout's own timer and the poll delay below: otherwise, under
    // `runTest`, both would run against the virtual clock, which the test dispatcher can advance
    // instantly instead of actually waiting.
    withContext(Dispatchers.Default + CoroutineName(description)) {
        withTimeout(timeout) {
            while (!condition()) {
                delay(interval)
            }
        }
    }
}
