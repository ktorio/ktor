/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.client.webrtc.browser.DOMException
import io.ktor.test.dispatcher.runTestWithRealTime
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WasmJsWebRtcMediaTest {

    private lateinit var client: WebRtcClient

    @BeforeTest
    fun setup() {
        client = WebRtcClient(JsWebRtc)
    }

    @AfterTest
    fun cleanup() {
        client.close()
    }

    private suspend inline fun assertThrows(jsExceptionName: String, crossinline block: suspend () -> Unit) {
        try {
            block()
            assertTrue(false, "Expected exception $jsExceptionName not thrown")
        } catch (e: JsException) {
            val jsException = (e.thrownValue) as DOMException
            assertEquals(jsExceptionName, jsException.name.toString())
        }
    }

    private val notFoundError = "NotFoundError"

    @Test
    fun testCreateAudioTrackConstraints() = runTestWithRealTime {
        // Assert that constraints are mapped correctly, though ChromeHeadless does not have any media devices
        assertThrows(notFoundError) {
            client.createAudioTrack(WebRtcMedia.AudioTrackConstraints())
        }
        assertThrows(notFoundError) {
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
        assertThrows(notFoundError) {
            client.createVideoTrack(WebRtcMedia.VideoTrackConstraints())
        }
        assertThrows(notFoundError) {
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
