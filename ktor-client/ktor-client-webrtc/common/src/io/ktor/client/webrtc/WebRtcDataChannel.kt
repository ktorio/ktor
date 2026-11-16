/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.utils.io.*
import kotlinx.coroutines.channels.*
import kotlin.time.Duration

/**
 * The behavior of a [WebRtcDataChannel] when its receive buffer is full.
 *
 * Incoming messages are delivered by the underlying WebRTC transport, which can't be paused,
 * so a full buffer can't suspend the remote sender.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.DataChannelBufferOverflow)
 */
public interface DataChannelBufferOverflow {
    public companion object {
        /**
         * Fail the data channel: the receive buffer is closed with a [WebRtc.IOException] cause,
         * a [DataChannelEvent.Error] is emitted, and the transport is closed.
         * Messages that were already buffered can still be received.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.DataChannelBufferOverflow.Companion.Close)
         */
        public val Close: DataChannelBufferOverflow = BuiltInDataChannelBufferOverflow(BufferOverflow.SUSPEND)

        /**
         * Drop the oldest buffered message to make room for the new one. The data channel stays open.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.DataChannelBufferOverflow.Companion.DropOldest)
         */
        public val DropOldest: DataChannelBufferOverflow = BuiltInDataChannelBufferOverflow(BufferOverflow.DROP_OLDEST)

        /**
         * Drop the new message and keep the buffered ones. The data channel stays open.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.DataChannelBufferOverflow.Companion.DropLatest)
         */
        public val DropLatest: DataChannelBufferOverflow = BuiltInDataChannelBufferOverflow(BufferOverflow.DROP_LATEST)
    }
}

private class BuiltInDataChannelBufferOverflow(
    val channelOverflow: BufferOverflow
) : DataChannelBufferOverflow

private val DataChannelBufferOverflow.channelOverflow: BufferOverflow
    get() {
        require(this is BuiltInDataChannelBufferOverflow) {
            "Unsupported DataChannelBufferOverflow $this."
        }
        return channelOverflow
    }

/**
 * Configuration options for the buffer of incoming messages in [WebRtcDataChannel].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.DataChannelReceiveOptions)
 */
@KtorDsl
public class DataChannelReceiveOptions {
    /**
     * The maximum number of received messages that are buffered until they are read.
     * Must be positive or [Channel.UNLIMITED].
     * Default is [DEFAULT_CAPACITY].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.DataChannelReceiveOptions.capacity)
     */
    public var capacity: Int = DEFAULT_CAPACITY

    /**
     * The behavior when the buffer is full.
     * Default is [DataChannelBufferOverflow.Close].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.DataChannelReceiveOptions.onOverflow)
     */
    public var onOverflow: DataChannelBufferOverflow = DataChannelBufferOverflow.Close
        set(value) {
            field = value
            suspendOnOverflow = false
        }

    /**
     * Deprecated equivalent of [onOverflow] that uses a coroutines [BufferOverflow].
     *
     * The remote peer can't be suspended, so [BufferOverflow.SUSPEND] requires [Channel.UNLIMITED] capacity.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.DataChannelReceiveOptions.onBufferOverflow)
     */
    @Deprecated("Use onOverflow instead. BufferOverflow.SUSPEND is allowed only with Channel.UNLIMITED capacity.")
    public var onBufferOverflow: BufferOverflow
        get() = onOverflow.channelOverflow
        set(value) {
            onOverflow = when (value) {
                BufferOverflow.SUSPEND -> DataChannelBufferOverflow.Close
                BufferOverflow.DROP_OLDEST -> DataChannelBufferOverflow.DropOldest
                BufferOverflow.DROP_LATEST -> DataChannelBufferOverflow.DropLatest
            }
            suspendOnOverflow = value == BufferOverflow.SUSPEND
        }

    internal var suspendOnOverflow: Boolean = false
        private set

    /**
     * An optional callback that is invoked when an element couldn't be delivered to its destination.
     * This can happen when the channel is closed or when an element is dropped due to buffer overflow.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.DataChannelReceiveOptions.onUndeliveredElement)
     */
    public var onUndeliveredElement: ((WebRtc.DataChannel.Message) -> Unit)? = null

    public companion object {
        /**
         * The default value of [capacity].
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.DataChannelReceiveOptions.Companion.DEFAULT_CAPACITY)
         */
        public const val DEFAULT_CAPACITY: Int = 64
    }
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

private fun Channel.Factory.from(options: DataChannelReceiveOptions): Channel<WebRtc.DataChannel.Message> {
    require(options.capacity > 0 || options.capacity == Channel.UNLIMITED) {
        "Data channel receive capacity must be positive or Channel.UNLIMITED, but was ${options.capacity}."
    }
    require(!options.suspendOnOverflow || options.capacity == Channel.UNLIMITED) {
        "BufferOverflow.SUSPEND isn't supported with the bounded receive capacity ${options.capacity} " +
            "because the remote peer can't be suspended. Set onOverflow or use Channel.UNLIMITED capacity."
    }
    return Channel(
        capacity = options.capacity,
        onBufferOverflow = options.onOverflow.channelOverflow,
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
@SubclassOptInRequired(InternalKtorWebRtcSubclassing::class)
public abstract class WebRtcDataChannel(
    protected val eventsEmitter: WebRtcConnectionEventsEmitter,
    receiveOptions: DataChannelReceiveOptions
) : WebRtc.DataChannel {
    private val receiveCapacity: Int = receiveOptions.capacity
    private val onUndeliveredMessage = receiveOptions.onUndeliveredElement
    private val receiveChannel: Channel<WebRtc.DataChannel.Message> = Channel.from(receiveOptions)

    /**
     * Returns an iterator over received messages.
     *
     * If the data channel is closed, the iterator keeps returning messages already stored in the buffer
     * and completes when no buffered messages remain.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcDataChannel.iterator)
     */
    public operator fun iterator(): ChannelIterator<WebRtc.DataChannel.Message> {
        return receiveChannel.iterator()
    }

    override suspend fun receive(): WebRtc.DataChannel.Message {
        val result = receiveChannel.receiveCatching()
        if (result.isSuccess) {
            return result.getOrThrow()
        }
        val message = "Data channel '$label' is closed and no more messages will be received."
        throw WebRtc.DataChannelClosedException(message, result.exceptionOrNull())
    }

    override suspend fun receiveBinary(): ByteArray = receive().binaryOrThrow()

    override suspend fun receiveText(): String = receive().textOrThrow()

    override fun tryReceive(): WebRtc.DataChannel.Message? = receiveChannel.tryReceive().getOrNull()

    override fun tryReceiveBinary(): ByteArray? = tryReceive()?.binaryOrNull()

    override fun tryReceiveText(): String? = tryReceive()?.textOrNull()

    /**
     * Adds a received [message] to the receive buffer.
     *
     * When the buffer is full and [DataChannelReceiveOptions.onOverflow] is [DataChannelBufferOverflow.Close],
     * the receive buffer is closed, a [DataChannelEvent.Error] is emitted, and the transport is closed.
     */
    protected fun emitMessage(message: WebRtc.DataChannel.Message): ChannelResult<Unit> {
        val result = receiveChannel.trySend(message)
        if (result.isSuccess || result.isClosed) {
            return result
        }
        onUndeliveredMessage?.invoke(message)
        val cause = WebRtc.IOException(
            "Receive buffer of data channel '$label' overflowed: more than $receiveCapacity messages were not read. " +
                "Read messages faster or configure DataChannelReceiveOptions."
        )
        if (receiveChannel.close(cause)) {
            val event = DataChannelEvent.Error(channel = this, reason = cause.message.orEmpty())
            eventsEmitter.emitDataChannelEvent(event)
            closeTransport()
        }
        return result
    }

    protected fun stopReceivingMessages() {
        receiveChannel.close()
    }
}

@InternalAPI
public suspend inline fun <R> withIOException(crossinline block: suspend () -> R): R {
    return try {
        block()
    } catch (cause: kotlinx.coroutines.CancellationException) {
        throw cause
    } catch (cause: WebRtc.IOException) {
        throw cause
    } catch (cause: Exception) {
        throw WebRtc.IOException("Error in WebRtcDataChannel operation", cause)
    }
}
