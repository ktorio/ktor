/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import kotlinx.coroutines.*
import kotlin.coroutines.*

internal actual fun <T> blockingCall(context: CoroutineContext, block: suspend CoroutineScope.() -> T): T {
    return runBlocking(context, block = block)
}

internal actual val ioDispatcher: CoroutineDispatcher
    get() = Dispatchers.Unconfined
