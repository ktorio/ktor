/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.files

import io.ktor.utils.io.ByteReadChannel
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

internal actual fun FileSystem.supportsAsyncFileIo(): Boolean =
    this === SystemFileSystem && IoUringWorkerPool.isAvailable

internal actual fun FileSystem.byteReadChannelNative(path: Path): ByteReadChannel =
    IoUringByteReadChannel(path, IoUringWorkerPool.next())
