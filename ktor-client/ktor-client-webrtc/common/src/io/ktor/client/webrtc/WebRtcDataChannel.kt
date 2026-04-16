/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.utils.io.*
import kotlinx.coroutines.channels.*
import kotlin.time.Duration

/**
 * Configuration options for creating a [Channel].
 * This class is used to configure the incoming messages channel in [WebRtcDataChannel].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.DataChannelReceiveOptions)
 *
 * @see [Channel] constructor documentation for more details.
 */
@KtorDsl
public class DataChannelReceiveOptions {
    /**
     * The capacity of the channel buffer.
     * Default is [Channel.UNLIMITED].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.DataChannelReceiveOptions.capacity)
     */
    public var capacity: Int = Channel.UNLIMITED

    /**
     * The behavior when the channel buffer is full.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.DataChannelReceiveOptions.onBufferOverflow)
     */
    public var onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND

    /**
     * An optional callback that is invoked when an element couldn't be delivered to its destination.
     * This can happen when the channel is closed or when an element is dropped due to buffer overflow.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.DataChannelReceiveOptions.onUndeliveredElement)
     */
    public var onUndeliveredElement: ((WebRtc.DataChannel.Message) -> Unit)? = null
}

/**
 * Configuration options for creating a WebRTC data channel.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcDataChannelOptions)
 *
 * @see [MDN RTCDataChannel](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel)
 */
@KtorDsl
public class WebRtcDataChannelOptions {
    /**
     * A 16-bit numeric ID for the channel; permitted values are 0 to 65534.
     * If you don't include this option, the user agent will select an ID for you.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcDataChannelOptions.id)
     *
     * @see [MDN RTCDataChannel.id](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/id)
     */
    public var id: Int? = null

    /**
     * The name of the sub-protocol being used on the [WebRtcDataChannel],
     * if any; otherwise, the empty string (""). Default: empty string ("").
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcDataChannelOptions.protocol)
     *
     * @see [MDN RTCDataChannel.protocol](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/protocol)
     */
    public var protocol: String = ""

    /**
     * The maximum number of milliseconds that attempts to transfer a message may take in unreliable mode.
     * Default: null.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcDataChannelOptions.maxPacketLifeTime)
     *
     * @see [MDN RTCDataChannel.maxPacketLifeTime](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/maxPacketLifeTime)
     */
    public var maxPacketLifeTime: Duration? = null

    /**
     * The maximum number of times the user agent should attempt to retransmit a message
     * which fails the first time in unreliable mode.
     * Default: null.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcDataChannelOptions.maxRetransmits)
     *
     * @see [MDN RTCDataChannel.maxRetransmits](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/maxRetransmits)
     */
    public var maxRetransmits: Int? = null

    /**
     * By default, (false), data channels are negotiated in-band, where one side calls [WebRtcPeerConnection.createDataChannel],
     * and the other side listens to the event using the [WebRtcPeerConnection.createDataChannel] event handler.
     * Alternatively (true), they can be negotiated out of-band, where both sides call
     * [WebRtcPeerConnection.createDataChannel] with an agreed-upon ID.
     * Default: false.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcDataChannelOptions.negotiated)
     *
     * @see [MDN RTCDataChannel.negotiated](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/negotiated)
     */
    public var negotiated: Boolean = false

    /**
     * Indicates whether messages sent on the [WebRtcDataChannel] are required to arrive at their destination
     * in the same order in which they were sent (true), or if they're allowed to arrive out-of-order (false).
     * Default: true.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcDataChannelOptions.ordered)
     *
     * @see [MDN RTCDataChannel.ordered](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/ordered)
     */
    public var ordered: Boolean = true

    /**
     * Configurations for message receiver [Channel].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcDataChannelOptions.receiveOptions)
     */
    public var receiveOptions: DataChannelReceiveOptions.() -> Unit = {}
}

/**
 * Events that can be emitted by a WebRTC data channel.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.DataChannelEvent)
 *
 * @see [MDN RTCDataChannel events](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel#events)
 */
public sealed interface DataChannelEvent {
    public val channel: WebRtcDataChannel

    /**
     * Fired when the data channel becomes open and ready to be used.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.DataChannelEvent.Open)
     *
     * @see [MDN open event](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/open_event)
     */
    public class Open(override val channel: WebRtcDataChannel) : DataChannelEvent

    /**
     * Fired when the data channel is closing.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.DataChannelEvent.Closing)
     *
     * @see [MDN closing event](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/closing_event)
     */
    public class Closing(override val channel: WebRtcDataChannel) : DataChannelEvent

    /**
     * Fired when the data channel has closed.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.DataChannelEvent.Closed)
     *
     * @see [MDN close event](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/close_event)
     */
    public class Closed(override val channel: WebRtcDataChannel) : DataChannelEvent

    /**
     * Fired when the buffered amount of data falls below the threshold.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.DataChannelEvent.BufferedAmountLow)
     *
     * @see [MDN bufferedamountlow event](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/bufferedamountlow_event)
     */
    public class BufferedAmountLow(override val channel: WebRtcDataChannel) : DataChannelEvent

    /**
     * Fired when an error occurs on the data channel.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.DataChannelEvent.Error)
     *
     * @param reason The error reason.
     * @see [MDN error event](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/error_event)
     */
    public class Error(override val channel: WebRtcDataChannel, public val reason: String) : DataChannelEvent
}

private fun Channel(options: DataChannelReceiveOptions): Channel<WebRtc.DataChannel.Message> {
    return Channel(
        capacity = options.capacity,
        onBufferOverflow = options.onBufferOverflow,
        onUndeliveredElement = options.onUndeliveredElement
    )
}

/**
 * Abstract class representing a network channel which can be used for bidirectional peer-to-peer transfers
 * of arbitrary data. Every data channel is associated with an [WebRtcPeerConnection].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcDataChannel)
 *
 * @see [MDN RTCDataChannel](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel)
 */
public abstract class WebRtcDataChannel private constructor(
    private val receiveChannel: Channel<WebRtc.DataChannel.Message>,
) : WebRtc.DataChannel {

    public constructor(receiveOptions: DataChannelReceiveOptions) : this(
        receiveChannel = Channel(options = receiveOptions)
    )

    override suspend fun receive(): WebRtc.DataChannel.Message = receiveChannel.receive()

    override suspend fun receiveBinary(): ByteArray = receive().binaryOrThrow()

    override suspend fun receiveText(): String = receive().textOrThrow()

    override fun tryReceive(): WebRtc.DataChannel.Message? = receiveChannel.tryReceive().getOrNull()

    override fun tryReceiveBinary(): ByteArray? = tryReceive()?.binaryOrNull()

    override fun tryReceiveText(): String? = tryReceive()?.textOrNull()

    protected fun emitMessage(message: WebRtc.DataChannel.Message): ChannelResult<Unit> {
        return receiveChannel.trySend(message)
    }

    protected fun stopReceivingMessages() {
        receiveChannel.close()
    }
}
