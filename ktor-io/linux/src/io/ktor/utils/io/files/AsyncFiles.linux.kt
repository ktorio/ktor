/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.files

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.RawSourceChannel
import io.ktor.utils.io.ioDispatcher
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.posix.getenv

private val driverOrNull: IoUringDriver? by lazy {
    if (!ioUringEnabled()) {
        null
    } else {
        runCatching {
            val probe = CInteropIoUringDriver.createRing(8)
            CInteropIoUringDriver.destroyRing(probe)
            CInteropIoUringDriver
        }.getOrNull()
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ioUringDriverOrNull(): IoUringDriver? = driverOrNull

internal actual fun FileSystem.supportsAsyncFileIo(): Boolean =
    this === SystemFileSystem && IoUringWorkerPool.isAvailable

@OptIn(InternalAPI::class)
internal actual fun FileSystem.byteReadChannelNative(path: Path): ByteReadChannel {
    val ring = IoUringWorkerPool.nextOrNull()
    return ring?.let { IoUringByteReadChannel(path, it) }
        ?: RawSourceChannel(source(path), ioDispatcher())
}

@OptIn(ExperimentalForeignApi::class)
private fun ioUringEnabled(): Boolean {
    val configuredValue = getenv("io.ktor.io.uring.enabled")?.toKString()
        ?: getenv("IO_KTOR_IO_URING_ENABLED")?.toKString()
    return !configuredValue.equals("false", ignoreCase = true)
}
