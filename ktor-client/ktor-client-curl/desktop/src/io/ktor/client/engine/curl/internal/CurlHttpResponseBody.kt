/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.internal

import io.ktor.client.engine.curl.internal.Libcurl.WRITEFUNC_ERROR
import io.ktor.client.engine.curl.internal.Libcurl.WRITEFUNC_PAUSE
import io.ktor.utils.io.*
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import platform.posix.size_t

internal class CurlHttpResponseBody(
    private val callContext: Job,
    private val onUnpause: () -> Unit
) : CurlResponseBodyData {
    private val bytesWritten = atomic(0)

    val bodyChannel = ByteChannel(true).apply {
        attachJob(callContext)
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun onBodyChunkReceived(buffer: CPointer<ByteVar>, size: size_t, count: size_t): size_t {
        if (bodyChannel.isClosedForWrite) {
            return if (bodyChannel.closedCause != null) WRITEFUNC_ERROR else 0.convert()
        }

        val chunkSize = (size * count).toInt()

        // TODO: delete `runBlocking` with fix of https://youtrack.jetbrains.com/issue/KTOR-6030/Migrate-to-new-kotlinx.io-library
        val written = try {
            runBlocking {
                bodyChannel.writeFully(buffer, 0, chunkSize)
            }
            chunkSize
        } catch (_: Throwable) {
            return WRITEFUNC_ERROR
        }
        if (written > 0) {
            bytesWritten.addAndGet(written)
        }
        if (bytesWritten.value == chunkSize) {
            bytesWritten.value = 0
            return chunkSize.convert()
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

        return WRITEFUNC_PAUSE
    }

    override fun close(cause: Throwable?) {
        if (bodyChannel.isClosedForWrite) return
        bodyChannel.close(cause)
    }
}
