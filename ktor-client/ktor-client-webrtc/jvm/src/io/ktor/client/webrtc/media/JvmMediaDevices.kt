/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.media

import dev.onvoid.webrtc.PeerConnectionFactory
import dev.onvoid.webrtc.media.audio.AudioDeviceModuleBase
import dev.onvoid.webrtc.media.audio.AudioTrackSource
import dev.onvoid.webrtc.media.video.VideoTrackSource
import io.ktor.client.webrtc.*
import kotlinx.atomicfu.atomic
import java.util.*

/**
 * Represents a video capture device that can start and stop video recording.
 * Implementations should properly handle resource cleanup.
 */
public interface VideoCapturer : AutoCloseable {
    /**
     * The underlying video track source that provides video frames.
     */
    public val source: VideoTrackSource

    /**
     * Starts video capture from this device.
     * This method should be called before the video source can produce frames.
     */
    public fun start()

    /**
     * Stops video capture from this device.
     * After calling this method, the video source will no longer produce frames.
     */
    public fun stop()
}

/**
 * Factory for creating video capture devices based on the provided video track constraints.
 */
public interface VideoFactory {
    public fun createVideoCapturer(
        constraints: WebRtcMedia.VideoTrackConstraints
    ): VideoCapturer
}

/**
 * Factory for creating audio-related components including audio sources and peer connection factory.
 *
 * This interface provides access to the audio device module and peer connection factory
 * (which are tightly bound in the `dev.onvoid.webrtc` library`).
 */
public interface AudioFactory : AutoCloseable {
    /**
     * The audio device module responsible for audio input/output operations.
     */
    public val audioModule: AudioDeviceModuleBase

    /**
     * The peer connection factory used for creating WebRTC peer connections and tracks.
     */
    public val peerConnectionFactory: PeerConnectionFactory

    public fun createAudioSource(
        constraints: WebRtcMedia.AudioTrackConstraints
    ): AudioTrackSource
}

/**
 * JVM implementation of MediaTrackFactory based `dev.onvoid.webrtc.media`.
 *
 * @param audioFactory The factory for creating audio-related components. Defaults to [DefaultAudioFactory].
 * @param videoFactory The factory for creating video capture devices. Defaults to [CameraVideoFactory].
 */
public class JvmMediaDevices(
    private val audioFactory: AudioFactory = DefaultAudioFactory(),
    private val videoFactory: VideoFactory = CameraVideoFactory(),
) : MediaTrackFactory, AutoCloseable {
    private val recordingStarted = atomic(false)

    /**
     * The peer connection factory used for creating WebRTC components.
     * This is provided by the configured [audioFactory].
     */
    public val peerConnectionFactory: PeerConnectionFactory
        get() = audioFactory.peerConnectionFactory

    /**
     * The audio device module responsible for audio input/output operations.
     * This is provided by the configured [audioFactory].
     */
    public val audioModule: AudioDeviceModuleBase
        get() = audioFactory.audioModule

    private fun ensureRecordingAudio() {
        if (!recordingStarted.compareAndSet(expect = false, update = true)) {
            return
        }
        audioModule.initRecording()
        audioModule.startRecording()
    }

    /**
     * Ensures the audio recording device is started when the first audio track is created and stopped
     * when the `close()` method is called, though you can stop it manually by calling `audioModule.stopRecording()`.
     * Audio playout initialization should be done separately.
     */
    override suspend fun createAudioTrack(constraints: WebRtcMedia.AudioTrackConstraints): WebRtcMedia.AudioTrack {
        ensureRecordingAudio()
        val label = "jvm-webrtc-audio-${UUID.randomUUID()}"
        val source = audioFactory.createAudioSource(constraints)
        val nativeTrack = peerConnectionFactory.createAudioTrack(label, source)
        return JvmAudioTrack(nativeTrack)
    }

    /**
     * Creates a video capturer using the configured video factory,
     * starts video capture, and creates a video track. The video capturer lifecycle
     * is automatically managed - it will be stopped and closed when the track is disposed.
     *
     * **Note**: Some video constraints are not supported on the JVM platform:
     * - `facingMode` is not supported
     * - `aspectRatio` is not supported
     * - `resizeMode` is not supported
     */
    override suspend fun createVideoTrack(constraints: WebRtcMedia.VideoTrackConstraints): WebRtcMedia.VideoTrack {
        require(constraints.facingMode == null) {
            "Facing mode filter is not supported on Jvm platform."
        }
        require(constraints.aspectRatio == null) {
            "Aspect ratio filter is not supported on Jvm platform."
        }
        require(constraints.resizeMode == null) {
            "Resize mode filter is not supported on Jvm platform."
        }

        val label = "jvm-webrtc-video-${UUID.randomUUID()}"
        val capturer = videoFactory.createVideoCapturer(constraints).apply { start() }
        val nativeTrack = peerConnectionFactory.createVideoTrack(label, capturer.source)
        return JvmVideoTrack(nativeTrack) {
            capturer.stop()
            nativeTrack.dispose()
            capturer.close()
        }
    }

    override fun close() {
        audioModule.stopRecording()
        audioFactory.close()
    }
}
