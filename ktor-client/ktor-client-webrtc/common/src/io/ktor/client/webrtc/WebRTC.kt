/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import kotlinx.serialization.Serializable

public object WebRTC {
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

    public data class Stats(
        val id: String,
        val type: String,
        val timestamp: Long,
        val props: Map<String, Any?>,
    )

    public data class IceServer(
        val urls: String,
        val username: String? = null,
        val credential: String? = null
    )

    @Serializable
    public data class IceCandidate(
        public val candidate: String,
        public val sdpMid: String,
        public val sdpMLineIndex: Int
    )

    @Serializable
    public data class SessionDescription(
        val type: SessionDescriptionType,
        val sdp: String
    )

    @Serializable
    public enum class SessionDescriptionType {
        OFFER,
        ANSWER,
        PROVISIONAL_ANSWER,
        ROLLBACK
    }

    public interface DtmfSender {
        public val toneBuffer: String
        public val canInsertDTMF: Boolean
        public fun insertDTMF(tones: String, duration: Int, interToneGap: Int)
    }

    public data class RtpHeaderExtensionParameters(
        val id: Int,
        val uri: String,
        val encrypted: Boolean
    )

    public interface RtpParameters {
        public val transactionId: String
        public val codecs: Iterable<Any>
        public val rtcp: Any

        public val headerExtensions: Iterable<RtpHeaderExtensionParameters>
        public val degradationPreference: DegradationPreference
        public val encodings: Iterable<Any>
    }

    public enum class DegradationPreference {
        DISABLED,
        MAINTAIN_FRAMERATE,
        MAINTAIN_RESOLUTION,
        BALANCED
    }

    public interface RtpSender {
        public val dtmf: DtmfSender?
        public val track: WebRTCMedia.Track?

        public suspend fun replaceTrack(withTrack: WebRTCMedia.Track?)
        public suspend fun getParameters(): RtpParameters
        public suspend fun setParameters(parameters: RtpParameters)
    }

    public class SdpException(message: String?, cause: Throwable? = null) : RuntimeException(message, cause)

    public class IceException(message: String?, cause: Throwable? = null) : RuntimeException(message, cause)
}

public object WebRTCMedia {
    public interface Source

    public interface AudioSource : Source

    public interface VideoSource : Source {
        public val isScreencast: Boolean
    }

    public data class VideoTrackConstraints(
        val width: Int? = null,
        val height: Int? = null,
        val frameRate: Int? = null,
        val aspectRatio: Double? = null,
        val facingMode: FacingMode? = null,
        val resizeMode: ResizeMode? = null,
    )

    public data class AudioTrackConstraints(
        var volume: Double? = null,
        var sampleRate: Int? = null,
        var sampleSize: Int? = null,
        var echoCancellation: Boolean? = null,
        var autoGainControl: Boolean? = null,
        var noiseSuppression: Boolean? = null,
        var latency: Double? = null,
        var channelCount: Int? = null,
    )

    public interface Track : AutoCloseable {
        public val id: String
        public val kind: TrackType
        public val enabled: Boolean

        public fun enable(enabled: Boolean)
        override fun close()
    }

    public enum class TrackType {
        AUDIO,
        VIDEO,
    }

    public interface VideoTrack : Track
    public interface AudioTrack : Track

    public enum class FacingMode {
        USER,
        ENVIRONMENT,
        LEFT,
        RIGHT
    }

    public enum class ResizeMode {
        NONE,
        CROP_AND_SCALE
    }

    public class PermissionException(mediaType: String?) :
        RuntimeException("You should grant $mediaType permission for this operation to work.")

    public class DeviceException(message: String?, cause: Throwable? = null) : RuntimeException(message, cause)
}
