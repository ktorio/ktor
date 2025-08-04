/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.utils

import io.ktor.client.webrtc.*
import io.ktor.test.dispatcher.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout

// Create different WebRtc engine implementation to be tested for every platform.
expect fun createTestWebRtcClient(): WebRtcClient

// Grant permissions to use audio and video media devices
expect fun grantPermissions(audio: Boolean = true, video: Boolean = true)

fun runTestWithPermissions(
    audio: Boolean = true,
    video: Boolean = true,
    realTime: Boolean = false,
    block: suspend CoroutineScope.(MutableList<Job>) -> Unit
): TestResult {
    grantPermissions(audio, video)
    // List to collect all jobs launched during the test to gracefully cancel
    // them in batch so no job will be hanging which lead to timeout on the js target.
    val jobs = mutableListOf<Job>()
    return when {
        realTime -> runTestWithRealTime {
            withTimeout(10_000) {
                try {
                    block(jobs)
                } finally {
                    jobs.forEach { it.cancel() }
                }
            }
        }

        else -> {
            runTest {
                try {
                    block(jobs)
                } finally {
                    jobs.forEach { it.cancel() }
                }
            }
        }
    }
}

// Listen for the connection ICE candidates and add them to the other peer (two-way).
fun CoroutineScope.setupIceExchange(
    pc1: WebRtcPeerConnection,
    pc2: WebRtcPeerConnection,
    jobs: MutableList<Job>
) {
    // Collect ICE candidates from both peers
    val iceCandidates1 = pc1.iceCandidates.collectToChannel(this, jobs)
    val iceCandidates2 = pc2.iceCandidates.collectToChannel(this, jobs)

    val iceExchangeJobs = arrayOf(
        launch {
            for (candidate in iceCandidates1) {
                pc2.addIceCandidate(candidate)
            }
        },
        launch {
            for (candidate in iceCandidates2) {
                pc1.addIceCandidate(candidate)
            }
        }
    )
    jobs.addAll(iceExchangeJobs)
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

fun <T> Flow<T>.collectToChannel(
    scope: CoroutineScope,
    jobs: MutableList<Job>,
    channelCapacity: Int = Channel.UNLIMITED
): Channel<T> {
    val channel = Channel<T>(channelCapacity)
    val collectorJob = scope.launch {
        collect { channel.trySend(it) }
    }
    jobs.add(collectorJob)
    return channel
}

fun <T> StateFlow<T>.collectToChannel(
    scope: CoroutineScope,
    jobs: MutableList<Job>
): Channel<T> = collectToChannel(scope, jobs, Channel.CONFLATED)

// Exchange ice candidates first, then exchange offer and answer.
suspend fun CoroutineScope.connect(
    pc1: WebRtcPeerConnection,
    pc2: WebRtcPeerConnection,
    jobs: MutableList<Job>
) {
    setupIceExchange(pc1, pc2, jobs)
    negotiate(pc1, pc2)
}
