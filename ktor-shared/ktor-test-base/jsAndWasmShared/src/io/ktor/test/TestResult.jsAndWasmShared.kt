/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import kotlinx.coroutines.test.TestResult

internal actual inline fun testWithRecover(
    noinline recover: (Throwable) -> Unit,
    test: () -> TestResult
): TestResult = test().catch(recover)

internal actual inline fun <T> runTestForEach(items: Iterable<T>, crossinline test: (T) -> TestResult): TestResult =
    items.fold(DummyTestResult) { acc, item -> acc.andThen { test(item) } }

internal actual inline fun retryTest(retries: Int, crossinline test: (Int) -> TestResult): TestResult =
    (1..retries).fold(test(0)) { acc, retry -> acc.catch { test(retry) } }

internal expect fun TestResult.catch(action: (Throwable) -> Any): TestResult
