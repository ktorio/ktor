/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.utils

import io.ktor.client.webrtc.*
import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest

// Create different WebRtc engine implementation to be tested for every platform.
@OptIn(ExperimentalKtorApi::class)
expect fun createTestWebRtcClient(): WebRtcClient

// Grant permissions to use audio and video media devices
expect fun grantPermissions(audio: Boolean = true, video: Boolean = true)

class BackgroundTasksScope(parent: CoroutineScope) : CoroutineScope by parent {
    fun stopBackgroundTasks() {
        coroutineContext.cancelChildren()
    }
}

suspend fun CoroutineScope.withBackgroundTasks(block: suspend BackgroundTasksScope.() -> Unit) {
    val scope = BackgroundTasksScope(parent = this)
    try {
        block(scope)
    } finally {
        scope.stopBackgroundTasks()
    }
}

fun runTestWithPermissions(
    audio: Boolean = true,
    video: Boolean = true,
    realTime: Boolean = false,
    block: suspend BackgroundTasksScope.() -> Unit
): TestResult {
    grantPermissions(audio, video)
    suspend fun CoroutineScope.testBody() = withBackgroundTasks {
        block()
    }
    return if (realTime) runTestWithRealTime { testBody() } else runTest { testBody() }
}

// Listen for the connection ICE candidates and add them to the other peer (two-way).
fun BackgroundTasksScope.setupIceExchange(
    pc1: WebRtcPeerConnection,
    pc2: WebRtcPeerConnection
) {
    // Collect ICE candidates from both peers
    val iceCandidates1 = pc1.iceCandidates.collectToChannel()
    val iceCandidates2 = pc2.iceCandidates.collectToChannel()

    launch {
        for (candidate in iceCandidates1) {
            pc2.addIceCandidate(candidate)
        }
    }
    launch {
        for (candidate in iceCandidates2) {
            pc1.addIceCandidate(candidate)
        }
    }
}

// Offer-Answer exchange. The first peer creates an offer, the second - an answer.
suspend fun negotiate(
    pc1: WebRtcPeerConnection,
    pc2: WebRtcPeerConnection
) {
    // Create and set offer
    val offer = pc1.createOffer()
    pc1.setLocalDescription(offer)
    pc2.setRemoteDescription(offer)

    // Create and set answer
    val answer = pc2.createAnswer()
    pc2.setLocalDescription(answer)
    pc1.setRemoteDescription(answer)
}

context(scope: BackgroundTasksScope)
fun <T> Flow<T>.collectToChannel(channelCapacity: Int = Channel.UNLIMITED): Channel<T> {
    val channel = Channel<T>(channelCapacity)
    scope.launch {
        collect { channel.trySend(it) }
    }
    return channel
}

context(scope: BackgroundTasksScope)
fun <T> StateFlow<T>.collectToChannel(): Channel<T> = collectToChannel(channelCapacity = Channel.CONFLATED)

// Exchange ice candidates first, then exchange offer and answer.
suspend fun BackgroundTasksScope.connect(
    pc1: WebRtcPeerConnection,
    pc2: WebRtcPeerConnection,
) {
    setupIceExchange(pc1, pc2)
    negotiate(pc1, pc2)
}
