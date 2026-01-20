/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.utils

import io.ktor.client.webrtc.WebRtcClient
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(markerClass = [ExperimentalKtorApi::class])
actual fun createTestWebRtcClient(): WebRtcClient {
    TODO("Not yet implemented")
}

actual fun grantPermissions(audio: Boolean, video: Boolean) {
}
