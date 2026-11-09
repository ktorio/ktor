/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.files

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.RawSourceChannel
import io.ktor.utils.io.ioDispatcher
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

internal actual fun FileSystem.supportsAsyncFileIo(): Boolean =
    this === SystemFileSystem && IoUringWorkerPool.isAvailable

@OptIn(InternalAPI::class)
internal actual fun FileSystem.byteReadChannelNative(path: Path): ByteReadChannel {
    val ring = IoUringEventLoopRing.currentRing ?: IoUringWorkerPool.nextOrNull()
    return ring?.let { IoUringByteReadChannel(path, it) }
        ?: RawSourceChannel(source(path), ioDispatcher())
}
