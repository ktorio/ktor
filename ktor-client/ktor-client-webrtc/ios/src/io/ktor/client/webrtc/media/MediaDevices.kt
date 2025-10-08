/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:OptIn(ExperimentalForeignApi::class)

package io.ktor.client.webrtc.media

import WebRTC.*
import io.ktor.client.webrtc.*
import io.ktor.utils.io.InternalAPI
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSUUID.Companion.UUID

/**
 * Represents a media capturer that controls the start and stop of media capture operations.
 *
 * This interface provides a unified abstraction for different types of media capture devices
 * such as cameras, synthetic video generators, or other media sources. Implementations handle
 * the underlying platform-specific capture mechanisms while providing a consistent API.
 *
 * ## Threading
 * Interface doesn't guarantee thread-safety. All operations should be performed on the same thread.
 *
 * ## Lifecycle and Idempotency
 * - [startCapture] begins media capture. Repeated calls are safe but will throw
 *   [IllegalArgumentException] if capture is already active.
 * - [stopCapture] ends media capture. Repeated calls are safe but will throw
 *   [IllegalStateException] if capture is not currently active.
 * - [isCapturing] reflects the current capture state and can be checked at any time.
 * - Resources are typically allocated during [startCapture] and released during [stopCapture].
 * - [close] ensures cleanup of all resources and stops capture if still active.
 */
public interface Capturer : AutoCloseable {
    public val isCapturing: Boolean
    public fun startCapture()
    public fun stopCapture()
}

public interface VideoCapturerFactory {
    /**
     * @param constraints Video track constraints specifying capture parameters
     * @param delegate Video capturer delegate for handling captured video frames
     * @return New Capturer instance configured with the provided parameters
     */
    public fun create(
        constraints: WebRtcMedia.VideoTrackConstraints,
        delegate: RTCVideoCapturerDelegateProtocol
    ): Capturer
}

/**
 * Creates a default video capturer factory for the current platform.
 *
 * This function returns a platform-specific implementation of [VideoCapturerFactory]
 * that provides access to the device's default video capture capabilities.
 *
 * ## Platform Behavior
 * - **iOS**: Returns a factory that creates camera-based video capturers
 * - **Simulator**: Returns a factory that creates synthetic video capturers
 */
@InternalAPI
public expect fun defaultVideoCapturerFactory(): VideoCapturerFactory

private const val DTLS_SRTP_KEY_AGREEMENT = "DtlsSrtpKeyAgreement"
private const val GOOG_ECHO_CANCELLATION = "googEchoCancellation"
private const val GOOG_AUTO_GAIN_CONTROL = "googAutoGainControl"
private const val GOOG_NOISE_SUPPRESSION = "googNoiseSuppression"

/**
 * iOS-specific implementation of [MediaTrackFactory] for WebRTC media operations.
 *
 * This class provides iOS-native WebRTC media track creation capabilities using the
 * WebRTC framework. It manages peer connection factory initialization, SSL setup,
 * and platform-specific media track creation.
 *
 * ## Lifecycle
 * - SSL initialization happens lazily when the first peer connection factory is accessed
 * - Video capturers are automatically started when video tracks are created
 *
 * @param videoCapturerFactory Factory for creating video capturer instances.
 *                            Defaults to platform-specific implementation
 */
@OptIn(InternalAPI::class)
public class IosMediaDevices(
    private val videoCapturerFactory: VideoCapturerFactory = defaultVideoCapturerFactory()
) : MediaTrackFactory {

    public val peerConnectionFactory: RTCPeerConnectionFactory by lazy {
        if (sslInitialized.compareAndSet(expect = false, update = true)) {
            RTCInitializeSSL()
        }
        RTCPeerConnectionFactory(
            encoderFactory = RTCDefaultVideoEncoderFactory(),
            decoderFactory = RTCDefaultVideoDecoderFactory()
        )
    }

    override suspend fun createAudioTrack(constraints: WebRtcMedia.AudioTrackConstraints): WebRtcMedia.AudioTrack {
        require(constraints.sampleSize == null) {
            "Sample size is not supported yet for iOS. You can provide custom MediaTrackFactory"
        }
        require(constraints.latency == null) {
            "Latency is not supported yet for iOS. You can provide custom MediaTrackFactory"
        }
        require(constraints.channelCount == null) {
            "Channel count is not supported yet for iOS. You can provide custom MediaTrackFactory"
        }
        val mediaConstraints = RTCMediaConstraints(
            mandatoryConstraints = mapOf(
                GOOG_ECHO_CANCELLATION to (constraints.echoCancellation != false).toString(),
                GOOG_AUTO_GAIN_CONTROL to (constraints.autoGainControl != false).toString(),
                GOOG_NOISE_SUPPRESSION to (constraints.noiseSuppression != false).toString()
            ),
            optionalConstraints = mapOf(DTLS_SRTP_KEY_AGREEMENT to "true")
        )
        val trackId = "ios-webrtc-audio-${UUID().UUIDString()}"
        val audioSource = peerConnectionFactory.audioSourceWithConstraints(mediaConstraints)
        audioSource.setVolume(constraints.volume ?: 1.0)
        val track = peerConnectionFactory.audioTrackWithSource(audioSource, trackId)
        return IosAudioTrack(nativeTrack = track)
    }

    override suspend fun createVideoTrack(constraints: WebRtcMedia.VideoTrackConstraints): WebRtcMedia.VideoTrack {
        require(constraints.resizeMode == null) {
            "Resize mode is not supported yet. You can provide custom MediaTrackFactory"
        }
        val videoSource = peerConnectionFactory.videoSource()
        val track = peerConnectionFactory.videoTrackWithSource(
            source = videoSource,
            trackId = "ios-webrtc-video-${UUID().UUIDString()}"
        )
        val videoCapturer = videoCapturerFactory.create(constraints, videoSource).apply {
            startCapture()
        }
        return IosVideoTrack(nativeTrack = track, onDispose = { videoCapturer.close() })
    }

    private companion object {
        var sslInitialized = atomic(false)
    }
}
