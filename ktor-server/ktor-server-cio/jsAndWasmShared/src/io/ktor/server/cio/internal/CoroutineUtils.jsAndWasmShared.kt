/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.cio.internal

import kotlinx.coroutines.*

internal actual val Dispatchers.IOBridge: CoroutineDispatcher
    get() = Default

internal actual fun <T> runBlockingBridge(block: suspend CoroutineScope.() -> T): T =
    error("runBlocking is not supported on JS and WASM")
