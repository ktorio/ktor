/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.internal

import io.ktor.client.engine.curl.internal.Libcurl.WRITEFUNC_ERROR
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.coroutines.Job
import platform.posix.size_t

internal class CurlHttpResponseBody(
    callContext: Job,
) : CurlResponseBodyData {

    val bodyChannel = ByteChannel().apply {
        attachJob(callContext)
    }

    @OptIn(ExperimentalForeignApi::class, InternalAPI::class)
    override fun onBodyChunkReceived(buffer: CPointer<ByteVar>, size: size_t, count: size_t): size_t {
        if (bodyChannel.isClosedForWrite) {
            return if (bodyChannel.closedCause != null) WRITEFUNC_ERROR else 0.convert()
        }

        val chunkSize = (size * count).toLong()
        return try {
            // TODO KTOR-9483: Handle backpressure
            bodyChannel.writeBuffer.writeFully(buffer, 0L, chunkSize)
            bodyChannel.flushWriteBuffer()
            chunkSize.convert()
        } catch (_: Throwable) {
            WRITEFUNC_ERROR
        }
    }

    override fun close(cause: Throwable?) {
        if (bodyChannel.isClosedForWrite) return
        if (cause != null) {
            bodyChannel.cancel(cause)
        } else {
            bodyChannel.close()
        }
    }
}
