/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.internal

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import libcurl.*
import platform.posix.*
import kotlin.coroutines.*

/**
 *  The callback is getting called on each completely parser header line.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun onHeadersReceived(
    buffer: CPointer<ByteVar>,
    size: size_t,
    count: size_t,
    userdata: COpaquePointer
): Long {
    val response = userdata.fromCPointer<CurlResponseBuilder>()
    val packet = response.headersBytes
    val chunkSize = (size * count).toLong()
    packet.writeFully(buffer, 0, chunkSize)

    if (isFinalHeaderLine(chunkSize, buffer) && !response.bodyStartedReceiving.isCompleted) {
        response.bodyStartedReceiving.complete(Unit)
    }

    return chunkSize
}

/**
 * Checks if the given header represents the final header line (CR LF).
 *
 * @see <a href="https://curl.se/libcurl/c/CURLOPT_HEADERFUNCTION.html">Description.</a>
 */
@OptIn(ExperimentalForeignApi::class)
private fun isFinalHeaderLine(chunkSize: Long, buffer: CPointer<ByteVar>) =
    chunkSize == 2L && buffer[0] == 0x0D.toByte() && buffer[1] == 0x0A.toByte()

@OptIn(ExperimentalForeignApi::class)
internal fun onBodyChunkReceived(
    buffer: CPointer<ByteVar>,
    size: size_t,
    count: size_t,
    userdata: COpaquePointer
): Int {
    val wrapper = userdata.fromCPointer<CurlResponseBodyData>()
    return wrapper.onBodyChunkReceived(buffer, size, count)
}

@OptIn(ExperimentalForeignApi::class)
internal fun onBodyChunkRequested(
    buffer: CPointer<ByteVar>,
    size: size_t,
    count: size_t,
    dataRef: COpaquePointer
): Int {
    val wrapper: CurlRequestBodyData = dataRef.fromCPointer()
    val body = wrapper.body
    val requested = (size * count).toInt()

    if (body.isClosedForRead) {
        return if (body.closedCause != null) -1 else 0
    }
    @Suppress("DEPRECATION")
    val readCount = try {
        body.readAvailable(1) { source: Buffer ->
            source.readAvailable(buffer, 0, requested)
        }
    } catch (cause: Throwable) {
        return -1
    }
    if (readCount > 0) {
        return readCount
    }

    CoroutineScope(wrapper.callContext).launch {
        try {
            body.awaitContent()
        } catch (_: Throwable) {
            // no op, error will be handled on next read on cURL thread
        } finally {
            wrapper.onUnpause()
        }
    }
    return CURL_READFUNC_PAUSE
}

internal class CurlRequestBodyData(
    val body: ByteReadChannel,
    val callContext: CoroutineContext,
    val onUnpause: () -> Unit
)

internal interface CurlResponseBodyData {
    @OptIn(ExperimentalForeignApi::class)
    fun onBodyChunkReceived(buffer: CPointer<ByteVar>, size: size_t, count: size_t): Int
    fun close(cause: Throwable? = null)
}
