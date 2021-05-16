/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.cio

import kotlinx.coroutines.*

internal expect class CIODispatcher(
    configuration: CIOApplicationEngine.Configuration,
    connectors: Int
) {
    val engineDispatcher: CoroutineDispatcher
    val userDispatcher: CoroutineDispatcher
    fun close()
    fun prepareShutdown()
    fun completeShutdown()
}
