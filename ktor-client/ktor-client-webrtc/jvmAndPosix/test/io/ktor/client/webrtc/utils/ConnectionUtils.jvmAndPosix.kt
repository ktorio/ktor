/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.utils

import io.ktor.client.webrtc.*

actual fun createTestWebRtcClient(): WebRtcClient {
    throw NotImplementedError("There are no JVM WebRTC clients now.")
}

actual fun grantPermissions(audio: Boolean, video: Boolean) {
    throw NotImplementedError("There are no JVM WebRTC clients now.")
}
