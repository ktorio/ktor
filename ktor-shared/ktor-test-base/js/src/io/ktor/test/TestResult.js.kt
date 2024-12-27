/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import kotlinx.coroutines.test.TestResult
import kotlin.js.Promise

internal actual val DummyTestResult: TestResult = Promise.resolve(Unit).asTestResult()

actual inline fun TestResult.andThen(crossinline block: () -> Any): TestResult =
    this.asPromise().then { block() }.asTestResult()

internal actual fun TestResult.catch(action: (Throwable) -> Any): TestResult =
    this.asPromise().catch(action).asTestResult()

@Suppress("CAST_NEVER_SUCCEEDS")
@PublishedApi
internal fun TestResult.asPromise(): Promise<Unit> = this as Promise<Unit>

@Suppress("CAST_NEVER_SUCCEEDS")
@PublishedApi
internal fun Promise<*>.asTestResult(): TestResult = this as TestResult
