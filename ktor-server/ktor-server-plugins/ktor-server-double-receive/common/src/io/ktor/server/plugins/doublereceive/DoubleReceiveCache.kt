/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.doublereceive

import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

internal interface DoubleReceiveCache {
    fun CoroutineScope.launchPump(channel: ByteReadChannel): Job

    suspend fun reader(): ByteReadChannel

    fun dispose()
}
