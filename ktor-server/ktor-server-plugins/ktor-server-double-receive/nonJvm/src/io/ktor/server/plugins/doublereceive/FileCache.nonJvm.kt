/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.doublereceive

import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

internal actual class FileCache actual constructor(
    bufferSize: Int
) : DoubleReceiveCache {
    init {
        error("File cache is not supported on nix")
    }

    actual override suspend fun reader(): ByteReadChannel {
        error("File cache is not supported on nix")
    }

    actual override fun dispose() {
        error("File cache is not supported on nix")
    }

    actual override fun CoroutineScope.launchPump(channel: ByteReadChannel): Job {
        error("File cache is not supported on nix")
    }
}
