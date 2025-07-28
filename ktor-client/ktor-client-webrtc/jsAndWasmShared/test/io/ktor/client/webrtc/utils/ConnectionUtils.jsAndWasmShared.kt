/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.utils

import io.ktor.client.webrtc.*
import kotlinx.coroutines.CoroutineExceptionHandler

actual fun createTestWebRtcClient(): WebRtcClient = WebRtcClient(JsWebRtc) {
    mediaTrackFactory = MockMediaTrackFactory
    defaultConnectionConfig = {
        iceServers = listOf()
        statsRefreshRate = 100
        // propagate exceptions to the test scope
        coroutineContext = CoroutineExceptionHandler { _, e -> throw e }
    }
}

actual fun grantPermissions(audio: Boolean, video: Boolean) = MockMediaTrackFactory.grantPermissions(audio, video)
