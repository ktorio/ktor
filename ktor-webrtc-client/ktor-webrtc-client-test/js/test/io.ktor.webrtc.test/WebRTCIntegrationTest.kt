/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.webrtc.test

import io.ktor.webrtc.client.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Base test class containing common integration tests for WebRTC engines.
 * Subclasses must provide the implementation of [createClient].
 */
class JsWebRTCEngineIntegrationTest {
    private lateinit var client: WebRTCClient

    /**
     * Create the WebRTC engine implementation to be tested.
     */
    private fun createClient() = WebRTCClient {
        iceServers = this.iceServers
        turnServers = this.turnServers
        statsRefreshRate = 1000 // 1 second refresh rate for stats in JS engine
    }

    /**
     * The STUN and TURN servers to use during testing.
     * Override this in subclasses if needed for specific platform tests.
     */
    private val iceServers: List<String> = listOf("stun:stun.l.google.com:19302")

    private val turnServers: List<IceServer> = listOf(
        IceServer(urls = "turn:test-turn-server.example.com:3478")
    )

    @BeforeTest
    fun setup() {
        client = createClient()
    }

    @AfterTest
    fun cleanup() {
        client.close()
    }

    @Test
    fun testCreatePeerConnection(): TestResult = runTest {
        val peerConnection = client.createPeerConnection()
        assertNotNull(peerConnection, "Peer connection should be created successfully")
        peerConnection.close()
    }

    @Test
    fun testCreateOffer(): TestResult = runTest {
        val peerConnection = client.createPeerConnection()
        val offer = peerConnection.createOffer()

        assertNotNull(offer, "Offer should be created successfully")
        assertEquals(WebRtcPeerConnection.SessionDescriptionType.OFFER, offer.type)
        assertTrue(offer.sdp.isNotEmpty(), "SDP should not be empty")
        assertTrue(offer.sdp.contains("v=0"), "SDP should contain version information")

        peerConnection.close()
    }

    @Test
    fun testCreateAnswer(): TestResult = runTest {
        val offerPeerConnection = client.createPeerConnection()
        val answerPeerConnection = client.createPeerConnection()

        // Create and set offer
        val offer = offerPeerConnection.createOffer()
        offerPeerConnection.setLocalDescription(offer)
        answerPeerConnection.setRemoteDescription(offer)

        // Create answer
        val answer = answerPeerConnection.createAnswer()

        assertNotNull(answer, "Answer should be created successfully")
        assertEquals(WebRtcPeerConnection.SessionDescriptionType.ANSWER, answer.type)
        assertTrue(answer.sdp.isNotEmpty(), "SDP should not be empty")

        offerPeerConnection.close()
        answerPeerConnection.close()
    }

    @Test
    fun testIceCandidateCollection(): TestResult = runTest {
        val peerConnection = client.createPeerConnection()
        val receivedCandidates = mutableListOf<WebRtcPeerConnection.IceCandidate>()

        val job = launch {
            peerConnection.iceCandidateFlow.take(5).toList(receivedCandidates)
        }

        // Trigger ICE candidate gathering by creating and setting an offer
        val offer = peerConnection.createOffer()
        peerConnection.setLocalDescription(offer)

        // Wait for candidates to be collected
        withTimeout(10.seconds) {
            while (receivedCandidates.isEmpty()) {
                delay(100)
            }
        }

        assertTrue(receivedCandidates.isNotEmpty(), "Should receive at least one ICE candidate")
        job.cancel()
        peerConnection.close()
    }

    @Test
    fun testCreateAudioTrack(): TestResult = runTest {
        val audioTrack = client.createAudioTrack()

        assertNotNull(audioTrack, "Audio track should be created successfully")
        assertEquals(WebRTCMediaTrack.Type.AUDIO, audioTrack.kind)
        assertTrue(audioTrack.enabled, "Audio track should be enabled by default")

        audioTrack.stop()
    }

    @Test
    fun testCreateVideoTrack(): TestResult = runTest {
        val videoTrack = client.createVideoTrack()

        assertNotNull(videoTrack, "Video track should be created successfully")
        assertEquals(WebRTCMediaTrack.Type.VIDEO, videoTrack.kind)
        assertTrue(videoTrack.enabled, "Video track should be enabled by default")

        videoTrack.stop()
    }

    @Test
    fun testAddRemoveTrack(): TestResult = runTest {
        val peerConnection = client.createPeerConnection()
        val audioTrack = client.createAudioTrack()

        // Add track
        peerConnection.addTrack(audioTrack)

        // Create offer after adding track
        val offerWithTrack = peerConnection.createOffer()
        assertTrue(offerWithTrack.sdp.contains("audio"), "SDP should contain audio media section")

        // Remove track
        peerConnection.removeTrack(audioTrack)

        // Cleanup
        audioTrack.stop()
        peerConnection.close()
    }

    @Test
    fun testEnableDisableTrack(): TestResult = runTest {
        val audioTrack = client.createAudioTrack()
        assertTrue(audioTrack.enabled, "Track should be enabled by default")

        audioTrack.enable(false)
        assertFalse(audioTrack.enabled, "Track should be disabled after calling enable(false)")

        audioTrack.enable(true)
        assertTrue(audioTrack.enabled, "Track should be enabled after calling enable(true)")

        audioTrack.stop()
    }

    @Test
    fun testEstablishPeerConnection(): TestResult = runTest {
        // Create two peer connections
        val peerConnection1 = client.createPeerConnection()
        val peerConnection2 = client.createPeerConnection()

        // Collect ICE candidates from both peers
        val iceCandidates1 = mutableListOf<WebRtcPeerConnection.IceCandidate>()
        val iceCandidates2 = mutableListOf<WebRtcPeerConnection.IceCandidate>()

        val job1 = launch {
            peerConnection1.iceCandidateFlow.collect { iceCandidates1.add(it) }
        }

        val job2 = launch {
            peerConnection2.iceCandidateFlow.collect { iceCandidates2.add(it) }
        }

        // Create and set offer
        val offer = peerConnection1.createOffer()
        peerConnection1.setLocalDescription(offer)
        peerConnection2.setRemoteDescription(offer)

        // Create and set answer
        val answer = peerConnection2.createAnswer()
        peerConnection2.setLocalDescription(answer)
        peerConnection1.setRemoteDescription(answer)

        // Exchange ICE candidates
        delay(1000) // Wait for some candidates to be generated

        iceCandidates1.forEach { candidate ->
            peerConnection2.addIceCandidate(candidate)
        }

        iceCandidates2.forEach { candidate ->
            peerConnection1.addIceCandidate(candidate)
        }

        // Allow some time for connection establishment
        delay(2000)

        // Cancel collection jobs and clean up
        job1.cancel()
        job2.cancel()
        peerConnection1.close()
        peerConnection2.close()
    }

    @Test
    fun testSimulateErrorHandling(): TestResult = runTest {
        val peerConnection = client.createPeerConnection()

        try {
            // Try to add an invalid ICE candidate
            val invalidCandidate = WebRtcPeerConnection.IceCandidate(
                candidate = "invalid candidate string",
                sdpMid = "0",
                sdpMLineIndex = 0
            )
            peerConnection.addIceCandidate(invalidCandidate)
        } catch (e: Exception) {
            // Expected to fail
            assertTrue(e.message?.contains("Invalid") == true || e.message?.contains("Error") == true)
        } finally {
            peerConnection.close()
        }
    }

    @Test
    fun testStatsCollection(): TestResult = runTest {
        val peerConnection = client.createPeerConnection()
        val audioTrack = client.createAudioTrack()

        peerConnection.addTrack(audioTrack)

        // Wait for stats to be updated
        delay(1000)

        // Get stats report
        val report = peerConnection.statsFlow.first()

        assertNotNull(report)
        assertTrue(report.timestamp > 0)

        // Cleanup
        audioTrack.stop()
        peerConnection.close()
    }
}
