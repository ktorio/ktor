/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import kotlinx.coroutines.test.TestResult

@Suppress("CAST_NEVER_SUCCEEDS")
internal actual val DummyTestResult = Unit as TestResult

actual inline fun TestResult.andThen(block: () -> Any): TestResult = also { block() }

internal actual inline fun testWithRecover(recover: (Throwable) -> Unit, test: () -> TestResult): TestResult {
    try {
        test()
    } catch (cause: Throwable) {
        recover(cause)
    }
    return DummyTestResult
}

internal actual inline fun <T> runTestForEach(items: Iterable<T>, test: (T) -> TestResult): TestResult {
    for (item in items) test(item)
    return DummyTestResult
}

actual inline fun retryTest(retries: Int, test: (Int) -> TestResult): TestResult {
    check(retries >= 0) { "Retries count shouldn't be negative but it is $retries" }

    lateinit var lastCause: Throwable
    repeat(retries + 1) { attempt ->
        try {
            return test(attempt)
        } catch (cause: Throwable) {
            lastCause = cause
        }
    }
    throw lastCause
}
