/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.test.base

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

expect abstract class BaseTest() {
    open val timeout: Duration

    open fun beforeTest()
    open fun afterTest()

    fun collectUnhandledException(error: Throwable) // TODO: better name?
    fun runTest(
        timeout: Duration = 60.seconds,
        retries: Int = DEFAULT_RETRIES,
        block: suspend CoroutineScope.() -> Unit
    ): TestResult
}

/**
 * Defaults to `1` on all platforms except for JVM.
 * On JVM retries are disabled as we use test-retry Gradle plugin instead.
 */
internal expect val DEFAULT_RETRIES: Int
