/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

actual fun createTestWebRtcClient(): WebRtcClient = WebRtcClient(JsWebRtc) {
    mediaTrackFactory = MockMediaTrackFactory
    defaultConnectionConfig = {
        iceServers = listOf()
        statsRefreshRate = 100
    }
}

actual fun grantPermissions(audio: Boolean, video: Boolean) = MockMediaTrackFactory.grantPermissions(audio, video)
