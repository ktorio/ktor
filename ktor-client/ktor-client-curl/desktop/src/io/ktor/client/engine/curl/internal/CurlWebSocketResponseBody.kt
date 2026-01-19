/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.internal

import io.ktor.utils.io.InternalAPI
import io.ktor.websocket.*
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import libcurl.*
import platform.posix.size_t
import platform.posix.size_tVar

@OptIn(InternalAPI::class, ExperimentalForeignApi::class)
internal class CurlWebSocketResponseBody(
    private val curl: EasyHandle,
    incomingFramesConfig: ChannelConfig
) : CurlResponseBodyData {

    private val closed = atomic(false)
    private val _incoming = if (incomingFramesConfig.canSuspend) {
        throw IllegalArgumentException("Curl Client does not support SUSPEND overflow strategy for incoming channel")
    } else {
        Channel.from<Frame>(incomingFramesConfig)
    }

    val incoming: ReceiveChannel<Frame>
        get() = _incoming

    @OptIn(ExperimentalForeignApi::class)
    fun sendFrame(flags: Int, data: ByteArray) = memScoped {
        if (closed.value) return@memScoped

        val sent = alloc<size_tVar>()
        data.usePinned { pinned ->
            val address = if (data.isEmpty()) null else pinned.addressOf(0)
            val status = curl_ws_send(curl, address, data.size.convert(), sent.ptr, 0, flags.convert())
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

        if (!onFrame(frameData, meta.flags)) {
            return 0
        }

        return bytesRead
    }

    private fun onFrame(buffer: ByteArray, flags: Int): Boolean {
        val isFinal = (flags and CURLWS_CONT) == 0
        val frame = when {
            (flags and CURLWS_BINARY != 0) -> Frame.Binary(fin = isFinal, data = buffer)
            (flags and CURLWS_TEXT != 0) -> Frame.Text(fin = isFinal, data = buffer)
            (flags and CURLWS_PING != 0) -> Frame.Ping(data = buffer)
            (flags and CURLWS_PONG != 0) -> Frame.Pong(data = buffer)
            (flags and CURLWS_CLOSE != 0) -> Frame.Close(buffer)
            else -> return false
        }
        return _incoming.trySend(frame).isSuccess
    }

    override fun close(cause: Throwable?) {
        if (!closed.compareAndSet(expect = false, update = true)) return
        _incoming.close()
    }
}
