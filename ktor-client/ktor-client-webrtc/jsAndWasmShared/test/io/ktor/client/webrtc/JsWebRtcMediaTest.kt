/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import kotlinx.coroutines.test.runTest
import web.errors.DOMException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFails

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

    @Test
    fun testCreateAudioTrackConstraints() = runTest {
        // Assert that constraints are mapped correctly, though ChromeHeadless does not have any media devices
        assertFails(DOMException.NotFoundError.toString()) {
            client.createAudioTrack(WebRtcMedia.AudioTrackConstraints())
        }
        assertFails(DOMException.NotFoundError.toString()) {
            client.createAudioTrack(
                WebRtcMedia.AudioTrackConstraints(
                    autoGainControl = true,
                    echoCancellation = true,
                    noiseSuppression = true,
                    latency = 1.0,
                    channelCount = 1,
                    sampleRate = 10,
                    volume = 0.5
                )
            )
        }
    }

    @Test
    fun testCreateVideoTrack() = runTest {
        // Assert that constraints are mapped correctly, though ChromeHeadless does not have any media devices
        assertFails(DOMException.NotFoundError.toString()) {
            client.createVideoTrack(WebRtcMedia.VideoTrackConstraints())
        }
        assertFails(DOMException.NotFoundError.toString()) {
            client.createVideoTrack(
                WebRtcMedia.VideoTrackConstraints(
                    width = 100,
                    height = 100,
                    frameRate = 30,
                    facingMode = WebRtcMedia.FacingMode.USER,
                    aspectRatio = 1.4,
                )
            )
        }
    }
}
