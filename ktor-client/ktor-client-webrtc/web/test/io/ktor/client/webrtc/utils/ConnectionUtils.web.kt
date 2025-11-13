/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.utils

import io.ktor.client.webrtc.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalKtorApi::class)
actual fun createTestWebRtcClient(): WebRtcClient = WebRtcClient(JsWebRtc) {
    mediaTrackFactory = MockMediaTrackFactory
    defaultConnectionConfig = {
        iceServers = listOf()
        statsRefreshRate = 100.milliseconds
        // propagate exceptions to the test scope
        exceptionHandler = CoroutineExceptionHandler { _, e -> throw e }
    }
}

actual fun grantPermissions(audio: Boolean, video: Boolean) = MockMediaTrackFactory.grantPermissions(audio, video)
