/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.utils

import android.Manifest
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.webrtc.*
import io.ktor.client.webrtc.media.AndroidMediaDevices
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.time.Duration.Companion.milliseconds

private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

@OptIn(ExperimentalKtorApi::class)
actual fun createTestWebRtcClient(): WebRtcClient {
    return WebRtcClient(AndroidWebRtc) {
        mediaTrackFactory = AndroidMediaDevices(ctx)
        defaultConnectionConfig = {
            iceServers = listOf()
            statsRefreshRate = 100.milliseconds
            // propagate exceptions to the test scope
            exceptionHandler = CoroutineExceptionHandler { _, e -> throw e }
        }
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
