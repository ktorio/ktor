/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import kotlinx.coroutines.test.TestResult
import kotlin.js.Promise

internal actual val DummyTestResult: TestResult = Promise.resolve(Unit.asJsAny()).asTestResult()

actual inline fun TestResult.andThen(crossinline block: () -> Any): TestResult =
    asPromise().then { block().asJsAny() }.asTestResult()

internal actual inline fun TestResult.catch(crossinline action: (Throwable) -> Any): TestResult =
    asPromise().catch { action(it.toThrowableOrNull()!!).asJsAny() }.asTestResult()

@Suppress("CAST_NEVER_SUCCEEDS")
@PublishedApi
internal fun TestResult.asPromise(): Promise<JsAny> = this as Promise<JsAny>

@Suppress("CAST_NEVER_SUCCEEDS")
@PublishedApi
internal fun Promise<*>.asTestResult(): TestResult = this as TestResult

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
@PublishedApi
internal fun Any.asJsAny() = this as JsAny
