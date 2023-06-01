// ktlint-disable filename
/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit4.*
import org.junit.*
import org.junit.rules.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

public actual abstract class BaseTest actual constructor() {
    public actual open val timeout: Duration = 10.seconds

    @get:Rule
    internal val errorCollector = ErrorCollector() // TODO: for some reason, it tracks only one error

    @get:Rule
    internal val coroutinesTimeout
        get() = CoroutinesTimeout.seconds(timeout.inWholeSeconds, true)

    @get:Rule
    internal val testName: TestName = TestName()

    public actual fun collectUnhandledException(error: Throwable) {
        errorCollector.addError(error)
    }

    public actual fun runTest(block: suspend CoroutineScope.() -> Unit) {
        runBlocking(CoroutineName("test-${testName.methodName}"), block)
    }
}
