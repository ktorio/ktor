/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.utils

import io.ktor.client.webrtc.*
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
actual fun createTestWebRtcClient(): WebRtcClient {
    throw NotImplementedError("There are no WebRTC client now.")
}

actual fun grantPermissions(audio: Boolean, video: Boolean) {
    throw NotImplementedError("There are no WebRTC client now.")
}
