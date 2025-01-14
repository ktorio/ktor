/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.test.base

import io.ktor.test.*
import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import io.ktor.utils.io.locks.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(InternalAPI::class)
actual abstract class BaseTest actual constructor() {
    actual open val timeout: Duration = 10.seconds

    private val errors = mutableListOf<Throwable>()

    private val errorsLock = SynchronizedObject()

    actual fun collectUnhandledException(error: Throwable) {
        synchronized(errorsLock) {
            errors.add(error)
        }
    }

    actual open fun beforeTest() {
    }

    actual open fun afterTest() {
        val errors = synchronized(errorsLock) { errors.toList() }
        this.errors.clear()

        if (errors.isEmpty()) return

        val error = UnhandledErrorsException(
            "There were ${errors.size} unhandled errors during running test (suppressed)"
        )

        errors.forEach {
            error.addSuppressed(it)
        }
        error.printStackTrace()
        throw error // suppressed exceptions print wrong in idea
    }

    actual fun runTest(
        timeout: Duration,
        retries: Int,
        block: suspend CoroutineScope.() -> Unit
    ): TestResult = retryTest(retries) { retry ->
        runTestWithRealTime(timeout = timeout) {
            if (retry > 0) println("[Retry $retry/$retries]")
            beforeTest()
            try {
                block()
            } finally {
                afterTest()
            }
        }
    }
}

private class UnhandledErrorsException(override val message: String) : Exception()
