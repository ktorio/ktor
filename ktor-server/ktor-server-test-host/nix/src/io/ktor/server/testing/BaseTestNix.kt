/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.test.dispatcher.*
import kotlinx.atomicfu.locks.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

@Suppress("FunctionName")
public actual abstract class BaseTest actual constructor() {
    public actual open val timeout: Duration = 10.seconds

    private val errors = mutableListOf<Throwable>()
    private val errorsLock = SynchronizedObject()

    public actual fun collectUnhandledException(error: Throwable) {
        synchronized(errorsLock) {
            errors.add(error)
        }
    }

    @AfterTest
    public fun _verifyErrors() {
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

    public actual fun runTest(block: suspend CoroutineScope.() -> Unit) {
        testSuspend(timeoutMillis = timeout.inWholeMilliseconds, block = block)
    }
}

private class UnhandledErrorsException(override val message: String) : Exception()
private class TestTimeoutException(override val message: String, override val cause: Throwable?) : Exception()
