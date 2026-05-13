/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.internal

import io.ktor.client.engine.curl.internal.Libcurl.WRITEFUNC_ERROR
import io.ktor.client.engine.curl.internal.Libcurl.WRITEFUNC_PAUSE
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.posix.size_t
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext

internal class CurlHttpResponseBody(
    callContext: Job,
    private val onUnpause: () -> Unit,
) : CurlResponseBodyData, CoroutineScope {

    private val job = Job(callContext)
    override val coroutineContext: CoroutineContext = job

    val bodyChannel = ByteChannel().apply {
        attachJob(job)
    }

    @Volatile
    private var paused = false

    @OptIn(ExperimentalForeignApi::class, InternalAPI::class)
    override fun onBodyChunkReceived(buffer: CPointer<ByteVar>, size: size_t, count: size_t): size_t {
        if (bodyChannel.isClosedForWrite) {
            return if (bodyChannel.closedCause != null) WRITEFUNC_ERROR else 0.convert()
        }
        if (paused) return WRITEFUNC_PAUSE

        val chunkSize = (size * count).toLong()
        return try {
            bodyChannel.writeBuffer.writeFully(buffer, 0L, chunkSize)
            bodyChannel.flushWriteBuffer()
            if (!bodyChannel.hasFreeSpace) pauseUntilFreeSpaceAvailable()
            chunkSize.convert()
        } catch (_: Throwable) {
            WRITEFUNC_ERROR
        }
    }

    private fun pauseUntilFreeSpaceAvailable() {
        paused = true
        launch {
            try {
                bodyChannel.awaitFreeSpace()
            } catch (cause: CancellationException) {
                throw cause
            } catch (_: Throwable) {
                // no op, error will be handled on next write on cURL thread
            } finally {
                paused = false
                onUnpause()
            }
        }
    }

    override fun close(cause: Throwable?) {
        if (bodyChannel.isClosedForWrite) return
        bodyChannel.close(cause)
        cancel(cause as? CancellationException ?: CancellationException(cause))
    }
}
