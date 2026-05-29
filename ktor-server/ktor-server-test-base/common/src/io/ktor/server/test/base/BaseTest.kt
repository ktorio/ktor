/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.test.base

import io.ktor.test.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestResult
import kotlin.time.Duration

expect abstract class BaseTest() {
    open val timeout: Duration

    open fun beforeTest()
    open fun afterTest()

    fun collectUnhandledException(error: Throwable) // TODO: better name?

    fun runTest(
        timeout: Duration = DEFAULT_TEST_TIMEOUT,
        retries: Int = DEFAULT_RETRIES,
        block: suspend CoroutineScope.() -> Unit
    ): TestResult
}
