/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.test.dispatcher.runTestWithRealTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Create different WebRTC engine implementation to be tested for every platform.
expect fun createTestWebRTCClient(): WebRTCClient

// Grant permissions to use audio and video media devices
expect fun grantPermissions(audio: Boolean = true, video: Boolean = true)

inline fun runTestWithPermissions(
    audio: Boolean = true,
    video: Boolean = true,
    realTime: Boolean = false,
    crossinline block: suspend CoroutineScope.() -> Unit
): TestResult {
    grantPermissions(audio, video)
    val testResult = if (realTime) {
        runTestWithRealTime { block() }
    } else {
        runTest { block() }
    }
    return testResult
}

class WebRTCEngineTest {

    private lateinit var client: WebRTCClient

    private inline fun testConnection(
        realtime: Boolean = false,
        crossinline block: suspend CoroutineScope.(WebRtcPeerConnection) -> Unit
    ): TestResult {
        return runTestWithPermissions(audio = true, video = true, realtime) {
            client.createPeerConnection().use { block(it) }
        }
    }

    @BeforeTest
    fun setup() {
        client = createTestWebRTCClient()
    }

    @AfterTest
    fun cleanup() {
        client.close()
    }

    @Test
    fun testCreatePeerConnection() = testConnection { peerConnection ->
        assertNotNull(peerConnection, "Peer connection should be created successfully")
    }

    @Test
    fun testCreateOffer() = testConnection { peerConnection ->
        val offer = peerConnection.createOffer()

        assertNotNull(offer, "Offer should be created successfully")
        assertEquals(WebRTC.SessionDescriptionType.OFFER, offer.type)
        assertTrue(offer.sdp.isNotEmpty(), "SDP should not be empty")
        assertTrue(offer.sdp.contains("v=0"), "SDP should contain version information")
    }

    @Test
    fun testCreateAnswer() = testConnection { offerPeerConnection ->
        client.createPeerConnection().use { answerPeerConnection ->
            // Create and set offer
            val offer = offerPeerConnection.createOffer()
            offerPeerConnection.setLocalDescription(offer)
            answerPeerConnection.setRemoteDescription(offer)

            // Create answer
            val answer = answerPeerConnection.createAnswer()

            assertNotNull(answer, "Answer should be created successfully")
            assertEquals(WebRTC.SessionDescriptionType.ANSWER, answer.type)
            assertTrue(answer.sdp.isNotEmpty(), "SDP should not be empty")
        }
    }

    @Test
    fun testIceCandidateCollection() = testConnection(realtime = true) { peerConnection ->
        val receivedCandidates = Channel<WebRTC.IceCandidate>(1)

        val job = launch {
            peerConnection.iceCandidateFlow.collect { receivedCandidates.trySend(it) }
        }

        // Create track
        val audioTrack = client.createAudioTrack(WebRTCMedia.AudioTrackConstraints())
        peerConnection.addTrack(audioTrack)

        // Trigger ICE candidate gathering by creating and setting an offer
        val offer = peerConnection.createOffer()
        peerConnection.setLocalDescription(offer)

        withTimeout(5000) {
            receivedCandidates.receive()
        }
        job.cancel()
    }

    @Test
    fun testCreateAudioTrack() = runTestWithPermissions {
        val audioTrack = client.createAudioTrack(WebRTCMedia.AudioTrackConstraints())

        assertNotNull(audioTrack, "Audio track should be created successfully")
        assertEquals(WebRTCMedia.TrackType.AUDIO, audioTrack.kind)
        assertTrue(audioTrack.enabled, "Audio track should be enabled by default")

        audioTrack.close()
    }

    @Test
    fun testCreateVideoTrack() = runTestWithPermissions {
        val videoTrack = client.createVideoTrack(WebRTCMedia.VideoTrackConstraints())

        assertNotNull(videoTrack, "Video track should be created successfully")
        assertEquals(WebRTCMedia.TrackType.VIDEO, videoTrack.kind)
        assertTrue(videoTrack.enabled, "Video track should be enabled by default")

        videoTrack.close()
    }

    @Test
    fun testAddRemoveTrack() = testConnection { pc ->
        val audioTrack = client.createAudioTrack(WebRTCMedia.AudioTrackConstraints())
        val sender = pc.addTrack(audioTrack)

        // Create offer after adding track
        val offerWithTrack = pc.createOffer()
        assertTrue(offerWithTrack.sdp.contains("audio"), "SDP should contain audio media section")

        // Remove track
        pc.removeTrack(sender)

        // Cleanup
        audioTrack.close()
    }

    @Test
    fun testEnableDisableTrack() = runTestWithPermissions {
        val audioTrack = client.createAudioTrack(WebRTCMedia.AudioTrackConstraints())
        assertTrue(audioTrack.enabled, "Track should be enabled by default")

        audioTrack.enable(false)
        assertFalse(audioTrack.enabled, "Track should be disabled after calling enable(false)")

        audioTrack.enable(true)
        assertTrue(audioTrack.enabled, "Track should be enabled after calling enable(true)")

        audioTrack.close()
    }

    @Test
    fun testEstablishPeerConnection() = testConnection(realtime = true) { pc1 ->
        client.createPeerConnection().use { pc2 ->
            // Collect ICE candidates from both peers
            val iceCandidates1 = Channel<WebRTC.IceCandidate>(Channel.UNLIMITED)
            val iceCandidates2 = Channel<WebRTC.IceCandidate>(Channel.UNLIMITED)

            val connectionState1 = Channel<WebRTC.IceConnectionState>(Channel.CONFLATED)
            val connectionState2 = Channel<WebRTC.IceConnectionState>(Channel.CONFLATED)

            val collectIceCandidates1Job = launch { pc1.iceCandidateFlow.collect { iceCandidates1.send(it) } }
            val collectIceCandidates2Job = launch { pc2.iceCandidateFlow.collect { iceCandidates2.send(it) } }

            val collectConnectionState1Job = launch { pc1.iceConnectionStateFlow.collect { connectionState1.send(it) } }
            val collectConnectionState2Job = launch { pc2.iceConnectionStateFlow.collect { connectionState2.send(it) } }

            // Add audio tracks for both connections
            pc1.addTrack(client.createAudioTrack(WebRTCMedia.AudioTrackConstraints()))
            pc2.addTrack(client.createAudioTrack(WebRTCMedia.AudioTrackConstraints()))

            // Create and set offer
            val offer = pc1.createOffer()
            pc1.setLocalDescription(offer)
            pc2.setRemoteDescription(offer)

            // Create and set answer
            val answer = pc2.createAnswer()
            pc2.setLocalDescription(answer)
            pc1.setRemoteDescription(answer)

            fun connectionEstablished(): Boolean =
                pc1.iceConnectionStateFlow.value.isSuccessful() && pc2.iceConnectionStateFlow.value.isSuccessful()

            withTimeout(5000) {
                // Exchange ICE candidates
                while (!connectionEstablished()) {
                    select {
                        iceCandidates1.onReceiveCatching { pc2.addIceCandidate(it.getOrThrow()) }
                        iceCandidates2.onReceiveCatching { pc1.addIceCandidate(it.getOrThrow()) }
                        connectionState1.onReceiveCatching { it.getOrThrow() }
                        connectionState2.onReceiveCatching { it.getOrThrow() }
                    }
                }
            }

            collectIceCandidates1Job.cancel()
            collectIceCandidates2Job.cancel()
            collectConnectionState1Job.cancel()
            collectConnectionState2Job.cancel()
        }
    }

    @Test
    fun testInvalidIceCandidate() = testConnection { pc ->
        assertFailsWith<WebRTC.IceException> {
            val invalidCandidate = WebRTC.IceCandidate(
                candidate = "invalid candidate string",
                sdpMid = "0",
                sdpMLineIndex = 0
            )
            pc.addIceCandidate(invalidCandidate)
        }
    }

    @Test
    fun testInvalidDescription() = testConnection { pc ->
        assertFailsWith<WebRTC.SdpException> {
            val remote = WebRTC.SessionDescription(WebRTC.SessionDescriptionType.OFFER, "invalid description")
            pc.setRemoteDescription(remote)
        }
        assertFailsWith<WebRTC.SdpException> {
            val remote = WebRTC.SessionDescription(WebRTC.SessionDescriptionType.ANSWER, "invalid description")
            pc.setLocalDescription(remote)
        }
    }

    @Test
    fun testStatsCollection() = testConnection(realtime = true) { peerConnection ->
        val audioTrack = client.createAudioTrack(WebRTCMedia.AudioTrackConstraints())
        peerConnection.addTrack(audioTrack)

        val stats = Channel<List<WebRTC.Stats>>()

        val statsCollectionJob = launch {
            peerConnection.statsFlow.collect { stats.trySend(it) }
        }

        withTimeout(5000) {
            val firstStats = stats.receive()
            assertEquals(emptyList(), firstStats)

            val realStats = stats.receive()
            assertTrue(realStats.size >= 2)

            assertNotNull(realStats.firstOrNull { it.type == "peer-connection" })

            val mediaSource = realStats.first { it.type == "media-source" }
            assertEquals("audio", mediaSource.props["kind"])
        }

        statsCollectionJob.cancel()
        audioTrack.close()
    }
}
