/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import kotlinx.coroutines.test.TestResult

internal actual inline fun testWithRecover(
    noinline recover: (Throwable) -> Unit,
    test: () -> TestResult
): TestResult = test().catch(recover)

internal actual inline fun <T> runTestForEach(items: Iterable<T>, crossinline test: (T) -> TestResult): TestResult =
    items.fold(DummyTestResult) { acc, item -> acc.andThen { test(item) } }

actual inline fun retryTest(
    retries: Int,
    crossinline shouldRetry: (Throwable) -> Boolean,
    crossinline test: (Int) -> TestResult,
): TestResult {
    check(retries >= 0) { "Retries count shouldn't be negative but it is $retries" }
    // Rethrowing rejects the promise, which the remaining `catch` links pass along untouched — the
    // web equivalent of breaking out of the loop.
    return (1..retries).fold(test(0)) { acc, retry ->
        acc.catch { cause -> if (shouldRetry(cause)) test(retry) else throw cause }
    }
}

@PublishedApi
internal expect fun TestResult.catch(action: (Throwable) -> Any): TestResult
