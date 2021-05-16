/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.cio

import kotlinx.coroutines.*

@OptIn(ExperimentalCoroutinesApi::class)
internal actual class CIODispatcher actual constructor(
    configuration: CIOApplicationEngine.Configuration,
    connectors: Int
) {
    //TODO replace with newSingleThreadContext("engine") ?
    actual val engineDispatcher: CoroutineDispatcher get() = Dispatchers.Unconfined
    actual val userDispatcher: CoroutineDispatcher get() = Dispatchers.Unconfined

    actual fun close() {
    }

    actual fun prepareShutdown() {
    }

    actual fun completeShutdown() {
    }
}
