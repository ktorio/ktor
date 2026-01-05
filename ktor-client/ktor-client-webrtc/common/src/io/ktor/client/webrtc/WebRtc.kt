/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import kotlinx.serialization.Serializable

/**
 * An object containing WebRTC protocol entities and abstractions.
 * Provides the core types and interfaces needed for WebRTC peer-to-peer communication.
 *
 * @see [MDN WebRTC API](https://developer.mozilla.org/en-US/docs/Web/API/WebRTC_API)
 */
public object WebRtc {
    /**
     * Represents the state of the ICE connection.
     *
     * @see [MDN iceConnectionState](https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection/iceConnectionState)
     */
    public enum class IceConnectionState {
        NEW,
        CHECKING,
        CONNECTED,
        COMPLETED,
        FAILED,
        DISCONNECTED,
        CLOSED;

        public fun isSuccessful(): Boolean = this == COMPLETED || this == CONNECTED
    }

    /**
     * Represents the state of the ICE gathering process.
     *
     * @see [MDN iceGatheringState](https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection/iceGatheringState)
     */
    public enum class IceGatheringState {
        NEW,
        GATHERING,
        COMPLETE
    }

    /**
     * Represents the state of the peer connection.
     *
     * @see [MDN connectionState](https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection/connectionState)
     */
    public enum class ConnectionState {
        NEW,
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
        FAILED,
        CLOSED
    }

    /**
     * Represents the signaling state of the peer connection.
     *
     * @see [MDN signalingState](https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection/signalingState)
     */
    public enum class SignalingState {
        STABLE,
        CLOSED,
        HAVE_LOCAL_OFFER,
        HAVE_LOCAL_PROVISIONAL_ANSWER,
        HAVE_REMOTE_OFFER,
        HAVE_REMOTE_PROVISIONAL_ANSWER,
    }

    /**
     * Represents statistics about the WebRtc connection.
     *
     * @property id The unique identifier for this statistics object.
     * @property type The type of the statistics object.
     * @property timestamp The timestamp when these statistics were collected.
     * @property props Additional properties specific to the statistics type.
     *
     * @see [MDN RTCStats](https://developer.mozilla.org/en-US/docs/Web/API/RTCStats)
     */
    public data class Stats(
        val id: String,
        val type: String,
        val timestamp: Long,
        val props: Map<String, Any?>,
    )

    /**
     * Represents an ICE server configuration for WebRtc connections.
     *
     * @property urls The URLs of the ICE server.
     * @property username Optional username for the ICE server.
     * @property credential Optional credential for the ICE server.
     *
     * @see [MDN RTCIceServer](https://developer.mozilla.org/en-US/docs/Web/API/RTCIceServer)
     */
    public data class IceServer(
        val urls: List<String>,
        val username: String? = null,
        val credential: String? = null
    ) {
        public constructor(url: String, username: String? = null, credential: String? = null) : this(
            urls = listOf(url),
            username,
            credential
        )
    }

    /**
     * Represents the bundle policy for media negotiation.
     *
     * @see [MDN bundlePolicy](https://developer.mozilla.org/en-US/docs/Web/API/RTCConfiguration/bundlePolicy)
     */
    public enum class BundlePolicy {
        MAX_BUNDLE,
        BALANCED,
        MAX_COMPAT
    }

    /**
     * Represents the ICE candidate policy for the connection.
     *
     * @see [MDN iceTransportPolicy](https://developer.mozilla.org/en-US/docs/Web/API/RTCConfiguration/iceTransportPolicy)
     */
    public enum class IceTransportPolicy {
        ALL,
        RELAY
    }

    /**
     * Represents the RTCP mux policy for the connection.
     *
     * @see [MDN rtcpMuxPolicy](https://developer.mozilla.org/en-US/docs/Web/API/RTCConfiguration/rtcpMuxPolicy)
     */
    public enum class RtcpMuxPolicy {
        NEGOTIATE,
        REQUIRE
    }

    /**
     * Represents an ICE candidate in the WebRtc connection process.
     *
     * @property candidate The ICE candidate string in SDP format.
     * @property sdpMid The media stream identifier for the candidate.
     * @property sdpMLineIndex The index of the media description in the SDP.
     *
     * @see [MDN RTCIceCandidate](https://developer.mozilla.org/en-US/docs/Web/API/RTCIceCandidate)
     */
    @Serializable
    public data class IceCandidate(
        public val candidate: String,
        public val sdpMid: String,
        public val sdpMLineIndex: Int
    )

    /**
     * Represents a session description in the WebRtc connection process.
     *
     * @property type The type of the session description.
     * @property sdp The SDP (Session Description Protocol) string.
     *
     * @see [MDN RTCSessionDescription](https://developer.mozilla.org/en-US/docs/Web/API/RTCSessionDescription)
     */
    @Serializable
    public data class SessionDescription(
        val type: SessionDescriptionType,
        val sdp: String
    )

    /**
     * Represents the type of session description in the WebRtc connection process.
     *
     * @see [MDN RTCSessionDescription](https://developer.mozilla.org/en-US/docs/Web/API/RTCSessionDescription)
     */
    @Serializable
    public enum class SessionDescriptionType {
        OFFER,
        ANSWER,
        PROVISIONAL_ANSWER,
        ROLLBACK
    }

    /**
     * Interface for sending DTMF (Dual-Tone Multi-Frequency) tones.
     *
     * @property toneBuffer The tone buffer containing the tones to be played.
     * @property canInsertDtmf Whether DTMF tones can be inserted.
     *
     * @see [MDN RTCDTMFSender](https://developer.mozilla.org/en-US/docs/Web/API/RTCDTMFSender)
     */
    public interface DtmfSender {
        public val toneBuffer: String
        public val canInsertDtmf: Boolean
        public fun insertDtmf(tones: String, duration: Int, interToneGap: Int)
    }

    /**
     * Represents parameters for RTP header extensions.
     *
     * @property id The ID of the header extension.
     * @property uri The URI of the header extension.
     * @property encrypted Whether the header extension is encrypted.
     *
     * @see [MDN RTCRtpHeaderExtension](https://developer.mozilla.org/en-US/docs/Web/API/RTCRtpHeaderExtension)
     */
    public data class RtpHeaderExtensionParameters(
        val id: Int,
        val uri: String,
        val encrypted: Boolean
    )

    /**
     * Interface representing parameters for RTP transmission.
     *
     * @property transactionId The transaction ID for these parameters.
     * @property codecs The codecs used for transmission.
     * @property rtcp The RTCP parameters.
     * @property headerExtensions The header extensions for the RTP packets.
     * @property degradationPreference The degradation preference for the media quality.
     * @property encodings The encoding parameters for the media.
     *
     * @see [MDN RTCRtpParameters](https://developer.mozilla.org/en-US/docs/Web/API/RTCRtpParameters)
     */
    public interface RtpParameters {
        public val transactionId: String
        public val codecs: Iterable<Any>
        public val rtcp: Any

        public val headerExtensions: Iterable<RtpHeaderExtensionParameters>
        public val degradationPreference: DegradationPreference
        public val encodings: Iterable<Any>
    }

    /**
     * Represents the degradation preference for media quality when bandwidth is constrained.
     *
     * @see [MDN degradationPreference](https://developer.mozilla.org/en-US/docs/Web/API/RTCRtpParameters/degradationPreference)
     */
    public enum class DegradationPreference {
        DISABLED,
        MAINTAIN_FRAMERATE,
        MAINTAIN_RESOLUTION,
        BALANCED
    }

    /**
     * Interface for sending RTP media.
     *
     * @property dtmf The DTMF sender associated with this RTP sender.
     * @property track The media track being sent.
     *
     * @see [MDN RTCRtpSender](https://developer.mozilla.org/en-US/docs/Web/API/RTCRtpSender)
     */
    public interface RtpSender {
        public val dtmf: DtmfSender?
        public val track: WebRtcMedia.Track?

        public suspend fun replaceTrack(withTrack: WebRtcMedia.Track?)
        public suspend fun getParameters(): RtpParameters
        public suspend fun setParameters(parameters: RtpParameters)
    }

    /**
     * Abstract class representing a network channel which can be used for bidirectional peer-to-peer transfers
     * of arbitrary data. Every data channel is associated with an [WebRtcPeerConnection].
     *
     * @see [MDN RTCDataChannel](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel)
     */
    public interface DataChannel : AutoCloseable {
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

            /**
             * Returns the text content of the message if it's a text message, otherwise returns null.
             */
            public fun textOrNull(): String? = (this as? Text)?.data

            /**
             * Returns the binary content of the message if it's a binary message, otherwise returns null.
             */
            public fun binaryOrNull(): ByteArray? = (this as? Binary)?.data

            /**
             * Returns the text content of the message if it's a text message, otherwise throws an exception.
             */
            public fun textOrThrow(): String =
                (this as? Text ?: error("Received a binary instead of string data.")).data

            /**
             * Returns the binary content of the message if it's a binary message, otherwise throws an exception.
             */
            public fun binaryOrThrow(): ByteArray =
                (this as? Binary ?: error("Received a string instead of binary data.")).data
        }

        /**
         * Represents the current state of a WebRTC data channel.
         *
         * @see [MDN RTCDataChannel.readyState](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/readyState)
         */
        public enum class State {
            CONNECTING,
            OPEN,
            CLOSING,
            CLOSED;

            public fun canSend(): Boolean = this == OPEN
        }

        /**
         * An ID number (between 0 and 65,534) which uniquely identifies the data channel.
         * It can be null when the data channel is created but not yet assigned an ID.
         *
         * @see [MDN RTCDataChannel.id](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/id)
         */
        public val id: Int?

        /**
         * A string containing a name describing the data channel.
         *
         * @see [MDN RTCDataChannel.label](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/label)
         */
        public val label: String

        /**
         * A state of the data channel's underlying data connection.
         *
         * @see [MDN RTCDataChannel.readyState](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/readyState)
         */
        public val state: State

        /**
         * A number of bytes of data currently queued to be sent over the data channel.
         *
         * @see [MDN RTCDataChannel.bufferedAmount](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/bufferedAmount)
         */
        public val bufferedAmount: Long

        /**
         * A number of queued outgoing data bytes below which the buffer is considered to be "low."
         * When the number of buffered outgoing bytes, as indicated by the bufferedAmount property,
         * falls to or below this value, a [DataChannelEvent.BufferedAmountLow] event is fired.
         * The default value is 0.
         *
         * @see [MDN RTCDataChannel.bufferedAmountLowThreshold](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/bufferedAmountLowThreshold)
         */
        public val bufferedAmountLowThreshold: Long

        /**
         * The maximum number of milliseconds that attempts to transfer a message may take in unreliable mode.
         *
         * @see [MDN RTCDataChannel.maxPacketLifeTime](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/maxPacketLifeTime)
         */
        public val maxPacketLifeTime: Int?

        /**
         * The maximum number of times the user agent should attempt to retransmit a message
         * which fails the first time in unreliable mode.
         *
         * @see [MDN RTCDataChannel.maxRetransmits](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/maxRetransmits)
         */
        public val maxRetransmits: Int?

        /**
         * Indicates whether the data channel was negotiated by the application or the WebRTC layer.
         *
         * @see [MDN RTCDataChannel.negotiated](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/negotiated)
         */
        public val negotiated: Boolean

        /**
         * Indicates whether messages sent on the data channel are required to arrive at their destination
         * in the same order in which they were sent, or if they're allowed to arrive out-of-order.
         *
         * @see [MDN RTCDataChannel.ordered](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/ordered)
         */
        public val ordered: Boolean

        /**
         * The name of the sub-protocol being used on the data channel, if any; otherwise, the empty string.
         *
         * @see [MDN RTCDataChannel.protocol](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/protocol)
         */
        public val protocol: String

        /**
         * Sets the threshold for the buffered amount of data below which the buffer is considered to be "low."
         * When the buffered amount falls to or below this value, a [DataChannelEvent.BufferedAmountLow] event is fired.
         */
        public fun setBufferedAmountLowThreshold(threshold: Long)

        /**
         * Sends a text message through the data channel.
         *
         * @param text The text message to send.
         * @see [MDN RTCDataChannel.send()](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/send)
         */
        public suspend fun send(text: String)

        /**
         * Sends binary data through the data channel.
         *
         * @param bytes The binary data to send.
         * @see [MDN RTCDataChannel.send()](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel/send)
         */
        public suspend fun send(bytes: ByteArray)

        /**
         * Suspends until a message is available in the data channel and returns it.
         *
         * This method will suspend the current coroutine until a message is received.
         * The message can be either text or binary data.
         */
        public suspend fun receive(): Message

        /**
         * Receives a binary message from the data channel.
         *
         * This method suspends until a binary message is available. If the next message
         * in the channel is a text message instead of binary data, this method will throw an error.
         */
        public suspend fun receiveBinary(): ByteArray

        /**
         * Receives a text message from the data channel.
         *
         * This method suspends until a text message is available. If the next message
         * in the channel is binary data instead of text, this method will throw an error.
         */
        public suspend fun receiveText(): String

        /**
         * Immediately returns a message from the data channel or null if no message is available.
         *
         * This method does not suspend and returns immediately. If a message is available,
         * it is returned; otherwise, null is returned.
         */
        public fun tryReceive(): Message?

        /**
         * Immediately returns binary data from the data channel or null if no binary message is available.
         *
         * This method does not suspend and returns immediately. If a binary message is available,
         * its data is returned; otherwise, null is returned. If the next message is a text message,
         * null is returned.
         */
        public fun tryReceiveBinary(): ByteArray?

        /**
         * Immediately returns text data from the data channel or null if no text message is available.
         *
         * This method does not suspend and returns immediately. If a text message is available,
         * its content is returned; otherwise, null is returned. If the next message is a binary message,
         * null is returned.
         */
        public fun tryReceiveText(): String?

        /**
         * Closes the data channel transport. The underlying message receiving channel will be closed.
         *
         * After calling a channel will start a closing process:
         * - The channel state will transition to [WebRtc.DataChannel.State.CLOSED]
         * - No more messages can be sent through this channel
         * - The underlying message receiving channel will be closed
         * - Any pending send operations may fail
         * - A [DataChannelEvent.Closed] event will be emitted
         */
        public fun closeTransport()

        /**
         * Closes the data channel and releases all associated resources.
         * Automatically invokes `closeTransport`.
         * Accessing the channel after this operation could throw an exception.
         */
        override fun close() {
            closeTransport()
        }
    }

    /**
     * This exception indicates problems with creating, parsing, or validating SDP descriptions
     * during the WebRTC connection establishment process.
     */
    public class SdpException(message: String?, cause: Throwable? = null) : RuntimeException(message, cause)

    /**
     * This exception indicates problems with ICE candidates gathering, processing, or connectivity
     * during the WebRTC peer connection establishment.
     */
    public class IceException(message: String?, cause: Throwable? = null) : RuntimeException(message, cause)
}
