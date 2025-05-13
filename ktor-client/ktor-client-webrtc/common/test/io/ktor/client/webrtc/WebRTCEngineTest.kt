/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.test.dispatcher.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.*

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

    private suspend fun negotiate(pc1: WebRtcPeerConnection, pc2: WebRtcPeerConnection) {
        // Create and set offer
        val offer = pc1.createOffer()
        pc1.setLocalDescription(offer)
        pc2.setRemoteDescription(offer)

        // Create and set answer
        val answer = pc2.createAnswer()
        pc2.setLocalDescription(answer)
        pc1.setRemoteDescription(answer)
    }

    private fun CoroutineScope.setupIceExchange(
        pc1: WebRtcPeerConnection,
        pc2: WebRtcPeerConnection,
        jobs: MutableList<Job> = mutableListOf()
    ) {
        // Collect ICE candidates from both peers
        val iceCandidates1 = Channel<WebRTC.IceCandidate>(Channel.UNLIMITED)
        val iceCandidates2 = Channel<WebRTC.IceCandidate>(Channel.UNLIMITED)

        val iceExchangeJobs = arrayOf(
            launch { pc1.iceCandidateFlow.collect { iceCandidates1.send(it) } },
            launch { pc2.iceCandidateFlow.collect { iceCandidates2.send(it) } },
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

    private fun <T> StateFlow<T>.collectAsConflatedChannel(scope: CoroutineScope, jobs: MutableList<Job>): Channel<T> {
        val channel = Channel<T>(Channel.CONFLATED)
        jobs.add(scope.launch { collect { channel.send(it) } })
        return channel
    }

    @Test
    fun testEstablishPeerConnection() = testConnection(realtime = true) { pc1 ->
        client.createPeerConnection().use { pc2 ->
            val cnt = atomic(0)
            val negotiationNeededCnt = Channel<Int>(Channel.CONFLATED)
            pc1.onNegotiationNeeded { launch { negotiationNeededCnt.send(cnt.incrementAndGet()) } }
            pc2.onNegotiationNeeded { launch { negotiationNeededCnt.send(cnt.incrementAndGet()) } }

            val jobs = mutableListOf<Job>()

            val iceConnectionState1 = pc1.iceConnectionStateFlow.collectAsConflatedChannel(this, jobs)
            val iceConnectionState2 = pc2.iceConnectionStateFlow.collectAsConflatedChannel(this, jobs)
            val iceGatheringState1 = pc1.iceGatheringStateFlow.collectAsConflatedChannel(this, jobs)
            val iceGatheringState2 = pc2.iceGatheringStateFlow.collectAsConflatedChannel(this, jobs)
            val connectionState1 = pc1.connectionStateFlow.collectAsConflatedChannel(this, jobs)
            val connectionState2 = pc2.connectionStateFlow.collectAsConflatedChannel(this, jobs)
            val signalingState1 = pc1.signalingStateFlow.collectAsConflatedChannel(this, jobs)
            val signalingState2 = pc2.signalingStateFlow.collectAsConflatedChannel(this, jobs)

            assertEquals(WebRTC.IceConnectionState.NEW, iceConnectionState1.receive())
            assertEquals(WebRTC.IceConnectionState.NEW, iceConnectionState2.receive())
            assertEquals(WebRTC.SignalingState.CLOSED, signalingState1.receive())
            assertEquals(WebRTC.SignalingState.CLOSED, signalingState2.receive())
            assertEquals(WebRTC.ConnectionState.NEW, connectionState1.receive())
            assertEquals(WebRTC.ConnectionState.NEW, connectionState2.receive())
            assertEquals(WebRTC.IceGatheringState.NEW, iceGatheringState1.receive())
            assertEquals(WebRTC.IceGatheringState.NEW, iceGatheringState2.receive())

            setupIceExchange(pc1, pc2, jobs)

            // Add audio tracks for both connections
            pc1.addTrack(client.createAudioTrack(WebRTCMedia.AudioTrackConstraints()))
            pc2.addTrack(client.createAudioTrack(WebRTCMedia.AudioTrackConstraints()))

            negotiate(pc1, pc2)

            fun connectionEstablished(): Boolean =
                pc1.iceConnectionStateFlow.value.isSuccessful() &&
                    pc2.iceConnectionStateFlow.value.isSuccessful() &&
                    pc1.signalingStateFlow.value == WebRTC.SignalingState.STABLE &&
                    pc2.signalingStateFlow.value == WebRTC.SignalingState.STABLE &&
                    pc1.iceGatheringStateFlow.value == WebRTC.IceGatheringState.COMPLETE &&
                    pc2.iceGatheringStateFlow.value == WebRTC.IceGatheringState.COMPLETE

            withTimeout(5000) {
                // Exchange ICE candidates
                while (!connectionEstablished()) {
                    select {
                        iceConnectionState1.onReceiveCatching { it.getOrThrow() }
                        iceConnectionState2.onReceiveCatching { it.getOrThrow() }
                        signalingState1.onReceiveCatching { it.getOrThrow() }
                        signalingState2.onReceiveCatching { it.getOrThrow() }
                        iceGatheringState1.onReceiveCatching { it.getOrThrow() }
                        iceGatheringState2.onReceiveCatching { it.getOrThrow() }
                    }
                }
            }

            val validConnectionStates = listOf(WebRTC.ConnectionState.CONNECTED, WebRTC.ConnectionState.CONNECTING)
            assertContains(validConnectionStates, connectionState1.receive())
            assertContains(validConnectionStates, connectionState2.receive())

            assertEquals(2, negotiationNeededCnt.receive())
            pc1.restartIce()

            withTimeout(5000) {
                while (cnt.value < 3) {
                    negotiationNeededCnt.receive()
                }
            }

            jobs.forEach { it.cancel() }
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

    @Test
    fun receiveRemoteTracks() = runTestWithPermissions(realTime = true) {
        client.createPeerConnection().use { pc1 ->
            client.createPeerConnection().use { pc2 ->
                val negotiationNeededCnt = atomic(0)
                pc1.onNegotiationNeeded { negotiationNeededCnt.incrementAndGet() }
                pc2.onNegotiationNeeded { negotiationNeededCnt.incrementAndGet() }

                val remoteTracks1 = Channel<Operation<WebRTCMedia.Track>>(Channel.UNLIMITED)
                val remoteTracks2 = Channel<Operation<WebRTCMedia.Track>>(Channel.UNLIMITED)

                val collectTracks1Job = launch { pc1.remoteTracksFlow.collect { remoteTracks1.send(it) } }
                val collectTracks2Job = launch { pc2.remoteTracksFlow.collect { remoteTracks2.send(it) } }

                pc1.addTrack(client.createAudioTrack(WebRTCMedia.AudioTrackConstraints()))
                pc1.addTrack(client.createVideoTrack(WebRTCMedia.VideoTrackConstraints()))

                val audioSender = pc2.addTrack(client.createAudioTrack(WebRTCMedia.AudioTrackConstraints()))
                pc2.addTrack(client.createVideoTrack(WebRTCMedia.VideoTrackConstraints()))

                negotiate(pc1, pc2)

                // Check if remote tracks are emitted
                withTimeout(5000) {
                    for (remoteTracks in listOf(remoteTracks1, remoteTracks2)) {
                        val tracks = arrayOf(remoteTracks.receive(), remoteTracks.receive())
                        assertTrue(tracks.all { it is Add })
                        assertEquals(1, tracks.filter { it.item.kind === WebRTCMedia.TrackType.AUDIO }.size)
                        assertEquals(1, tracks.filter { it.item.kind === WebRTCMedia.TrackType.VIDEO }.size)
                    }
                }

                // remove audio track at pc2, needs renegotiation to work
                assertTrue(negotiationNeededCnt.value >= 2)
                pc2.removeTrack(audioSender)
                negotiate(pc1, pc2)

                // Check if the remote track is removed
                withTimeout(5000) {
                    val removedTrack = remoteTracks1.receive()
                    assertTrue(removedTrack is Remove)
                    assertEquals(WebRTCMedia.TrackType.AUDIO, removedTrack.item.kind)
                }

                collectTracks1Job.cancel()
                collectTracks2Job.cancel()
            }
        }
    }
}
