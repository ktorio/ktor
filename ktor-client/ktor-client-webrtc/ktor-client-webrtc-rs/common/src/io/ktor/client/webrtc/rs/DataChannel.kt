/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.rs

import io.ktor.client.webrtc.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import uniffi.ktor_client_webrtc.DataChannel
import uniffi.ktor_client_webrtc.DataChannelMessage
import uniffi.ktor_client_webrtc.DataChannelObserver
import uniffi.ktor_client_webrtc.RtcException

/**
 * WebRtc data channel implementation for the Android platform.
 */
public class RustWebRtcDataChannel(
    internal val inner: DataChannel,
    private val coroutineScope: CoroutineScope,
    receiveOptions: DataChannelReceiveOptions
) : WebRtcDataChannel(receiveOptions) {

    override val id: Int?
        get() = inner.id()?.toInt()

    override val label: String
        get() = inner.label()

    override val state: WebRtc.DataChannel.State
        get() = inner.state().toKtor()

    override val bufferedAmount: Long
        get() = runBlocking { inner.bufferedAmount().toLong() }

    override val bufferedAmountLowThreshold: Long
        get() = runBlocking { inner.bufferedAmountLowThreshold().toLong() }

    override val maxPacketLifeTime: Int?
        get() = inner.maxPacketLifetime()?.toInt()

    override val maxRetransmits: Int?
        get() = inner.maxRetransmits()?.toInt()

    override val negotiated: Boolean
        get() = inner.negotiated()

    override val ordered: Boolean
        get() = inner.ordered()

    override val protocol: String
        get() = inner.protocol()

    override suspend fun send(text: String) {
        inner.sendText(text)
    }

    override suspend fun send(bytes: ByteArray) {
        inner.send(bytes)
    }

    override fun setBufferedAmountLowThreshold(threshold: Long): Unit = runBlocking {
        inner.setBufferedAmountLowThreshold(threshold.toULong())
    }

    override fun closeTransport(): Unit = runBlocking {
        inner.closeChannel()
    }

    override fun close() {
        super.close()
        inner.destroy()
    }

    internal inline fun runInScope(crossinline block: suspend () -> Unit) {
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            block()
        }
    }

    internal fun setupEvents(events: WebRtcConnectionEventsEmitter): Unit = runBlocking {
        inner.registerObserver(object : DataChannelObserver {
            override fun onOpen() = runInScope {
                events.emitDataChannelEvent(DataChannelEvent.Open(this@RustWebRtcDataChannel))
            }

            override fun onClose() = runInScope {
                stopReceivingMessages()
                events.emitDataChannelEvent(DataChannelEvent.Closed(this@RustWebRtcDataChannel))
            }

            override fun onError(error: RtcException) = runInScope {
                events.emitDataChannelEvent(
                    DataChannelEvent.Error(
                        this@RustWebRtcDataChannel,
                        error.message ?: "Unknown datachannel error"
                    )
                )
            }

            override fun onMessage(message: DataChannelMessage) = runInScope {
                // This coroutine should start immediately because the protocol relies on the message order
                val message = when (message.isString) {
                    true -> WebRtc.DataChannel.Message.Text(data = message.data.decodeToString())
                    false -> WebRtc.DataChannel.Message.Binary(data = message.data)
                }
                emitMessage(message)
            }

            override fun onBufferedAmountLow() = runInScope {
                events.emitDataChannelEvent(DataChannelEvent.BufferedAmountLow(this@RustWebRtcDataChannel))
            }
        })
    }
}

/**
 * Returns implementation of the data channel that is used under the hood. Use it with caution.
 */
public fun WebRtcDataChannel.getNative(): DataChannel {
    val channel = (this as? RustWebRtcDataChannel) ?: error("Wrong data channel implementation.")
    return channel.inner
}
