/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.client.webrtc.browser.*
import io.ktor.client.webrtc.utils.*
import org.khronos.webgl.ArrayBuffer
import org.w3c.files.Blob

public class JsWebRtcDataChannel(
    internal val nativeChannel: RTCDataChannel,
    options: WebRtcDataChannelOptions
) : WebRtcDataChannel(options) {

    override val id: Int
        get() = nativeChannel.id.toInt()

    override val label: String
        get() = nativeChannel.label

    override val state: WebRtc.DataChannelState
        get() = nativeChannel.readyState.toDataChannelState()

    override val bufferedAmount: Long
        get() = nativeChannel.bufferedAmount.toLong()

    override val bufferedAmountLowThreshold: Long
        get() = nativeChannel.bufferedAmountLowThreshold.toLong()

    override val maxPacketLifeTime: Int?
        get() = nativeChannel.maxPacketLifeTime?.toInt()

    override val maxRetransmits: Int?
        get() = nativeChannel.maxRetransmits?.toInt()

    override val negotiated: Boolean
        get() = nativeChannel.negotiated

    override val ordered: Boolean
        get() = nativeChannel.ordered

    override val protocol: String
        get() = nativeChannel.protocol

    override fun send(text: String): Unit = nativeChannel.send(text)

    override fun send(bytes: ByteArray): Unit = nativeChannel.send(bytes.toJs())

    override fun setBufferedAmountLowThreshold(threshold: Long) {
        nativeChannel.bufferedAmountLowThreshold = threshold
    }

    override fun close() {
        nativeChannel.close()
    }

    internal fun setupEvents(eventsEmitter: WebRtcConnectionEventsEmitter) {
        nativeChannel.onopen = { _ ->
            eventsEmitter.emitDataChannelEvent(DataChannelEvent.Open(this))
        }

        nativeChannel.onbufferedamountlow = {
            eventsEmitter.emitDataChannelEvent(DataChannelEvent.BufferedAmountLow(this))
        }

        nativeChannel.onmessage = { e ->
            when {
                jsTypeOf(e.data) == "string" -> {
                    emitMessage(Message.Text(e.data as String))
                }

                nativeChannel.binaryType == "arraybuffer" -> {
                    emitMessage(Message.Binary((e.data as ArrayBuffer).toKotlin()))
                }

                nativeChannel.binaryType == "blob" -> {
                    (e.data as Blob).asArrayBuffer().then {
                        emitMessage(Message.Binary(it.toKotlin()))
                    }
                }

                else -> error("Received message of unknown type: ${e.data}")
            }
        }

        nativeChannel.onclose = { _ ->
            stopReceivingMessages()
            eventsEmitter.emitDataChannelEvent(DataChannelEvent.Closed(this))
        }

        nativeChannel.onerror = { e ->
            eventsEmitter.emitDataChannelEvent(DataChannelEvent.Error(this, e.error.message))
        }
    }
}
