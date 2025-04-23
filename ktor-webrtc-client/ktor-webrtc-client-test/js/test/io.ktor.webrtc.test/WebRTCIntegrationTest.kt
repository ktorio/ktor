/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.webrtc.test

import io.ktor.webrtc.client.*
import io.ktor.webrtc.client.engine.*
import io.ktor.webrtc.client.utils.*

object MockMediaTrackFactory : MediaTrackFactory {
    override suspend fun createAudioTrack(constraints: WebRTCMedia.AudioTrackConstraints): WebRTCMedia.AudioTrack {
        return JsAudioTrack(makeDummyAudioStreamTrack())
    }

    override suspend fun createVideoTrack(constraints: WebRTCMedia.VideoTrackConstraints): WebRTCMedia.VideoTrack {
        return JsVideoTrack(makeDummyVideoStreamTrack(100, 100))
    }
}

class JsWebRTCEngineIntegrationTest : WebRTCEngineIntegrationTest() {
    /**
     * Create the WebRTC engine implementation to be tested.
     */
    override fun createClient() = WebRTCClient(JsWebRTC) {
        iceServers = this@JsWebRTCEngineIntegrationTest.iceServers
        turnServers = this@JsWebRTCEngineIntegrationTest.turnServers
        statsRefreshRate = 100 // 100 ms refresh rate for stats in JS engine
        mediaTrackFactory = MockMediaTrackFactory
    }
}
