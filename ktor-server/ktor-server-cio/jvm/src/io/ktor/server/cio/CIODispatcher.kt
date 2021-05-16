/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.cio

import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.scheduling.*

@OptIn(InternalCoroutinesApi::class)
internal actual class CIODispatcher actual constructor(
    configuration: CIOApplicationEngine.Configuration,
    connectors: Int
) {
    private val corePoolSize: Int = maxOf(
        configuration.connectionGroupSize + configuration.workerGroupSize,
        connectors + 1 // number of selectors + 1
    )
    private val _engineDispatcher = ExperimentalCoroutineDispatcher(corePoolSize)
    private val _userDispatcher = DispatcherWithShutdown(_engineDispatcher.blocking(configuration.callGroupSize))
    actual val engineDispatcher: CoroutineDispatcher get() = _engineDispatcher
    actual val userDispatcher: CoroutineDispatcher get() = _userDispatcher

    actual fun close() {
        _engineDispatcher.close()
    }

    actual fun prepareShutdown() {
        _userDispatcher.prepareShutdown()
    }

    actual fun completeShutdown() {
        _userDispatcher.completeShutdown()
    }
}
