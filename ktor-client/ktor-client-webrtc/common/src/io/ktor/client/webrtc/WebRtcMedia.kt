/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

/**
 * An object containing Media Capture abstractions.
 * Media Capturing is platform- and implementation-specific,
 * so here we encapsulate only the essential parts for the WebRtc communication.
 * The links describe the standard browser API, though some platforms could have different behaviors.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcMedia)
 */
public object WebRtcMedia {

    /**
     * Constraints for video tracks.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcMedia.VideoTrackConstraints)
     *
     * @property width The width of the video in pixels.
     * @property height The height of the video in pixels.
     * @property frameRate The frame rate of the video.
     * @property aspectRatio The aspect ratio of the video.
     * @property facingMode The camera-facing mode.
     * @property resizeMode The resize mode for the video. Not supported for Android.
     *
     * @see [MDN MediaTrackConstraints](https://developer.mozilla.org/en-US/docs/Web/API/MediaTrackConstraints)
     */
    public data class VideoTrackConstraints(
        var width: Int? = null,
        var height: Int? = null,
        var frameRate: Int? = null,
        var aspectRatio: Double? = null,
        var facingMode: FacingMode? = null,
        var resizeMode: ResizeMode? = null,
    )

    /**
     * Constraints for audio tracks.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcMedia.AudioTrackConstraints)
     *
     * @property volume The volume of the audio.
     * @property sampleRate The sample rate of the audio in Hz.
     * @property sampleSize The sample size of the audio in bits. Not supported for Android.
     * @property echoCancellation Whether echo cancellation is enabled.
     * @property autoGainControl Whether automatic gain control is enabled.
     * @property noiseSuppression Whether noise suppression is enabled.
     * @property latency The latency of the audio in seconds. Not supported for Android.
     * @property channelCount The number of audio channels. Not supported for Android.
     *
     * @see [MDN MediaTrackConstraints](https://developer.mozilla.org/en-US/docs/Web/API/MediaTrackConstraints)
     */
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

    /**
     * Interface representing a media track.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcMedia.Track)
     *
     * @property id The unique identifier of the track.
     * @property kind The type of the track (audio or video).
     * @property enabled Whether the track is enabled.
     *
     * @see [MDN MediaStreamTrack](https://developer.mozilla.org/en-US/docs/Web/API/MediaStreamTrack)
     */
    public interface Track : AutoCloseable {
        public val id: String
        public val kind: TrackType
        public val enabled: Boolean

        /**
         * Enables or disables the track.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcMedia.Track.enable)
         */
        public fun enable(enabled: Boolean)
    }

    /**
     * Enum representing the type of media track.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcMedia.TrackType)
     *
     * @see [MDN MediaStreamTrack.kind](https://developer.mozilla.org/en-US/docs/Web/API/MediaStreamTrack/kind)
     */
    public enum class TrackType {
        AUDIO,
        VIDEO,
    }

    /**
     * Interface representing a video track.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcMedia.VideoTrack)
     *
     * @see [MDN MediaStreamTrack](https://developer.mozilla.org/en-US/docs/Web/API/MediaStreamTrack)
     */
    public interface VideoTrack : Track

    /**
     * Interface representing an audio track.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcMedia.AudioTrack)
     *
     * @see [MDN MediaStreamTrack](https://developer.mozilla.org/en-US/docs/Web/API/MediaStreamTrack)
     */
    public interface AudioTrack : Track

    /**
     * Enum representing the facing mode of a camera.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcMedia.FacingMode)
     *
     * @see [MDN facingMode](https://developer.mozilla.org/en-US/docs/Web/API/MediaTrackConstraints/facingMode)
     */
    public enum class FacingMode {
        /**
         * Front-facing camera (selfie camera).
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcMedia.FacingMode.USER)
         */
        USER,

        /**
         * Back-facing camera (environment camera).
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcMedia.FacingMode.ENVIRONMENT)
         */
        ENVIRONMENT,

        /**
         * Camera facing to the left of the user.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcMedia.FacingMode.LEFT)
         */
        LEFT,

        /**
         * Camera facing to the right of the user.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcMedia.FacingMode.RIGHT)
         */
        RIGHT
    }

    /**
     * Enum representing the resize mode for video tracks.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcMedia.ResizeMode)
     *
     * @see [MDN resizeMode](https://developer.mozilla.org/en-US/docs/Web/API/MediaTrackConstraints/resizeMode)
     */
    public enum class ResizeMode {
        NONE,
        CROP_AND_SCALE
    }

    /**
     * Exception thrown when media permissions are not granted.
     *
     * This exception is typically thrown when the application cannot access microphone,
     * camera, or other media devices due to missing user permissions.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcMedia.PermissionException)
     */
    public class PermissionException(mediaType: String?) :
        RuntimeException("You should grant $mediaType permission for this operation to work.")

    /**
     * Exception thrown when there is an issue with a media device.
     *
     * This exception indicates problems with initializing, accessing, or using media devices
     * such as cameras or microphones, typically due to hardware or driver issues rather than permissions.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.WebRtcMedia.DeviceException)
     */
    public class DeviceException(message: String?, cause: Throwable? = null) : RuntimeException(message, cause)
}

/**
 * Factory interface for creating audio and video media tracks.
 *
 * This interface abstracts the platform-specific details of creating media tracks,
 * allowing clients to request audio and video tracks without dealing with platform differences.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.MediaTrackFactory)
 */
public interface MediaTrackFactory {
    public suspend fun createAudioTrack(constraints: WebRtcMedia.AudioTrackConstraints): WebRtcMedia.AudioTrack
    public suspend fun createVideoTrack(constraints: WebRtcMedia.VideoTrackConstraints): WebRtcMedia.VideoTrack

    public suspend fun createAudioTrack(
        constraints: WebRtcMedia.AudioTrackConstraints.() -> Unit = {}
    ): WebRtcMedia.AudioTrack =
        createAudioTrack(constraints = WebRtcMedia.AudioTrackConstraints().apply(constraints))

    public suspend fun createVideoTrack(
        constraints: WebRtcMedia.VideoTrackConstraints.() -> Unit = {}
    ): WebRtcMedia.VideoTrack =
        createVideoTrack(constraints = WebRtcMedia.VideoTrackConstraints().apply(constraints))
}
