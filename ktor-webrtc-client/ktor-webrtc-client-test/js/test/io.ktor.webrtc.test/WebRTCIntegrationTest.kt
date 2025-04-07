/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.webrtc.test

import io.ktor.webrtc.client.*
import io.ktor.webrtc.client.engine.*
import io.ktor.webrtc.client.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

object MockMediaTrackFactory : MediaTrackFactory {
    override suspend fun createAudioTrack(constraints: AudioTrackConstraints): WebRTCAudioTrack {
        return JsAudioTrack(makeDummyAudioStreamTrack())
    }

    override suspend fun createVideoTrack(constraints: VideoTrackConstraints): WebRTCVideoTrack {
        return JsVideoTrack(makeDummyVideoStreamTrack(100, 100))
    }

}

/**
 * Base test class containing common integration tests for WebRTC engines.
 * Subclasses must provide the implementation of [createClient].
 */
class JsWebRTCEngineIntegrationTest {
    private lateinit var client: WebRTCClient
    private val scope = TestScope()

    /**
     * Create the WebRTC engine implementation to be tested.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun createClient() = WebRTCClient(JsWebRTC) {
        iceServers = this@JsWebRTCEngineIntegrationTest.iceServers
        turnServers = this@JsWebRTCEngineIntegrationTest.turnServers
        statsRefreshRate = 100 // 100 ms refresh rate for stats in JS engine
        mediaTrackFactory = MockMediaTrackFactory
    }

    /**
     * The STUN and TURN servers to use during testing.
     * Override this in subclasses if needed for specific platform tests.
     */
    private val iceServers: List<IceServer> = listOf(IceServer(urls = "stun:stun.l.google.com:19302"))

    private val turnServers: List<IceServer> = listOf()

    @BeforeTest
    fun setup() {
        client = createClient()
    }

    @AfterTest
    fun cleanup() {
        client.close()
    }

    @Test
    fun testCreatePeerConnection() = runTest {
        client.createPeerConnection().use { peerConnection ->
            assertNotNull(peerConnection, "Peer connection should be created successfully")
        }
    }

    @Test
    fun testCreateOffer() = runTest {
        client.createPeerConnection().use { peerConnection ->
            val offer = peerConnection.createOffer()

            assertNotNull(offer, "Offer should be created successfully")
            assertEquals(WebRtcPeerConnection.SessionDescriptionType.OFFER, offer.type)
            assertTrue(offer.sdp.isNotEmpty(), "SDP should not be empty")
            assertTrue(offer.sdp.contains("v=0"), "SDP should contain version information")
        }
    }

    @Test
    fun testCreateAnswer() = runTest {
        val offerPeerConnection = client.createPeerConnection()
        val answerPeerConnection = client.createPeerConnection()

        try {
            // Create and set offer
            val offer = offerPeerConnection.createOffer()
            offerPeerConnection.setLocalDescription(offer)
            answerPeerConnection.setRemoteDescription(offer)

            // Create answer
            val answer = answerPeerConnection.createAnswer()

            assertNotNull(answer, "Answer should be created successfully")
            assertEquals(WebRtcPeerConnection.SessionDescriptionType.ANSWER, answer.type)
            assertTrue(answer.sdp.isNotEmpty(), "SDP should not be empty")
        } finally {
            offerPeerConnection.close()
            answerPeerConnection.close()
        }
    }

    @Test
    fun testIceCandidateCollection() = runTest {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            client.createPeerConnection().use { peerConnection ->
                val receivedCandidates = mutableListOf<WebRtcPeerConnection.IceCandidate>()

                val job = launch {
                    peerConnection.iceCandidateFlow.collect { receivedCandidates.add(it) }
                }

                // Create track
                val audioTrack = client.createAudioTrack(AudioTrackConstraints())
                peerConnection.addTrack(audioTrack)

                // Trigger ICE candidate gathering by creating and setting an offer
                val offer = peerConnection.createOffer()
                peerConnection.setLocalDescription(offer)

                // Add a delay to give time for ICE candidates to be generated
                delay(1000)
                job.cancel()

                assertTrue(receivedCandidates.isNotEmpty(), "Should receive at least one ICE candidate")
            }
        }
    }

    @Test
    fun testCreateAudioTrack() = runTest {
        val audioTrack = client.createAudioTrack(AudioTrackConstraints())

        assertNotNull(audioTrack, "Audio track should be created successfully")
        assertEquals(WebRTCMediaTrack.Type.AUDIO, audioTrack.kind)
        assertTrue(audioTrack.enabled, "Audio track should be enabled by default")

        audioTrack.stop()
    }

    @Test
    fun testCreateVideoTrack() = runTest {
        val videoTrack = client.createVideoTrack(VideoTrackConstraints())

        assertNotNull(videoTrack, "Video track should be created successfully")
        assertEquals(WebRTCMediaTrack.Type.VIDEO, videoTrack.kind)
        assertTrue(videoTrack.enabled, "Video track should be enabled by default")

        videoTrack.stop()
    }

    @Test
    fun testAddRemoveTrack() = runTest {
        val peerConnection = client.createPeerConnection()
        val audioTrack = client.createAudioTrack(AudioTrackConstraints())
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
    fun testEnableDisableTrack() = runTest {
        val audioTrack = client.createAudioTrack(AudioTrackConstraints())
        assertTrue(audioTrack.enabled, "Track should be enabled by default")

        audioTrack.enable(false)
        assertFalse(audioTrack.enabled, "Track should be disabled after calling enable(false)")

        audioTrack.enable(true)
        assertTrue(audioTrack.enabled, "Track should be enabled after calling enable(true)")

        audioTrack.stop()
    }

    @Test
    fun testEstablishPeerConnection() = runTest {
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

        // Add video tracks for both connections
        peerConnection1.addTrack(client.createAudioTrack(AudioTrackConstraints()))
        peerConnection2.addTrack(client.createAudioTrack(AudioTrackConstraints()))

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

        assertEquals(2, iceCandidates1.size, "Peer connection 1 should have received 2 candidates")
        assertEquals(2, iceCandidates2.size, "Peer connection 2 should have received 2 candidates")

        // Cancel collection jobs and clean up
        job1.cancel()
        job2.cancel()
        peerConnection1.close()
        peerConnection2.close()
    }

    @Test
    fun testSimulateErrorHandling() = runTest {
        val peerConnection = client.createPeerConnection()

        assertFails {
            val invalidCandidate = WebRtcPeerConnection.IceCandidate(
                candidate = "invalid candidate string",
                sdpMid = "0",
                sdpMLineIndex = 0
            )
            peerConnection.addIceCandidate(invalidCandidate)
        }

        peerConnection.close()
    }

    @Test
    fun testStatsCollection() = runTest {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            client.createPeerConnection().use { peerConnection ->
                val audioTrack = client.createAudioTrack(AudioTrackConstraints())
                peerConnection.addTrack(audioTrack)

                val stats = mutableListOf<List<WebRTCStats>>()

                val statsCollectionJob = launch {
                    peerConnection.statsFlow.collect { stats.add(it) }
                }
                delay(150.milliseconds)

                // Wait for stats to be collected
                statsCollectionJob.cancel()

                assertEquals(2, stats.size, "Stats should be collected")
                val (s1, s2) = stats

                assertEquals(emptyList<WebRTCStats>(), s1)
                assertEquals(3, s2.size)
                assertNotNull(s2.firstOrNull { it.type == "media-playout" })
                assertNotNull(s2.firstOrNull { it.type == "peer-connection" })
                assertNotNull(s2.firstOrNull { it.type == "media-source" })
                audioTrack.stop()
            }
        }
    }
}
