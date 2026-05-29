/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.utils

import io.ktor.client.webrtc.*
import io.ktor.test.*
import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestResult
import kotlin.time.Duration

// Create different WebRtc engine implementation to be tested for every platform.
@OptIn(ExperimentalKtorApi::class)
expect fun createTestWebRtcClient(): WebRtcClient

// Grant permissions to use audio and video media devices
expect fun grantPermissions(audio: Boolean = true, video: Boolean = true)

class BackgroundTasksScope(parent: CoroutineScope) : CoroutineScope {
    // we still want to propagate exception to the parent
    internal val job = Job(parent = parent.coroutineContext.job)
    override val coroutineContext = parent.coroutineContext + job
}

/**
 * Creates a new child coroutine scope that cancels all children jobs on completion of the block
 */
suspend fun CoroutineScope.withBackgroundTasks(block: suspend BackgroundTasksScope.() -> Unit) {
    val scope = BackgroundTasksScope(parent = this)
    try {
        block(scope)
    } finally {
        scope.job.cancelAndJoin()
    }
}

fun runTestWithPermissions(
    audio: Boolean = true,
    video: Boolean = true,
    realTime: Boolean = false,
    timeout: Duration = DEFAULT_TEST_TIMEOUT,
    block: suspend BackgroundTasksScope.() -> Unit
): TestResult {
    grantPermissions(audio, video)
    val testBody: suspend CoroutineScope.() -> Unit = {
        withBackgroundTasks {
            block()
        }
    }
    return when {
        realTime -> runTestWithRealTime(timeout = timeout, testBody = testBody)
        else -> runTest(timeout = timeout, testBody = testBody)
    }
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
        try {
            collect { channel.trySend(it) }
        } finally {
            channel.close()
        }
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
