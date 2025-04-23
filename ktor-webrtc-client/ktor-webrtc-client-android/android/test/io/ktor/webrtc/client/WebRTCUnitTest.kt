/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.webrtc.client

import android.Manifest
import android.app.UiAutomation
import androidx.test.rule.GrantPermissionRule
import io.ktor.test.dispatcher.*
import io.ktor.webrtc.client.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
class AndroidWebRTCEngineIntegrationTest {

    @get:Rule
    val mediaRule: GrantPermissionRule? = GrantPermissionRule.grant(
        Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
    )

    // Create the WebRTC engine implementation to be tested.
    private fun createClient() = WebRTCClient(AndroidWebRTC) {
        iceServers = this@AndroidWebRTCEngineIntegrationTest.iceServers
        turnServers = this@AndroidWebRTCEngineIntegrationTest.turnServers
        statsRefreshRate = 100 // 100 ms refresh rate
        mediaTrackFactory = DefaultMediaDevices(RuntimeEnvironment.getApplication())
    }

    private lateinit var client: WebRTCClient

    /**
     * The STUN and TURN servers to use during testing.
     * Override this in subclasses if needed for specific platform tests.
     */
    private val iceServers: List<WebRTC.IceServer> = listOf(WebRTC.IceServer(urls = "stun:stun.l.google.com:19302"))

    private val turnServers: List<WebRTC.IceServer> = listOf()

    @Before
    fun setup() {
        client = createClient()
    }

    @After
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
            assertEquals(WebRTC.SessionDescriptionType.OFFER, offer.type)
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
            assertEquals(WebRTC.SessionDescriptionType.ANSWER, answer.type)
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
                val receivedCandidates = mutableListOf<WebRTC.IceCandidate>()

                val job = launch {
                    peerConnection.iceCandidateFlow.collect { receivedCandidates.add(it) }
                }

                // Create track
                val audioTrack = client.createAudioTrack(WebRTCMedia.AudioTrackConstraints())
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
        val audioTrack = client.createAudioTrack(WebRTCMedia.AudioTrackConstraints())

        assertNotNull(audioTrack, "Audio track should be created successfully")
        assertEquals(WebRTCMedia.TrackType.AUDIO, audioTrack.kind)
        assertTrue(audioTrack.enabled, "Audio track should be enabled by default")

        audioTrack.close()
    }

    @Test
    fun testCreateVideoTrack() = runTest {
        val videoTrack = client.createVideoTrack(WebRTCMedia.VideoTrackConstraints())

        assertNotNull(videoTrack, "Video track should be created successfully")
        assertEquals(WebRTCMedia.TrackType.VIDEO, videoTrack.kind)
        assertTrue(videoTrack.enabled, "Video track should be enabled by default")

        videoTrack.close()
    }

    @Test
    fun testAddRemoveTrack() = runTest {
        val peerConnection = client.createPeerConnection()
        val audioTrack = client.createAudioTrack(WebRTCMedia.AudioTrackConstraints())
        val sender = peerConnection.addTrack(audioTrack)

        // Create offer after adding track
        val offerWithTrack = peerConnection.createOffer()
        assertTrue(offerWithTrack.sdp.contains("audio"), "SDP should contain audio media section")

        // Remove track
        peerConnection.removeTrack(sender)

        // Cleanup
        audioTrack.close()
        peerConnection.close()
    }

    @Test
    fun testEnableDisableTrack() = runTest {
        val audioTrack = client.createAudioTrack(WebRTCMedia.AudioTrackConstraints())
        assertTrue(audioTrack.enabled, "Track should be enabled by default")

        audioTrack.enable(false)
        assertFalse(audioTrack.enabled, "Track should be disabled after calling enable(false)")

        audioTrack.enable(true)
        assertTrue(audioTrack.enabled, "Track should be enabled after calling enable(true)")

        audioTrack.close()
    }

    @Test
    fun testEstablishPeerConnection(): Unit = runTestWithRealTime {
        // Create two peer connections
        val peerConnection1 = client.createPeerConnection()
        val peerConnection2 = client.createPeerConnection()

        // Collect ICE candidates from both peers
        val iceCandidates1 = mutableListOf<WebRTC.IceCandidate>()
        val iceCandidates2 = mutableListOf<WebRTC.IceCandidate>()

        val job1 = launch {
            peerConnection1.iceCandidateFlow.collect { iceCandidates1.add(it) }
        }

        val job2 = launch {
            peerConnection2.iceCandidateFlow.collect { iceCandidates2.add(it) }
        }

        // Add audio tracks for both connections
        peerConnection1.addTrack(client.createAudioTrack(WebRTCMedia.AudioTrackConstraints()))
        peerConnection2.addTrack(client.createAudioTrack(WebRTCMedia.AudioTrackConstraints()))

        // Create and set offer
        val offer = peerConnection1.createOffer()
        peerConnection1.setLocalDescription(offer)
        peerConnection2.setRemoteDescription(offer)

        // Create and set answer
        val answer = peerConnection2.createAnswer()
        peerConnection2.setLocalDescription(answer)
        peerConnection1.setRemoteDescription(answer)

        // Exchange ICE candidates
        delay(2000) // Wait for some candidates to be generated
        job1.cancel()
        job2.cancel()

        for (candidate in iceCandidates1) {
            peerConnection2.addIceCandidate(candidate)
        }

        for (candidate in iceCandidates2) {
            peerConnection1.addIceCandidate(candidate)
        }

        assertEquals(2, iceCandidates1.size, "Peer connection 1 should have received 2 candidates")
        assertEquals(2, iceCandidates2.size, "Peer connection 2 should have received 2 candidates")

        peerConnection1.close()
        peerConnection2.close()
    }

    @Test
    fun testSimulateErrorHandling() = runTest {
        val peerConnection = client.createPeerConnection()

        assertFails {
            val invalidCandidate = WebRTC.IceCandidate(
                candidate = "invalid candidate string", sdpMid = "0", sdpMLineIndex = 0
            )
            peerConnection.addIceCandidate(invalidCandidate)
        }

        peerConnection.close()
    }

    @Test
    fun testStatsCollection() = runTestWithRealTime {
        client.createPeerConnection().use { peerConnection ->
            val audioTrack = client.createAudioTrack(WebRTCMedia.AudioTrackConstraints())
            peerConnection.addTrack(audioTrack)

            val stats = mutableListOf<List<WebRTC.Stats>>()

            val statsCollectionJob = launch {
                peerConnection.statsFlow.collect { stats.add(it) }
            }
            delay(150.milliseconds)

            // Wait for stats to be collected
            statsCollectionJob.cancel()

            assertEquals(2, stats.size, "Stats should be collected")
            val (s1, s2) = stats

            assertEquals(emptyList(), s1)
            println(s2.map { it.type })
            assertEquals(3, s2.size)

            assertNotNull(s2.firstOrNull { it.type == "peer-connection" })

            val mediaPlayout = s2.first { it.type == "media-playout" }
            assertEquals(mediaPlayout.id, mediaPlayout.props["id"])

            val mediaSource = s2.first { it.type == "media-source" }
            assertEquals("audio", mediaSource.props["kind"])
            audioTrack.close()
        }
    }
}
