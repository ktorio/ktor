/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.doublereceive

import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

internal expect class FileCache(
    bufferSize: Int = 4096
) : DoubleReceiveCache {
    override fun CoroutineScope.launchPump(channel: ByteReadChannel): Job

    override suspend fun reader(): ByteReadChannel

    override fun dispose()
}
