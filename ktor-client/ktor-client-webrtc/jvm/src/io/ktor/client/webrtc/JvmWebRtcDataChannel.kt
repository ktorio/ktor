/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import dev.onvoid.webrtc.RTCDataChannel
import dev.onvoid.webrtc.RTCDataChannelBuffer
import dev.onvoid.webrtc.RTCDataChannelObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/**
 * WebRtc data channel implementation based on `dev.onvoid.webrtc.RTCDataChannel`.
 */
public class JvmWebRtcDataChannel(
    internal val inner: RTCDataChannel,
    private val coroutineScope: CoroutineScope,
    receiveOptions: DataChannelReceiveOptions
) : WebRtcDataChannel(receiveOptions) {

    override val id: Int?
        get() = inner.id.takeIf { it >= 0 }

    override val label: String
        get() = inner.label

    override val state: WebRtc.DataChannel.State
        get() = inner.state.toKtor()

    override val bufferedAmount: Long
        get() = inner.bufferedAmount

    override var bufferedAmountLowThreshold: Long = 0
        private set

    override val maxPacketLifeTime: Int?
        get() = inner.maxPacketLifeTime.takeIf { it >= 0 }

    override val maxRetransmits: Int?
        get() = inner.maxRetransmits.takeIf { it >= 0 }

    override val negotiated: Boolean
        get() = inner.isNegotiated

    override val ordered: Boolean
        get() = inner.isOrdered

    override val protocol: String
        get() = inner.protocol ?: ""

    override suspend fun send(text: String) {
        require(state == WebRtc.DataChannel.State.OPEN) {
            "Can't send a message when the channel is not open."
        }
        val data = ByteBuffer.wrap(text.encodeToByteArray())
        inner.send(RTCDataChannelBuffer(data, false))
    }

    override suspend fun send(bytes: ByteArray) {
        require(state == WebRtc.DataChannel.State.OPEN) {
            "Can't send a message when the channel is not open."
        }
        val data = ByteBuffer.wrap(bytes)
        inner.send(RTCDataChannelBuffer(data, true))
    }

    override fun setBufferedAmountLowThreshold(threshold: Long) {
        bufferedAmountLowThreshold = threshold
    }

    override fun closeTransport() {
        inner.close()
    }

    override fun close() {
        super.close()
        inner.dispose()
    }

    private fun runInConnectionScope(block: suspend CoroutineScope.() -> Unit) {
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED, block = block)
    }

    internal fun setupEvents(eventsEmitter: WebRtcConnectionEventsEmitter) {
        inner.registerObserver(object : RTCDataChannelObserver {
            override fun onBufferedAmountChange(previousAmount: Long) = runInConnectionScope {
                // guard against duplicate events
                if (previousAmount < bufferedAmountLowThreshold) {
                    return@runInConnectionScope
                }
                // buffer has dropped below the threshold
                if (bufferedAmount <= bufferedAmountLowThreshold) {
                    val event = DataChannelEvent.BufferedAmountLow(this@JvmWebRtcDataChannel)
                    eventsEmitter.emitDataChannelEvent(event)
                }
            }

            override fun onStateChange() = runInConnectionScope {
                val event = when (state) {
                    WebRtc.DataChannel.State.CONNECTING -> null
                    WebRtc.DataChannel.State.OPEN -> DataChannelEvent.Open(this@JvmWebRtcDataChannel)
                    WebRtc.DataChannel.State.CLOSING -> DataChannelEvent.Closing(this@JvmWebRtcDataChannel)
                    WebRtc.DataChannel.State.CLOSED -> {
                        stopReceivingMessages()
                        DataChannelEvent.Closed(this@JvmWebRtcDataChannel)
                    }
                }
                if (event != null) eventsEmitter.emitDataChannelEvent(event)
            }

            override fun onMessage(buffer: RTCDataChannelBuffer) = runInConnectionScope {
                emitMessage(buffer.toKtor())
            }
        })
    }
}

/**
 * Returns the native RTCDataChannel. Use with caution.
 */
public fun WebRtcDataChannel.getNative(): RTCDataChannel {
    return (this as JvmWebRtcDataChannel).inner
}
