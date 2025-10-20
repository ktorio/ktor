/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import js.buffer.ArrayBuffer
import js.core.JsString
import kotlinx.coroutines.CoroutineScope
import web.blob.Blob
import web.blob.arrayBuffer
import web.rtc.RTCDataChannel
import web.sockets.BinaryType
import web.sockets.arraybuffer
import web.sockets.blob

/**
 * WebRtc data channel implementation for the JavaScript platform.
 *
 * @param channel Native browser RTCDataChannel.
 */
public class JsWebRtcDataChannel(
    internal val channel: RTCDataChannel,
    private val coroutineScope: CoroutineScope,
    receiveOptions: DataChannelReceiveOptions
) : WebRtcDataChannel(receiveOptions) {

    override val id: Int?
        get() = channel.id?.let { if (it >= 0) it.toInt() else null }

    override val label: String
        get() = channel.label

    override val state: WebRtc.DataChannel.State
        get() = channel.readyState.toKtor()

    override val bufferedAmount: Long
        get() = channel.bufferedAmount.toLong()

    override val bufferedAmountLowThreshold: Long
        get() = channel.bufferedAmountLowThreshold.toLong()

    override val maxPacketLifeTime: Int?
        get() = channel.maxPacketLifeTime?.toInt()

    override val maxRetransmits: Int?
        get() = channel.maxRetransmits?.toInt()

    override val negotiated: Boolean
        get() = channel.negotiated

    override val ordered: Boolean
        get() = channel.ordered

    override val protocol: String
        get() = channel.protocol

    override suspend fun send(text: String) {
        channel.send(text)
    }

    override suspend fun send(bytes: ByteArray) {
        channel.send(bytes.toArrayBuffer())
    }

    override fun setBufferedAmountLowThreshold(threshold: Long) {
        channel.bufferedAmountLowThreshold = threshold.toInt()
    }

    override fun closeTransport() {
        channel.close()
    }

    internal fun setupEvents(eventsEmitter: WebRtcConnectionEventsEmitter) {
        channel.onopen = eventHandler(coroutineScope) {
            eventsEmitter.emitDataChannelEvent(DataChannelEvent.Open(this))
        }

        channel.onbufferedamountlow = eventHandler(coroutineScope) {
            eventsEmitter.emitDataChannelEvent(DataChannelEvent.BufferedAmountLow(this))
        }

        channel.onmessage = eventHandler(coroutineScope) { e ->
            when {
                e.data is JsString -> {
                    emitMessage(WebRtc.DataChannel.Message.Text(e.data.toString()))
                }

                channel.binaryType == BinaryType.arraybuffer -> {
                    emitMessage(WebRtc.DataChannel.Message.Binary((e.data as ArrayBuffer).toByteArray()))
                }

                channel.binaryType == BinaryType.blob -> {
                    val buffer = (e.data as Blob).arrayBuffer()
                    emitMessage(WebRtc.DataChannel.Message.Binary(buffer.toByteArray()))
                }

                else -> error("Received message of unknown type: ${e.data}")
            }
        }

        channel.onclose = eventHandler(coroutineScope) { _ ->
            stopReceivingMessages()
            eventsEmitter.emitDataChannelEvent(DataChannelEvent.Closed(this))
        }

        channel.onerror = eventHandler(coroutineScope) { e ->
            eventsEmitter.emitDataChannelEvent(DataChannelEvent.Error(this, e.error.message))
        }
    }
}

/**
 * Returns implementation of the data channel that is used under the hood. Use it with caution.
 */
public fun WebRtcDataChannel.getNative(): RTCDataChannel {
    return (this as JsWebRtcDataChannel).channel
}
