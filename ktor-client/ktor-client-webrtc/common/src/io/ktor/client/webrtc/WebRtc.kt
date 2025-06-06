/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import kotlinx.serialization.Serializable

/**
 * An object containing WebRtc protocol entities and abstractions.
 * Provides the core types and interfaces needed for WebRtc peer-to-peer communication.
 *
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/WebRtc_API">MDN WebRtc API</a>
 */
public object WebRtc {
    /**
     * Represents the state of the ICE connection.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection/iceConnectionState">MDN iceConnectionState</a>
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
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection/iceGatheringState">MDN iceGatheringState</a>
     */
    public enum class IceGatheringState {
        NEW,
        GATHERING,
        COMPLETE
    }

    /**
     * Represents the state of the peer connection.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection/connectionState">MDN connectionState</a>
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
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection/signalingState">MDN signalingState</a>
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
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/RTCStats">MDN RTCStats</a>
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
     * @property urls The URL(s) of the ICE server.
     * @property username Optional username for the ICE server.
     * @property credential Optional credential for the ICE server.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/RTCIceServer">MDN RTCIceServer</a>
     */
    public data class IceServer(
        val urls: String,
        val username: String? = null,
        val credential: String? = null
    )

    /**
     * Represents the bundle policy for media negotiation.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/RTCConfiguration/bundlePolicy">MDN bundlePolicy</a>
     */
    public enum class BundlePolicy {
        MAX_BUNDLE,
        BALANCED,
        MAX_COMPAT
    }

    /**
     * Represents the ICE candidate policy for the connection.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/RTCConfiguration/iceTransportPolicy">MDN iceTransportPolicy</a>
     */
    public enum class IceTransportPolicy {
        ALL,
        RELAY
    }

    /**
     * Represents the RTCP mux policy for the connection.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/RTCConfiguration/rtcpMuxPolicy">MDN rtcpMuxPolicy</a>
     */
    public enum class RTCPMuxPolicy {
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
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/RTCIceCandidate">MDN RTCIceCandidate</a>
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
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/RTCSessionDescription">MDN RTCSessionDescription</a>
     */
    @Serializable
    public data class SessionDescription(
        val type: SessionDescriptionType,
        val sdp: String
    )

    /**
     * Represents the type of session description in the WebRtc connection process.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/RTCSessionDescription">MDN RTCSessionDescription</a>
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
     * @property canInsertDTMF Whether DTMF tones can be inserted.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/RTCDTMFSender">MDN RTCDTMFSender</a>
     */
    public interface DtmfSender {
        public val toneBuffer: String
        public val canInsertDTMF: Boolean
        public fun insertDTMF(tones: String, duration: Int, interToneGap: Int)

        /**
         * @return Native platform-specific object representing this DtmfSender.
         */
        public fun <T> getNative(): T
    }

    /**
     * Represents parameters for RTP header extensions.
     *
     * @property id The ID of the header extension.
     * @property uri The URI of the header extension.
     * @property encrypted Whether the header extension is encrypted.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/RTCRtpHeaderExtension">MDN RTCRtpHeaderExtension</a>
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
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/RTCRtpParameters">MDN RTCRtpParameters</a>
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
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/RTCRtpParameters/degradationPreference">MDN degradationPreference</a>
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
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/RTCRtpSender">MDN RTCRtpSender</a>
     */
    public interface RtpSender {
        public val dtmf: DtmfSender?
        public val track: WebRtcMedia.Track?

        public suspend fun replaceTrack(withTrack: WebRtcMedia.Track?)
        public suspend fun getParameters(): RtpParameters
        public suspend fun setParameters(parameters: RtpParameters)

        /**
         * @return Native platform-specific object representing this DtmfSender.
         */
        public fun <T> getNative(): T
    }

    /**
     * Exception thrown when there is an issue with Session Description Protocol (SDP) processing.
     */
    public class SdpException(message: String?, cause: Throwable? = null) : RuntimeException(message, cause)

    /**
     * Exception thrown when there is an issue with Interactive Connectivity Establishment (ICE) processing.
     */
    public class IceException(message: String?, cause: Throwable? = null) : RuntimeException(message, cause)
}
