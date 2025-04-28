/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import MockMediaTrackFactory

actual fun createTestWebRTCClient(): WebRTCClient = WebRTCClient(JsWebRTC) {
    iceServers = listOf(WebRTC.IceServer(urls = "stun:stun.l.google.com:19302"))
    turnServers = listOf()
    statsRefreshRate = 100
    mediaTrackFactory = MockMediaTrackFactory
}
