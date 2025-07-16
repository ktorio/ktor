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
 * @see [Channel] constructor documentation for more details.
 */
@KtorDsl
public class DataChannelReceiveOptions {
    /**
     * The capacity of the channel buffer.
     * Default is [Channel.UNLIMITED].
     */
    public var capacity: Int = Channel.UNLIMITED

    /**
     * The behavior when the channel buffer is full.
     */
    public var onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND

    /**
     * An optional callback that is invoked when an element couldn't be delivered to its destination.
     * This can happen when the channel is closed or when an element is dropped due to buffer overflow.
     */
    public var onUndeliveredElement: ((WebRtcDataChannel.Message) -> Unit)? = null
}

/**
 * Configuration options for creating a WebRTC data channel.
 *
 * @see [MDN RTCDataChannel](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel)
 */
@KtorDsl
public class WebRtcDataChannelOptions {
    /**
     * A 16-bit numeric ID for the channel; permitted values are 0 to 65534.
     * If you don't include this option, the user agent will select an ID for you.
     *
     * @see [MDN RTCDataChannel.id](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/id)
     */
    public var id: Int? = null

    /**
     * The name of the sub-protocol being used on the [WebRtcDataChannel],
     * if any; otherwise, the empty string (""). Default: empty string ("").
     *
     * @see [MDN RTCDataChannel.protocol](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/protocol)
     */
    public var protocol: String = ""

    /**
     * The maximum number of milliseconds that attempts to transfer a message may take in unreliable mode.
     * Default: null.
     *
     * @see [MDN RTCDataChannel.maxPacketLifeTime](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/maxPacketLifeTime)
     */
    public var maxPacketLifeTime: Duration? = null

    /**
     * The maximum number of times the user agent should attempt to retransmit a message
     * which fails the first time in unreliable mode.
     * Default: null.
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
     * @see [MDN RTCDataChannel.negotiated](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/negotiated)
     */
    public var negotiated: Boolean = false

    /**
     * Indicates whether messages sent on the [WebRtcDataChannel] are required to arrive at their destination
     * in the same order in which they were sent (true), or if they're allowed to arrive out-of-order (false).
     * Default: true.
     *
     * @see [MDN RTCDataChannel.ordered](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/ordered)
     */
    public var ordered: Boolean = true

    /**
     * Configurations for message receiver [Channel].
     */
    public var receiveOptions: DataChannelReceiveOptions.() -> Unit = {}
}

/**
 * Events that can be emitted by a WebRTC data channel.
 *
 * @see [MDN RTCDataChannel events](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel#events)
 */
public sealed interface DataChannelEvent {
    public val channel: WebRtcDataChannel

    /**
     * Fired when the data channel becomes open and ready to be used.
     *
     * @see [MDN open event](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/open_event)
     */
    public class Open(override val channel: WebRtcDataChannel) : DataChannelEvent

    /**
     * Fired when the data channel is closing.
     *
     * @see [MDN closing event](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/closing_event)
     */
    public class Closing(override val channel: WebRtcDataChannel) : DataChannelEvent

    /**
     * Fired when the data channel has closed.
     *
     * @see [MDN close event](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/close_event)
     */
    public class Closed(override val channel: WebRtcDataChannel) : DataChannelEvent

    /**
     * Fired when the buffered amount of data falls below the threshold.
     *
     * @see [MDN bufferedamountlow event](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/bufferedamountlow_event)
     */
    public class BufferedAmountLow(override val channel: WebRtcDataChannel) : DataChannelEvent

    /**
     * Fired when an error occurs on the data channel.
     *
     * @param reason The error reason.
     * @see [MDN error event](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/error_event)
     */
    public class Error(override val channel: WebRtcDataChannel, public val reason: String) : DataChannelEvent
}

private fun Channel(options: DataChannelReceiveOptions): Channel<WebRtcDataChannel.Message> {
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
 * @see [MDN RTCDataChannel](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel)
 */
public abstract class WebRtcDataChannel private constructor(
    private val receiveChannel: Channel<Message>,
) : AutoCloseable, ReceiveChannel<WebRtcDataChannel.Message> by receiveChannel {

    public constructor(options: WebRtcDataChannelOptions) : this(
        receiveChannel = Channel(
            options = DataChannelReceiveOptions().apply(options.receiveOptions)
        ),
    )

    /**
     * Represents a message that can be received through a WebRTC data channel.
     * The message can contain either string data or binary data.
     *
     * @see [MDN RTCDataChannel.send()](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/send)
     * @see [MDN message event](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/message_event)
     */
    public sealed interface Message {
        public class Text(public val data: String) : Message
        public class Binary(public val data: ByteArray) : Message
    }

    /**
     * An ID number (between 0 and 65,534) which uniquely identifies the data channel.
     *
     * @see [MDN RTCDataChannel.id](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/id)
     */
    public abstract val id: Int

    /**
     * A string containing a name describing the data channel.
     *
     * @see [MDN RTCDataChannel.label](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/label)
     */
    public abstract val label: String

    /**
     * A state of the data channel's underlying data connection.
     *
     * @see [MDN RTCDataChannel.readyState](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/readyState)
     */
    public abstract val state: WebRtc.DataChannelState

    /**
     * A number of bytes of data currently queued to be sent over the data channel.
     *
     * @see [MDN RTCDataChannel.bufferedAmount](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/bufferedAmount)
     */
    public abstract val bufferedAmount: Long

    /**
     * A number of queued outgoing data bytes below which the buffer is considered to be "low."
     * When the number of buffered outgoing bytes, as indicated by the bufferedAmount property,
     * falls to or below this value, a [DataChannelEvent.BufferedAmountLow] event is fired.
     * The default value is 0.
     *
     * @see [MDN RTCDataChannel.bufferedAmountLowThreshold](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/bufferedAmountLowThreshold)
     */
    public abstract val bufferedAmountLowThreshold: Long

    /**
     * The maximum number of milliseconds that attempts to transfer a message may take in unreliable mode.
     *
     * @see [MDN RTCDataChannel.maxPacketLifeTime](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/maxPacketLifeTime)
     */
    public abstract val maxPacketLifeTime: Int?

    /**
     * The maximum number of times the user agent should attempt to retransmit a message
     * which fails the first time in unreliable mode.
     *
     * @see [MDN RTCDataChannel.maxRetransmits](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/maxRetransmits)
     */
    public abstract val maxRetransmits: Int?

    /**
     * Indicates whether the data channel was negotiated by the application or the WebRTC layer.
     *
     * @see [MDN RTCDataChannel.negotiated](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/negotiated)
     */
    public abstract val negotiated: Boolean

    /**
     * Indicates whether messages sent on the data channel are required to arrive at their destination
     * in the same order in which they were sent, or if they're allowed to arrive out-of-order.
     *
     * @see [MDN RTCDataChannel.ordered](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/ordered)
     */
    public abstract val ordered: Boolean

    /**
     * The name of the sub-protocol being used on the data channel, if any; otherwise, the empty string.
     *
     * @see [MDN RTCDataChannel.protocol](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/protocol)
     */
    public abstract val protocol: String

    public abstract fun setBufferedAmountLowThreshold(threshold: Long)

    /**
     * Sends a text message through the data channel.
     *
     * @param text The text message to send.
     * @see [MDN RTCDataChannel.send()](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/send)
     */
    public abstract fun send(text: String)

    /**
     * Sends binary data through the data channel.
     *
     * @param bytes The binary data to send.
     * @see [MDN RTCDataChannel.send()](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/send)
     */
    public abstract fun send(bytes: ByteArray)

    /**
     * Receives a binary message from the data channel.
     *
     * This method suspends until a binary message is available. If the next message
     * in the channel is a text message instead of binary data, this method will throw
     * an error.
     */
    public suspend fun receiveBinary(): ByteArray {
        val binary = receive() as? Message.Binary ?: error("Received a string instead of binary data.")
        return binary.data
    }

    /**
     * Receives a text message from the data channel.
     *
     * This method suspends until a text message is available. If the next message
     * in the channel is binary data instead of text, this method will throw an error.
     */
    public suspend fun receiveText(): String {
        val text = receive() as? Message.Text ?: error("Received a binary instead of string data.")
        return text.data
    }

    protected fun emitMessage(message: Message): ChannelResult<Unit> {
        return receiveChannel.trySend(message)
    }

    protected fun stopReceivingMessages() {
        receiveChannel.close()
    }

    /**
     * Closes the data channel and releases its resources.
     *
     * After calling a channel will start a closing process:
     * - The channel state will transition to [WebRtc.DataChannelState.CLOSED]
     * - No more messages can be sent through this channel
     * - The underlying message receiving channel will be closed
     * - Any pending send operations may fail
     * - A [DataChannelEvent.Closed] event will be emitted
     */
    abstract override fun close()
}
