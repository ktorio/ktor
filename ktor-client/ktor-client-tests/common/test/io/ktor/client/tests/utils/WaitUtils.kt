/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import kotlinx.coroutines.delay
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

suspend fun waitForCondition(
    description: String,
    timeout: Duration,
    delay: Duration = (timeout / 10).coerceAtMost(100.milliseconds),
    condition: () -> Boolean,
) {
    val timeMark = TimeSource.Monotonic.markNow() + timeout
    while (timeMark.hasNotPassedNow()) {
        if (condition()) {
            return
        }
        delay(delay)
    }
    assertTrue(condition(), "Timed out after $timeout waiting for $description")
}
