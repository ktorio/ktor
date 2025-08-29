/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.rs.utils

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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

fun assertSdpEquivalent(expected: WebRtc.SessionDescription, actual: WebRtc.SessionDescription?) {
    assertNotNull(actual, "Session description should not be null")
    assertEquals(expected.type, actual.type, "Session description types should match")

    // Compare SDPs without ICE candidates
    val expectedLines = expected.sdp.lines().filter { !it.startsWith("a=candidate:") }

    // Check that all essential lines from expected are present in actual
    for (line in expectedLines) {
        if (line.isNotBlank() && !line.startsWith("a=ice-")) {
            assertTrue(
                actual.sdp.contains(line.trim().trimEnd()),
                "SDP should contain line: $line.\nExpected: ${expected.sdp}\nActual: ${actual.sdp}"
            )
        }
    }
}

fun runTestWithTimeout(
    realTime: Boolean = false,
    block: suspend CoroutineScope.(MutableList<Job>) -> Unit
): TestResult {
    // List to collect all jobs launched during the test to gracefully cancel
    // them in batch so no job will be hanging which lead to timeout on the js target.
    val jobs = mutableListOf<Job>()
    return when {
        realTime -> runTestWithRealTime {
            withTimeout(10_0000) {
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

// Offer-Answer exchange. The first peer creates an offer, the second - an answer.
suspend fun negotiate(
    pc1: WebRtcPeerConnection,
    pc2: WebRtcPeerConnection
) {
    // --- PC1 (Offerer) ---
    val offer = pc1.createOffer()
    // Set a local description. This starts ICE gathering on pc1.
    pc1.setLocalDescription(offer)

    // Wait for PC1's ICE gathering to finish. This ensures all candidates
    // are included in the offer that pc2 will receive.
    pc1.awaitIceGatheringComplete()

    // Now get the local description again, which now contains all ICE candidates.
    pc2.setRemoteDescription(pc1.localDescription!!)

    // --- PC2 (Answerer) ---
    val answer = pc2.createAnswer()
    // Set a local description. This starts ICE gathering on pc2.
    pc2.setLocalDescription(answer)

    pc2.awaitIceGatheringComplete()

    // Get the final answer with all of pc2's candidates.
    pc1.setRemoteDescription(pc2.localDescription!!)
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
