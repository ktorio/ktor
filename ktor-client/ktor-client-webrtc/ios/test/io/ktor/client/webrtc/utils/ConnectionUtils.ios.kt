/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.utils

import io.ktor.client.webrtc.*
import io.ktor.utils.io.*
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalKtorApi::class)
actual fun createTestWebRtcClient(): WebRtcClient {
    return WebRtcClient(IosWebRtc) {
        defaultConnectionConfig = {
            statsRefreshRate = 100.milliseconds
        }
    }
}

/**
 * There are no camera or microphone on the iOS simulator.
 */
actual fun grantPermissions(audio: Boolean, video: Boolean) {}
