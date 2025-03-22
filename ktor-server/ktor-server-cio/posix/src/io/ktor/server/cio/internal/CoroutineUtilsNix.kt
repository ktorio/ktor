/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.cio.internal

import kotlinx.coroutines.*

internal actual val Dispatchers.IOBridge: CoroutineDispatcher
    get() = IO

internal actual fun <T> runBlockingBridge(block: suspend CoroutineScope.() -> T): T = runBlocking(block = block)
