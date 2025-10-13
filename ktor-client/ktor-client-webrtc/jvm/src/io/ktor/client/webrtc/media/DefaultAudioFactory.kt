/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.media

import dev.onvoid.webrtc.PeerConnectionFactory
import dev.onvoid.webrtc.media.MediaDevices
import dev.onvoid.webrtc.media.audio.AudioDeviceModule
import dev.onvoid.webrtc.media.audio.AudioOptions
import dev.onvoid.webrtc.media.audio.AudioTrackSource
import io.ktor.client.webrtc.*

/**
 * The factory automatically configures the audio device module with the system's default audio capture device
 * during initialization. Audio sources created by this factory will use the configured audio options
 * based on the provided constraints.
 */
public class DefaultAudioFactory : AudioFactory {

    override val audioModule: AudioDeviceModule = AudioDeviceModule().apply {
        setRecordingDevice(MediaDevices.getDefaultAudioCaptureDevice())
    }
    override val peerConnectionFactory: PeerConnectionFactory = PeerConnectionFactory(audioModule)

    override fun createAudioSource(constraints: WebRtcMedia.AudioTrackConstraints): AudioTrackSource {
        val options = AudioOptions().apply {
            autoGainControl = constraints.autoGainControl ?: true
            echoCancellation = constraints.echoCancellation ?: true
            noiseSuppression = constraints.noiseSuppression ?: true
        }
        val volumePercentage = constraints.volume?.let { (it * 100).toInt() } ?: 100
        audioModule.microphoneVolume = volumePercentage
        return peerConnectionFactory.createAudioSource(options)
    }

    override fun close() {
        audioModule.dispose()
        peerConnectionFactory.dispose()
    }
}
