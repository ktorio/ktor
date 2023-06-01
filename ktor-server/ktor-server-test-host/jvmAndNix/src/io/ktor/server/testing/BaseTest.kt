/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import kotlinx.coroutines.*
import kotlin.time.*

public expect abstract class BaseTest() {
    public open val timeout: Duration
    public fun collectUnhandledException(error: Throwable) // TODO: better name?
    public fun runTest(block: suspend CoroutineScope.() -> Unit)
}
