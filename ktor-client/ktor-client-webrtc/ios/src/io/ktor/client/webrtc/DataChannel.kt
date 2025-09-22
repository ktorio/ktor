/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import WebRTC.RTCDataBuffer
import WebRTC.RTCDataChannel
import WebRTC.RTCDataChannelDelegateProtocol
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import platform.darwin.NSObject

/**
 * WebRtc data channel implementation for the Ios platform.
 */
@OptIn(ExperimentalForeignApi::class)
public class IosWebRtcDataChannel(
    internal val nativeChannel: RTCDataChannel,
    private val coroutineScope: CoroutineScope,
    receiveOptions: DataChannelReceiveOptions
) : WebRtcDataChannel(receiveOptions) {

    override val id: Int?
        get() = nativeChannel.channelId.let { if (it < 0) null else it }

    override val label: String
        get() = nativeChannel.label()

    override val state: WebRtc.DataChannel.State
        get() = nativeChannel.readyState.toKtor()

    override val bufferedAmount: Long
        get() = nativeChannel.bufferedAmount().toLong()

    override var bufferedAmountLowThreshold: Long = 0
        private set

    override val maxPacketLifeTime: Int
        get() = nativeChannel.maxPacketLifeTime.toInt()

    override val maxRetransmits: Int
        get() = nativeChannel.maxRetransmits.toInt()

    override val negotiated: Boolean
        get() = nativeChannel.isNegotiated

    override val ordered: Boolean
        get() = nativeChannel.isOrdered

    override val protocol: String
        get() = nativeChannel.protocol()

    private fun assertOpen() {
        if (!state.canSend()) {
            error("Data channel is closed.")
        }
    }

    override suspend fun send(text: String) {
        assertOpen()
        nativeChannel.sendData(data = text.toRTCDataBuffer())
    }

    override suspend fun send(bytes: ByteArray) {
        assertOpen()
        nativeChannel.sendData(data = bytes.toRTCDataBuffer())
    }

    override fun setBufferedAmountLowThreshold(threshold: Long) {
        bufferedAmountLowThreshold = threshold
    }

    override fun closeTransport() {
        nativeChannel.close()
    }

    private fun runInConnectionScope(block: suspend CoroutineScope.() -> Unit) {
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED, block = block)
    }

    internal fun setupEvents(eventsEmitter: WebRtcConnectionEventsEmitter) {
        nativeChannel.setDelegate(object : RTCDataChannelDelegateProtocol, NSObject() {
            override fun dataChannel(dataChannel: RTCDataChannel, didChangeBufferedAmount: ULong) =
                runInConnectionScope {
                    // guard against duplicate events
                    if (didChangeBufferedAmount.toLong() < bufferedAmountLowThreshold) {
                        return@runInConnectionScope
                    }
                    // buffer has dropped below the threshold
                    if (bufferedAmount <= bufferedAmountLowThreshold) {
                        val event = DataChannelEvent.BufferedAmountLow(this@IosWebRtcDataChannel)
                        eventsEmitter.emitDataChannelEvent(event)
                    }
                }

            override fun dataChannelDidChangeState(dataChannel: RTCDataChannel) = runInConnectionScope {
                val event = when (state) {
                    WebRtc.DataChannel.State.CONNECTING -> null
                    WebRtc.DataChannel.State.OPEN -> DataChannelEvent.Open(this@IosWebRtcDataChannel)
                    WebRtc.DataChannel.State.CLOSING -> DataChannelEvent.Closing(this@IosWebRtcDataChannel)
                    WebRtc.DataChannel.State.CLOSED -> {
                        stopReceivingMessages()
                        DataChannelEvent.Closed(this@IosWebRtcDataChannel)
                    }
                }
                if (event != null) {
                    eventsEmitter.emitDataChannelEvent(event)
                }
            }

            override fun dataChannel(
                dataChannel: RTCDataChannel,
                didReceiveMessageWithBuffer: RTCDataBuffer
            ) = runInConnectionScope {
                emitMessage(didReceiveMessageWithBuffer.toKtor())
            }
        })
    }
}

/**
 * Returns implementation of the data channel that is used under the hood. Use it with caution.
 */
@OptIn(ExperimentalForeignApi::class)
public fun WebRtcDataChannel.getNative(): RTCDataChannel {
    val channel = (this as? IosWebRtcDataChannel) ?: error("Wrong data channel implementation.")
    return channel.nativeChannel
}
