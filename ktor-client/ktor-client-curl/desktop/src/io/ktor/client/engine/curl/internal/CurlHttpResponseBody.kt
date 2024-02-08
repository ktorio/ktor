/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.internal

import io.ktor.utils.io.*
import kotlinx.atomicfu.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import libcurl.*
import platform.posix.*

internal class CurlHttpResponseBody(
    private val callContext: Job,
    private val onUnpause: () -> Unit
) : CurlResponseBodyData {
    private val bytesWritten = atomic(0)

    val bodyChannel = ByteChannel(true).apply {
        @Suppress("DEPRECATION") attachJob(callContext)
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun onBodyChunkReceived(buffer: CPointer<ByteVar>, size: size_t, count: size_t): Int {
        if (bodyChannel.isClosedForWrite) {
            return if (bodyChannel.closedCause != null) -1 else 0
        }

        val chunkSize = (size * count).toInt()

        // TODO: delete `runBlocking` with fix of https://youtrack.jetbrains.com/issue/KTOR-6030/Migrate-to-new-kotlinx.io-library
        val written = try {
            runBlocking {
                bodyChannel.writeFully(buffer, 0, chunkSize)
            }
            chunkSize
        } catch (cause: Throwable) {
            return -1
        }
        if (written > 0) {
            bytesWritten += written
        }
        if (bytesWritten.value == chunkSize) {
            bytesWritten.value = 0
            return chunkSize
        }

        CoroutineScope(callContext).launch {
            try {
                bodyChannel.awaitFreeSpace()
            } catch (_: Throwable) {
                // no op, error will be handled on next write on cURL thread
            } finally {
                onUnpause()
            }
        }

        return CURL_WRITEFUNC_PAUSE
    }

    override fun close(cause: Throwable?) {
        if (bodyChannel.isClosedForWrite) return
        bodyChannel.close(cause)
    }
}
