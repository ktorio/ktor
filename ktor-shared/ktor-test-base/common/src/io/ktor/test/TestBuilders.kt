/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlinx.coroutines.test.runTest as runTestImpl

/**
 * An override of [kotlinx.coroutines.test.runTest] using our [DEFAULT_TEST_TIMEOUT].
 * This function should be used to align timeout across all tests.
 */
fun runTest(
    context: CoroutineContext = EmptyCoroutineContext,
    timeout: Duration = DEFAULT_TEST_TIMEOUT,
    testBody: suspend TestScope.() -> Unit
): TestResult = runTestImpl(context, timeout, testBody)

/**
 * An override of [kotlinx.coroutines.test.runTest] using our [DEFAULT_TEST_TIMEOUT].
 * This function should be used to align timeout across all tests.
 */
fun TestScope.runTest(
    timeout: Duration = DEFAULT_TEST_TIMEOUT,
    testBody: suspend TestScope.() -> Unit
): TestResult = this.runTestImpl(timeout, testBody)
