/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:OptIn(ExperimentalForeignApi::class)

package io.ktor.client.webrtc.media

import WebRTC.*
import io.ktor.client.webrtc.*
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSUUID.Companion.UUID

public interface Capturer {
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

internal expect fun defaultVideoCapturerFactory(): VideoCapturerFactory

public class IosMediaDevices(
    private val videoCapturerFactory: VideoCapturerFactory = defaultVideoCapturerFactory()
) : MediaTrackFactory {

    public val peerConnectionFactory: RTCPeerConnectionFactory by lazy {
        if (!sslInitialized) {
            RTCInitializeSSL()
            sslInitialized = true
        }
        RTCPeerConnectionFactory(
            encoderFactory = RTCDefaultVideoEncoderFactory(),
            decoderFactory = RTCDefaultVideoDecoderFactory()
        )
    }

    override suspend fun createAudioTrack(constraints: WebRtcMedia.AudioTrackConstraints): WebRtcMedia.AudioTrack {
        if (constraints.sampleSize != null) {
            TODO("Sample size is not supported yet for Ios. You can provide custom MediaTrackFactory")
        }
        if (constraints.latency != null) {
            TODO("Latency is not supported yet for Ios. You can provide custom MediaTrackFactory")
        }
        if (constraints.channelCount != null) {
            TODO("Channel count is not supported yet for Android. You can provide custom MediaTrackFactory")
        }
        val mediaConstraints = RTCMediaConstraints(
            mandatoryConstraints = mapOf(
                "googEchoCancellation" to (constraints.echoCancellation != false).toString(),
                "googAutoGainControl" to (constraints.autoGainControl != false).toString(),
                "googNoiseSuppression" to (constraints.noiseSuppression != false).toString()
            ),
            optionalConstraints = mapOf("DtlsSrtpKeyAgreement" to "true")
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
        return IosVideoTrack(nativeTrack = track, onDispose = { videoCapturer.stopCapture() })
    }

    private companion object {
        var sslInitialized = false
    }
}
