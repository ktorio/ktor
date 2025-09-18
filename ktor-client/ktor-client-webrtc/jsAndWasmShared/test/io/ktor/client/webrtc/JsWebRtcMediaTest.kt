/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import web.errors.DOMException
import web.errors.NotFoundError
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

    private inline fun assertNoMediaDevice(block: () -> Unit) {
        try {
            block()
            assertTrue(false, "Expected NotFoundError to be thrown")
        } catch (e: Throwable) {
            val exception = e.asDomException() ?: throw e
            assertEquals(DOMException.NotFoundError, exception.name, "Expected NotFoundError")
        }
    }

    @Test
    fun testCreateAudioTrackConstraints() = runTest {
        // Assert that constraints are mapped correctly, though ChromeHeadless does not have any media devices
        assertNoMediaDevice {
            client.createAudioTrack()
        }
        assertNoMediaDevice {
            client.createAudioTrack {
                autoGainControl = true
                echoCancellation = true
                noiseSuppression = true
                latency = 1.0
                channelCount = 1
                sampleRate = 10
                volume = 0.5
            }
        }
        // check overloading
        assertNoMediaDevice {
            val constraints = WebRtcMedia.AudioTrackConstraints(echoCancellation = true)
            client.createAudioTrack(constraints)
        }
    }

    @Test
    fun testCreateVideoTrack() = runTest {
        // Assert that constraints are mapped correctly, though ChromeHeadless does not have any media devices
        assertNoMediaDevice {
            client.createVideoTrack()
        }
        assertNoMediaDevice {
            client.createVideoTrack {
                width = 100
                height = 100
                frameRate = 30
                facingMode = WebRtcMedia.FacingMode.USER
                aspectRatio = 1.4
            }
        }
        // check overloading
        assertNoMediaDevice {
            val constraints = WebRtcMedia.VideoTrackConstraints(height = 100)
            client.createVideoTrack(constraints)
        }
    }
}
