/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.client.webrtc.browser.*
import io.ktor.client.webrtc.utils.*
import org.khronos.webgl.ArrayBuffer
import org.w3c.files.Blob

/**
 * WebRtc data channel implementation for the Wasm platform.
 */
public class WasmJsWebRtcDataChannel(
    internal val nativeChannel: RTCDataChannel,
    options: WebRtcDataChannelOptions
) : WebRtcDataChannel(options) {

    override val id: Int
        get() = nativeChannel.id?.toInt() ?: 0

    override val label: String
        get() = nativeChannel.label.toString()

    override val state: WebRtc.DataChannelState
        get() = nativeChannel.readyState.toString().toDataChannelState()

    override val bufferedAmount: Long
        get() = nativeChannel.bufferedAmount.toDouble().toLong()

    override val bufferedAmountLowThreshold: Long
        get() = nativeChannel.bufferedAmountLowThreshold.toDouble().toLong()

    override val maxPacketLifeTime: Int?
        get() = nativeChannel.maxPacketLifeTime?.toInt()

    override val maxRetransmits: Int?
        get() = nativeChannel.maxRetransmits?.toInt()

    override val negotiated: Boolean
        get() = nativeChannel.negotiated

    override val ordered: Boolean
        get() = nativeChannel.ordered

    override val protocol: String
        get() = nativeChannel.protocol.toString()

    override fun send(text: String): Unit = nativeChannel.send(text.toJsString())

    override fun send(bytes: ByteArray): Unit = nativeChannel.send(bytes.toJs())

    override fun setBufferedAmountLowThreshold(threshold: Long) {
        nativeChannel.bufferedAmountLowThreshold = threshold.toInt().toJsNumber()
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
                e.data is JsString -> {
                    emitMessage(Message.Text(e.data.toString()))
                }

                nativeChannel.binaryType.toString() == "arraybuffer" -> {
                    emitMessage(Message.Binary((e.data as ArrayBuffer).toKotlin()))
                }

                nativeChannel.binaryType.toString() == "blob" -> {
                    (e.data as Blob).asArrayBuffer().then {
                        emitMessage(Message.Binary(it.toKotlin()))
                        0.toJsNumber() // make Wasm compiler happy
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
            eventsEmitter.emitDataChannelEvent(DataChannelEvent.Error(this, e.error.message.toString()))
        }
    }
}

/**
 * Returns implementation of the data channel that is used under the hood. Use it with caution.
 */
public fun WebRtcDataChannel.getNative(): RTCDataChannel {
    val channel = (this as? WasmJsWebRtcDataChannel) ?: error("Wrong data channel implementation.")
    return channel.nativeChannel
}
