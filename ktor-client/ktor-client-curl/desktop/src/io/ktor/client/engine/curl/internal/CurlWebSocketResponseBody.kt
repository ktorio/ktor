/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.internal

import io.ktor.utils.io.bits.*
import io.ktor.websocket.*
import kotlinx.atomicfu.*
import kotlinx.cinterop.*
import kotlinx.coroutines.channels.*
import libcurl.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
internal class CurlWebSocketResponseBody(
    private val curl: EasyHandle
) : CurlResponseBodyData {

    private val closed = atomic(false)
    private val _incoming = Channel<Frame>(Channel.UNLIMITED)

    val incoming: ReceiveChannel<Frame>
        get() = _incoming

    @OptIn(ExperimentalForeignApi::class)
    fun sendFrame(flags: Int, data: ByteArray) = memScoped {
        if (closed.value) return@memScoped

        val sent = alloc<size_tVar>()
        data.useMemory(0, data.size) { buffer ->
            val status = curl_ws_send(curl, buffer.pointer, buffer.size.convert(), sent.ptr, 0, flags.convert())
            if ((flags and CURLWS_CLOSE) == 0) {
                status.verify()
            }
        }
    }

    override fun onBodyChunkReceived(buffer: CPointer<ByteVar>, size: size_t, count: size_t): Int {
        if (closed.value) return 0

        val bytesRead = (size * count).toInt()
        val meta = curl_ws_meta(curl)?.pointed ?: error("Missing metadata")
        val frameData = buffer.readBytes(bytesRead)

        onFrame(frameData, meta.flags)

        return bytesRead
    }

    private fun onFrame(buffer: ByteArray, flags: Int) {
        val isFinal = (flags and CURLWS_CONT) == 0
        when {
            (flags and CURLWS_BINARY != 0) -> {
                _incoming.trySend(Frame.Binary(fin = isFinal, data = buffer))
            }

            (flags and CURLWS_TEXT != 0) -> {
                _incoming.trySend(Frame.Text(fin = isFinal, data = buffer))
            }

            (flags and CURLWS_PING != 0) -> {
                _incoming.trySend(Frame.Ping(data = buffer))
            }

            (flags and CURLWS_PONG != 0) -> {
                _incoming.trySend(Frame.Ping(data = buffer))
            }

            (flags and CURLWS_CLOSE != 0) -> {
                _incoming.trySend(Frame.Close(buffer))
            }
        }
    }

    override fun close(cause: Throwable?) {
        if (!closed.compareAndSet(expect = false, update = true)) return
        _incoming.close()
    }
}
