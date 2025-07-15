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
 */
public class AndroidWebRtcDataChannel(
    internal val nativeChannel: DataChannel,
    private val channelInit: DataChannel.Init?,
    private val coroutineScope: CoroutineScope,
    receiveOptions: DataChannelReceiveOptions
) : WebRtcDataChannel(receiveOptions) {

    override val id: Int
        get() = nativeChannel.id()

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

    private fun checkStatus() {
        if (!state.canSend()) {
            error("Data channel is closed.")
        }
    }

    override fun send(text: String) {
        checkStatus()
        val buffer = DataChannel.Buffer(Charsets.UTF_8.encode(text), false)
        nativeChannel.send(buffer)
    }

    override fun send(bytes: ByteArray) {
        checkStatus()
        val buffer = DataChannel.Buffer(ByteBuffer.wrap(bytes), true)
        nativeChannel.send(buffer)
    }

    override fun setBufferedAmountLowThreshold(threshold: Long) {
        bufferedAmountLowThreshold = threshold
    }

    override fun closeTransport() {
        nativeChannel.dispose()
    }

    internal fun setupEvents(eventsEmitter: WebRtcConnectionEventsEmitter) {
        nativeChannel.registerObserver(object : DataChannel.Observer {
            override fun onStateChange() {
                coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    val event = when (state) {
                        WebRtc.DataChannel.State.CONNECTING -> null
                        WebRtc.DataChannel.State.OPEN -> DataChannelEvent.Open(this@AndroidWebRtcDataChannel)
                        WebRtc.DataChannel.State.CLOSING -> DataChannelEvent.Closing(this@AndroidWebRtcDataChannel)
                        WebRtc.DataChannel.State.CLOSED -> {
                            stopReceivingMessages()
                            DataChannelEvent.Closed(this@AndroidWebRtcDataChannel)
                        }
                    }
                    event?.let { eventsEmitter.emitDataChannelEvent(it) }
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer?) {
                // This coroutine should start immediately because the protocol relies on the message order
                coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    if (buffer == null) {
                        return@launch
                    }
                    val message = when (buffer.binary) {
                        true -> {
                            val data = ByteArray(buffer.data.remaining()).apply { buffer.data.get(this) }
                            WebRtc.DataChannel.Message.Binary(data)
                        }

                        false -> {
                            val data = Charsets.UTF_8.decode(buffer.data).toString()
                            WebRtc.DataChannel.Message.Text(data)
                        }
                    }
                    emitMessage(message)
                }
            }

            override fun onBufferedAmountChange(previousAmount: Long) {
                coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    if (bufferedAmountLowThreshold in (bufferedAmount + 1)..<previousAmount) {
                        return@launch
                    }
                    val event = DataChannelEvent.BufferedAmountLow(this@AndroidWebRtcDataChannel)
                    eventsEmitter.emitDataChannelEvent(event)
                }
            }
        })
    }
}

/**
 * Returns implementation of the data channel that is used under the hood. Use it with caution.
 */
public fun WebRtcDataChannel.getNative(): DataChannel {
    val channel = (this as? AndroidWebRtcDataChannel) ?: error("Wrong data channel implementation.")
    return channel.nativeChannel
}
