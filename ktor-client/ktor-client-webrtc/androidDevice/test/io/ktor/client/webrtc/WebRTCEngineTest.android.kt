/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import android.Manifest
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry

private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

actual fun createTestWebRTCClient(): WebRTCClient {
    return WebRTCClient(AndroidWebRTC) {
        iceServers = listOf()
        turnServers = listOf()
        statsRefreshRate = 100 // 100 ms refresh rate
        mediaTrackFactory = AndroidMediaDevices(ctx)
    }
}

actual fun grantPermissions(audio: Boolean, video: Boolean) {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    if (audio) {
        automation.grantRuntimePermission(ctx.packageName, Manifest.permission.RECORD_AUDIO)
    } else {
        automation.revokeRuntimePermission(ctx.packageName, Manifest.permission.RECORD_AUDIO)
    }
    if (video) {
        automation.grantRuntimePermission(ctx.packageName, Manifest.permission.CAMERA)
    } else {
        automation.revokeRuntimePermission(ctx.packageName, Manifest.permission.CAMERA)
    }
}
