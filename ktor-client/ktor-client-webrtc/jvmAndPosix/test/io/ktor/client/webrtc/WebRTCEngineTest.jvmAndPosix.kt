/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

actual fun createTestWebRTCClient(): WebRTCClient {
    throw NotImplementedError("JVM platform is not supported for WebRTC client")
}

actual fun grantPermissions(audio: Boolean, video: Boolean) {
    throw NotImplementedError("JVM platform is not supported for WebRTC client")
}
