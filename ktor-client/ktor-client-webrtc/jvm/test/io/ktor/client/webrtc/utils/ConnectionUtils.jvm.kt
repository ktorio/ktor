/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.utils

import io.ktor.client.webrtc.*
import io.ktor.client.webrtc.media.JvmMediaDevices
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalKtorApi::class)
actual fun createTestWebRtcClient(): WebRtcClient {
    return WebRtcClient(JvmWebRtc) {
        mediaTrackFactory = JvmMediaDevices(
            audioFactory = MockAudioFactory(),
            videoFactory = MockVideoFactory()
        )
        defaultConnectionConfig = {
            statsRefreshRate = 100.milliseconds
            exceptionHandler = CoroutineExceptionHandler { _, e ->
                throw e
            }
        }
    }
}

actual fun grantPermissions(audio: Boolean, video: Boolean) {}
