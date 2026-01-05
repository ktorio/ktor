/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.test.*

@OptIn(ExperimentalKtorApi::class)
class JsWebRtcMediaTest {

    private lateinit var client: WebRtcClient

    @BeforeTest
    fun setup() {
        client = WebRtcClient(JsWebRtc)
    }

    @AfterTest
    fun cleanup() {
        client.close()
    }

    private fun WebRtcMedia.Track.getSettings() = getNative().getSettings()

    @OptIn(ExperimentalWasmJsInterop::class)
    @Test
    fun testCreateAudioTrackConstraints() = runTest {
        val tracks = mutableListOf<WebRtcMedia.Track>()
        try {
            tracks.add(client.createAudioTrack())

            val audioTrack1 = client.createAudioTrack {
                autoGainControl = true
                echoCancellation = true
            }.also { tracks.add(it) }

            val settings1 = audioTrack1.getSettings()
            assertEquals(true, settings1.autoGainControl)
            assertEquals(true, settings1.echoCancellation.toString().toBoolean())

            // check overloading
            val constraints = WebRtcMedia.AudioTrackConstraints(autoGainControl = false)
            val audioTrack2 = client.createAudioTrack(constraints).also { tracks.add(it) }
            val settings2 = audioTrack2.getSettings()
            assertEquals(false, settings2.autoGainControl)
        } finally {
            tracks.forEach { it.close() }
        }
    }

    @Test
    fun testCreateVideoTrack() = runTest {
        val tracks = mutableListOf<WebRtcMedia.Track>()
        try {
            tracks.add(client.createVideoTrack())

            val videoTrack1 = client.createVideoTrack {
                width = 100
                height = 100
                frameRate = 30
                facingMode = WebRtcMedia.FacingMode.USER
            }.also { tracks.add(it) }
            val settings1 = videoTrack1.getSettings()
            assertEquals(100, settings1.width)
            assertEquals(100, settings1.height)

            // check overloading
            val constraints = WebRtcMedia.VideoTrackConstraints(aspectRatio = 2.0)
            val videoTrack2 = client.createVideoTrack(constraints).also { tracks.add(it) }
            val settings2 = videoTrack2.getSettings()
            assertEquals(2.0, settings2.aspectRatio)
        } finally {
            tracks.forEach { it.close() }
        }
    }
}
