/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.internal

import io.ktor.client.engine.curl.internal.Libcurl.WRITEFUNC_ERROR
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import libcurl.*
import platform.posix.size_t

@OptIn(InternalAPI::class, ExperimentalForeignApi::class)
internal class CurlWebSocketResponseBody(
    internal val easyHandle: EasyHandle,
    incomingFramesConfig: ChannelConfig
) : CurlResponseBodyData {

    private val closed = atomic(false)
    private val _incoming = run {
        require(!incomingFramesConfig.canSuspend) {
            "Curl Client does not support SUSPEND overflow strategy for incoming channel"
        }
        Channel.from<Frame>(incomingFramesConfig)
    }

    val incoming: ReceiveChannel<Frame>
        get() = _incoming

    /**
     * Buffer for collecting WebSocket frame data that libcurl splits across multiple write callbacks
     * due to its internal buffering (~4KB chunks). `null` when no frame is being collected.
     */
    private var frameDataBuffer: Buffer? = null

    override fun onBodyChunkReceived(buffer: CPointer<ByteVar>, size: size_t, count: size_t): size_t {
        if (closed.value) return 0.convert()

        val meta = curl_ws_meta(easyHandle)?.pointed ?: return WRITEFUNC_ERROR
        val chunkSize = meta.len.toInt()
        val chunkData = buffer.readBytes(chunkSize)

        return if (processFrameChunk(chunkData, meta)) chunkSize.convert() else WRITEFUNC_ERROR
    }

    private fun processFrameChunk(chunk: ByteArray, meta: curl_ws_frame): Boolean {
        val flags = meta.flags
        return if (isControlFrame(flags)) {
            handleIncomingFrame(controlFrame(chunk, flags))
        } else {
            // Data frames (text/binary) may be split across callbacks
            handleDataFrameChunk(chunk, meta)
        }
    }

    private fun isControlFrame(flags: Int): Boolean {
        return (flags and (CURLWS_PING or CURLWS_PONG or CURLWS_CLOSE)) != 0
    }

    /** Handles data frame chunks, collecting them if they're split across multiple callbacks. */
    private fun handleDataFrameChunk(chunk: ByteArray, meta: curl_ws_frame): Boolean {
        val flags = meta.flags
        val offset = meta.offset
        val bytesLeft = meta.bytesleft

        // Fast path: complete frame in a single chunk
        if (offset == 0L && bytesLeft == 0L) {
            return handleIncomingFrame(dataFrame(chunk, flags))
        }

        // First chunk of a multi-chunk frame: initialize buffer
        if (offset == 0L) {
            frameDataBuffer = Buffer()
        }

        val buffer = frameDataBuffer ?: return false
        buffer.write(chunk)

        // Last chunk: complete and emit the frame
        if (bytesLeft == 0L) {
            val data = buffer.readByteArray()
            frameDataBuffer = null
            return handleIncomingFrame(dataFrame(data, flags))
        }

        // More chunks expected
        return true
    }

    private fun handleIncomingFrame(frame: Frame?): Boolean =
        if (frame != null) _incoming.trySend(frame).isSuccess else false

    override fun close(cause: Throwable?) {
        if (!closed.compareAndSet(expect = false, update = true)) return
        frameDataBuffer = null
        _incoming.close()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun controlFrame(data: ByteArray, flags: Int): Frame? = when {
    (flags and CURLWS_PING != 0) -> Frame.Ping(data)
    (flags and CURLWS_PONG != 0) -> Frame.Pong(data)
    (flags and CURLWS_CLOSE != 0) -> Frame.Close(data)
    else -> null
}

@OptIn(ExperimentalForeignApi::class)
private fun dataFrame(data: ByteArray, flags: Int): Frame? {
    val isFinal = (flags and CURLWS_CONT) == 0
    return when {
        (flags and CURLWS_BINARY != 0) -> Frame.Binary(fin = isFinal, data = data)
        (flags and CURLWS_TEXT != 0) -> Frame.Text(fin = isFinal, data = data)
        else -> null
    }
}
