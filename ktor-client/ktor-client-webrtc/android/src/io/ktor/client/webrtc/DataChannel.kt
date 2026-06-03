/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import java.nio.ByteBuffer

/**
 * WebRtc data channel implementation for the Android platform.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.AndroidWebRtcDataChannel)
 */
public class AndroidWebRtcDataChannel(
    internal val nativeChannel: DataChannel,
    private val channelInit: DataChannel.Init?,
    private val coroutineScope: CoroutineScope,
    receiveOptions: DataChannelReceiveOptions
) : WebRtcDataChannel(receiveOptions) {

    override val id: Int?
        get() = nativeChannel.id().let { if (it >= 0) it else null }

    override val label: String
        get() = nativeChannel.label()

    override val state: WebRtc.DataChannel.State
        get() = nativeChannel.state().toKtor()

    override val bufferedAmount: Long
        get() = nativeChannel.bufferedAmount()

    override var bufferedAmountLowThreshold: Long = 0
        private set

    override val maxPacketLifeTime: Int?
        get() = channelInit?.maxRetransmitTimeMs

    override val maxRetransmits: Int?
        get() = channelInit?.maxRetransmits

    override val negotiated: Boolean
        get() = channelInit?.negotiated ?: error("Can't retrieve negotiated state on Android")

    override val ordered: Boolean
        get() = channelInit?.ordered ?: error("Can't retrieve ordered state on Android")

    override val protocol: String
        get() = channelInit?.protocol ?: error("Protocol is not supported in WebRTC")

    private fun assertOpen() {
        if (!state.canSend()) {
            error("Data channel is closed.")
        }
    }

    override suspend fun send(text: String) {
        assertOpen()
        val buffer = DataChannel.Buffer(Charsets.UTF_8.encode(text), false)
        nativeChannel.send(buffer)
    }

    override suspend fun send(bytes: ByteArray) {
        assertOpen()
        val buffer = DataChannel.Buffer(ByteBuffer.wrap(bytes), true)
        nativeChannel.send(buffer)
    }

    override fun setBufferedAmountLowThreshold(threshold: Long) {
        bufferedAmountLowThreshold = threshold
    }

    override fun closeTransport() {
        nativeChannel.close()
    }

    override fun close() {
        super.close()
        nativeChannel.dispose()
    }

    private inline fun runInConnectionScope(crossinline block: suspend () -> Unit) {
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            block()
        }
    }

    internal fun setupEvents(eventsEmitter: WebRtcConnectionEventsEmitter) {
        nativeChannel.registerObserver(object : DataChannel.Observer {
            override fun onStateChange() = runInConnectionScope {
                val event = when (state) {
                    WebRtc.DataChannel.State.CONNECTING -> null
                    WebRtc.DataChannel.State.OPEN -> DataChannelEvent.Open(this@AndroidWebRtcDataChannel)
                    WebRtc.DataChannel.State.CLOSING -> DataChannelEvent.Closing(this@AndroidWebRtcDataChannel)
                    WebRtc.DataChannel.State.CLOSED -> {
                        stopReceivingMessages()
                        DataChannelEvent.Closed(this@AndroidWebRtcDataChannel)
                    }
                }
                if (event != null) {
                    eventsEmitter.emitDataChannelEvent(event)
                }
            }

            // This coroutine should start immediately because the protocol relies on the message order
            override fun onMessage(buffer: DataChannel.Buffer?) = runInConnectionScope {
                val message = buffer?.toKtor() ?: return@runInConnectionScope
                emitMessage(message)
            }

            override fun onBufferedAmountChange(previousAmount: Long) = runInConnectionScope {
                // guard against duplicate events
                if (previousAmount < bufferedAmountLowThreshold) {
                    return@runInConnectionScope
                }
                // buffer has dropped below the threshold
                if (bufferedAmount <= bufferedAmountLowThreshold) {
                    val event = DataChannelEvent.BufferedAmountLow(this@AndroidWebRtcDataChannel)
                    eventsEmitter.emitDataChannelEvent(event)
                }
            }
        })
    }
}

/**
 * Returns implementation of the data channel that is used under the hood. Use it with caution.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.getNative)
 */
public fun WebRtcDataChannel.getNative(): DataChannel {
    return (this as AndroidWebRtcDataChannel).nativeChannel
}
