/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

/**
 * An object containing Media Capture abstractions.
 * Media Capturing is platform- and implementation-specific,
 * so here we encapsulate only the essential parts for the WebRTC communication.
 * The links describe the standard browser API, though some platforms could have different behaviors.
 */
public object WebRTCMedia {

    /**
     * Constraints for video tracks.
     *
     * @property width The width of the video in pixels.
     * @property height The height of the video in pixels.
     * @property frameRate The frame rate of the video.
     * @property aspectRatio The aspect ratio of the video.
     * @property facingMode The camera-facing mode.
     * @property resizeMode The resize mode for the video.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/MediaTrackConstraints">MDN MediaTrackConstraints</a>
     */
    public data class VideoTrackConstraints(
        val width: Int? = null,
        val height: Int? = null,
        val frameRate: Int? = null,
        val aspectRatio: Double? = null,
        val facingMode: FacingMode? = null,
        val resizeMode: ResizeMode? = null,
    )

    /**
     * Constraints for audio tracks.
     *
     * @property volume The volume of the audio.
     * @property sampleRate The sample rate of the audio in Hz.
     * @property sampleSize The sample size of the audio in bits.
     * @property echoCancellation Whether echo cancellation is enabled.
     * @property autoGainControl Whether automatic gain control is enabled.
     * @property noiseSuppression Whether noise suppression is enabled.
     * @property latency The latency of the audio in seconds.
     * @property channelCount The number of audio channels.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/MediaTrackConstraints">MDN MediaTrackConstraints</a>
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
     * @property id The unique identifier of the track.
     * @property kind The type of the track (audio or video).
     * @property enabled Whether the track is enabled.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/MediaStreamTrack">MDN MediaStreamTrack</a>
     */
    public interface Track : AutoCloseable {
        public val id: String
        public val kind: TrackType
        public val enabled: Boolean

        /**
         * Enables or disables the track.
         */
        public fun enable(enabled: Boolean)

        /**
         * Gets the native platform-specific object representing this track.
         */
        public fun getNative(): Any
    }

    /**
     * Enum representing the type of media track.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/MediaStreamTrack/kind">MDN MediaStreamTrack.kind</a>
     */
    public enum class TrackType {
        AUDIO,
        VIDEO,
    }

    /**
     * Interface representing a video track.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/MediaStreamTrack">MDN MediaStreamTrack</a>
     */
    public interface VideoTrack : Track

    /**
     * Interface representing an audio track.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/MediaStreamTrack">MDN MediaStreamTrack</a>
     */
    public interface AudioTrack : Track

    /**
     * Enum representing the facing mode of a camera.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/MediaTrackConstraints/facingMode">MDN facingMode</a>
     */
    public enum class FacingMode {
        /**
         * Front-facing camera (selfie camera).
         */
        USER,

        /**
         * Back-facing camera (environment camera).
         */
        ENVIRONMENT,

        /**
         * Camera facing to the left of the user.
         */
        LEFT,

        /**
         * Camera facing to the right of the user.
         */
        RIGHT
    }

    /**
     * Enum representing the resize mode for video tracks.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/MediaTrackConstraints/resizeMode">MDN resizeMode</a>
     */
    public enum class ResizeMode {
        NONE,
        CROP_AND_SCALE
    }

    /**
     * Exception thrown when media permissions are not granted.
     */
    public class PermissionException(mediaType: String?) :
        RuntimeException("You should grant $mediaType permission for this operation to work.")

    /**
     * Exception thrown when there is an issue with a media device.
     */
    public class DeviceException(message: String?, cause: Throwable? = null) : RuntimeException(message, cause)
}
