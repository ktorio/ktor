/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.webrtc.test

import io.ktor.test.dispatcher.*
import io.ktor.webrtc.client.*
import io.ktor.webrtc.client.engine.*
import io.ktor.webrtc.client.utils.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.newCoroutineContext
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

object MockMediaTrackFactory : MediaTrackFactory {
    override suspend fun createAudioTrack(constraints: WebRTCMedia.AudioTrackConstraints): WebRTCMedia.AudioTrack {
        return WasmJsAudioTrack(makeDummyAudioStreamTrack())
    }

    override suspend fun createVideoTrack(constraints: WebRTCMedia.VideoTrackConstraints): WebRTCMedia.VideoTrack {
        return WasmJsVideoTrack(makeDummyVideoStreamTrack(100, 100))
    }
}

/**
 * Base test class containing common integration tests for WebRTC engines.
 * Subclasses must provide the implementation of [createClient].
 */
class WasmJsWebRTCEngineIntegrationTest : WebRTCEngineIntegrationTest() {
    /**
     * Create the WebRTC engine implementation to be tested.
     */
    override fun createClient() = WebRTCClient(JsWebRTC) {
        iceServers = this@WasmJsWebRTCEngineIntegrationTest.iceServers
        turnServers = this@WasmJsWebRTCEngineIntegrationTest.turnServers
        statsRefreshRate = 100 // 100 ms refresh rate for stats in JS engine
        mediaTrackFactory = MockMediaTrackFactory
    }

    @Test
    fun test() = runTest {
        val ctx = currentCoroutineContext()
        println(ctx.isActive)
    }
}
