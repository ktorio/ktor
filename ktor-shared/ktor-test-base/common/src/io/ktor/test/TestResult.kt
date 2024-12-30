/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import kotlinx.coroutines.test.TestResult

internal expect val DummyTestResult: TestResult

/**
 * Executes the provided [block] after the test.
 * It is the only way to execute something **after** test on JS/WasmJS targets.
 * @see TestResult
 */
expect inline fun TestResult.andThen(crossinline block: () -> Any): TestResult

internal expect inline fun testWithRecover(noinline recover: (Throwable) -> Unit, test: () -> TestResult): TestResult

internal expect inline fun <T> runTestForEach(items: Iterable<T>, crossinline test: (T) -> TestResult): TestResult
internal expect inline fun retryTest(retries: Int, crossinline test: (Int) -> TestResult): TestResult
