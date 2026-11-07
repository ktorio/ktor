/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.files

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ExperimentalKtorApi
import io.ktor.utils.io.RawSourceChannel
import io.ktor.utils.io.ioDispatcher
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path

/**
 * Opens [path] in this file system and returns a channel that reads its contents.
 *
 * @param path the file to read.
 * @return a channel that provides the file contents.
 */
@ExperimentalKtorApi
public fun FileSystem.byteReadChannel(path: Path): ByteReadChannel =
    if (supportsAsyncFileIo()) {
        byteReadChannelNative(path)
    } else {
        RawSourceChannel(source(path), ioDispatcher())
    }

internal expect fun FileSystem.supportsAsyncFileIo(): Boolean

internal expect fun FileSystem.byteReadChannelNative(path: Path): ByteReadChannel
