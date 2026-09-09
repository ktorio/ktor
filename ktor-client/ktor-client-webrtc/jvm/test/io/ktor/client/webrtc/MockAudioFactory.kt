/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import dev.onvoid.webrtc.PeerConnectionFactory
import dev.onvoid.webrtc.media.audio.AudioTrackSource
import dev.onvoid.webrtc.media.audio.CustomAudioSource
import dev.onvoid.webrtc.media.audio.HeadlessAudioDeviceModule
import io.ktor.client.webrtc.media.*
import io.ktor.client.webrtc.utils.Ticker
import kotlin.time.Duration.Companion.milliseconds

class MockAudioCapturer : AutoCloseable {
    val source: CustomAudioSource = CustomAudioSource()
    val ticker = Ticker(10.milliseconds) {
        // Generate 10ms of silence (480 frames at 48kHz, stereo, 16-bit)
        val pcm = ByteArray(480 * 2 * 2) // frames * channels * bytes_per_sample
        source.pushAudio(pcm, 16, 48000, 2, 480)
    }

    fun start() {
        ticker.start()
    }

    fun stop() {
        ticker.stop()
    }

    override fun close() {
        stop()
        source.dispose()
    }
}

class MockAudioFactory : AudioFactory {
    override val audioModule: HeadlessAudioDeviceModule = HeadlessAudioDeviceModule()
    override val peerConnectionFactory: PeerConnectionFactory = PeerConnectionFactory(audioModule)
    private val startedCapturers = mutableListOf<MockAudioCapturer>()

    override fun createAudioSource(constraints: WebRtcMedia.AudioTrackConstraints): AudioTrackSource {
        val capturer = MockAudioCapturer().apply { start() }
        startedCapturers.add(capturer)
        return capturer.source
    }

    override fun close() {
        startedCapturers.forEach { it.close() }
        startedCapturers.clear()
        audioModule.dispose()
        peerConnectionFactory.dispose()
    }
}
