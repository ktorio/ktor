/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import kotlinx.coroutines.test.TestResult
import kotlin.js.Promise
import kotlin.time.Duration

internal actual val DummyTestResult: TestResult = Promise.resolve(Unit).asTestResult()

internal actual fun TestResult.withWatchdog(timeout: Duration): TestResult {
    val delayMillis = watchdogDelayMillis(timeout) ?: return this
    return raceWithWatchdog(asPromise(), delayMillis, watchdogMessage(timeout)).asTestResult()
}

@Suppress("UNUSED_PARAMETER")
private fun raceWithWatchdog(promise: Promise<Unit>, delayMillis: Int, message: String): Promise<Unit> =
    js(WATCHDOG_SCRIPT)

actual inline fun TestResult.andThen(crossinline block: () -> Any): TestResult =
    this.asPromise().then { block() }.asTestResult()

@PublishedApi
internal actual fun TestResult.catch(action: (Throwable) -> Any): TestResult =
    this.asPromise().catch(action).asTestResult()

@Suppress("CAST_NEVER_SUCCEEDS")
@PublishedApi
internal fun TestResult.asPromise(): Promise<Unit> = this as Promise<Unit>

@Suppress("CAST_NEVER_SUCCEEDS")
@PublishedApi
internal fun Promise<*>.asTestResult(): TestResult = this as TestResult
