/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.test.dispatcher.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.*

// Create different WebRtc engine implementation to be tested for every platform.
expect fun createTestWebRtcClient(): WebRtcClient

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

@IgnoreJvm
@IgnorePosix
class WebRtcEngineTest {

    private lateinit var client: WebRtcClient

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
        client = createTestWebRtcClient()
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
        assertEquals(WebRtc.SessionDescriptionType.OFFER, offer.type)
        assertTrue(offer.sdp.isNotEmpty(), "SDP should not be empty")
        assertTrue(offer.sdp.contains("v=0"), "SDP should contain version information")
    }

    @Test
    fun testCreateAnswer() = testConnection(realtime = true) { offerPeerConnection ->
        client.createPeerConnection().use { answerPeerConnection ->
            // Create and set offer
            val offer = offerPeerConnection.createOffer()
            offerPeerConnection.setLocalDescription(offer)
            answerPeerConnection.setRemoteDescription(offer)

            // Create answer
            val answer = answerPeerConnection.createAnswer()

            assertNotNull(answer, "Answer should be created successfully")
            assertEquals(WebRtc.SessionDescriptionType.ANSWER, answer.type)
            assertTrue(answer.sdp.isNotEmpty(), "SDP should not be empty")

            delay(1000)
            assertEquals(offer, offerPeerConnection.localDescription)
            assertEquals(offer, answerPeerConnection.remoteDescription)
        }
    }

    @Test
    fun testIceCandidateCollection() = testConnection(realtime = true) { peerConnection ->
        val receivedCandidates = Channel<WebRtc.IceCandidate>(1)

        val job = launch {
            peerConnection.iceCandidates.collect { receivedCandidates.trySend(it) }
        }

        // Create track
        val audioTrack = client.createAudioTrack(WebRtcMedia.AudioTrackConstraints())
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
        val audioTrack = client.createAudioTrack(WebRtcMedia.AudioTrackConstraints())

        assertNotNull(audioTrack, "Audio track should be created successfully")
        assertEquals(WebRtcMedia.TrackType.AUDIO, audioTrack.kind)
        assertTrue(audioTrack.enabled, "Audio track should be enabled by default")

        audioTrack.close()
    }

    @Test
    fun testCreateVideoTrack() = runTestWithPermissions {
        val videoTrack = client.createVideoTrack(WebRtcMedia.VideoTrackConstraints())

        assertNotNull(videoTrack, "Video track should be created successfully")
        assertEquals(WebRtcMedia.TrackType.VIDEO, videoTrack.kind)
        assertTrue(videoTrack.enabled, "Video track should be enabled by default")

        videoTrack.close()
    }

    @Test
    fun testAddRemoveTrack() = testConnection { pc ->
        val audioTrack = client.createAudioTrack(WebRtcMedia.AudioTrackConstraints())
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
        val audioTrack = client.createAudioTrack(WebRtcMedia.AudioTrackConstraints())
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
        val iceCandidates1 = Channel<WebRtc.IceCandidate>(Channel.UNLIMITED)
        val iceCandidates2 = Channel<WebRtc.IceCandidate>(Channel.UNLIMITED)

        val iceExchangeJobs = arrayOf(
            launch { pc1.iceCandidates.collect { iceCandidates1.send(it) } },
            launch { pc2.iceCandidates.collect { iceCandidates2.send(it) } },
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
            val jobs = mutableListOf<Job>()

            val cnt = atomic(0)
            val negotiationNeededCnt = Channel<Int>(Channel.CONFLATED)
            launch {
                pc1.negotiationNeeded.collect { negotiationNeededCnt.send(cnt.incrementAndGet()) }
            }.also { jobs.add(it) }
            launch {
                pc2.negotiationNeeded.collect { negotiationNeededCnt.send(cnt.incrementAndGet()) }
            }.also { jobs.add(it) }

            val iceConnectionState1 = pc1.iceConnectionState.collectAsConflatedChannel(this, jobs)
            val iceConnectionState2 = pc2.iceConnectionState.collectAsConflatedChannel(this, jobs)
            val iceGatheringState1 = pc1.iceGatheringState.collectAsConflatedChannel(this, jobs)
            val iceGatheringState2 = pc2.iceGatheringState.collectAsConflatedChannel(this, jobs)
            val connectionState1 = pc1.state.collectAsConflatedChannel(this, jobs)
            val connectionState2 = pc2.state.collectAsConflatedChannel(this, jobs)
            val signalingState1 = pc1.signalingState.collectAsConflatedChannel(this, jobs)
            val signalingState2 = pc2.signalingState.collectAsConflatedChannel(this, jobs)

            assertEquals(WebRtc.IceConnectionState.NEW, iceConnectionState1.receive())
            assertEquals(WebRtc.IceConnectionState.NEW, iceConnectionState2.receive())
            assertEquals(WebRtc.SignalingState.STABLE, signalingState1.receive())
            assertEquals(WebRtc.SignalingState.STABLE, signalingState2.receive())
            assertEquals(WebRtc.ConnectionState.NEW, connectionState1.receive())
            assertEquals(WebRtc.ConnectionState.NEW, connectionState2.receive())
            assertEquals(WebRtc.IceGatheringState.NEW, iceGatheringState1.receive())
            assertEquals(WebRtc.IceGatheringState.NEW, iceGatheringState2.receive())

            setupIceExchange(pc1, pc2, jobs)

            // Add audio tracks for both connections
            pc1.addTrack(client.createAudioTrack(WebRtcMedia.AudioTrackConstraints()))
            pc2.addTrack(client.createAudioTrack(WebRtcMedia.AudioTrackConstraints()))

            negotiate(pc1, pc2)

            fun connectionEstablished(): Boolean =
                pc1.iceConnectionState.value.isSuccessful() &&
                    pc2.iceConnectionState.value.isSuccessful() &&
                    pc1.signalingState.value == WebRtc.SignalingState.STABLE &&
                    pc2.signalingState.value == WebRtc.SignalingState.STABLE &&
                    pc1.iceGatheringState.value == WebRtc.IceGatheringState.COMPLETE &&
                    pc2.iceGatheringState.value == WebRtc.IceGatheringState.COMPLETE

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

            val validConnectionStates = listOf(WebRtc.ConnectionState.CONNECTED, WebRtc.ConnectionState.CONNECTING)
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
        assertFailsWith<WebRtc.IceException> {
            val invalidCandidate = WebRtc.IceCandidate(
                candidate = "invalid candidate string",
                sdpMid = "0",
                sdpMLineIndex = 0
            )
            pc.addIceCandidate(invalidCandidate)
        }
    }

    @Test
    fun testInvalidDescription() = testConnection { pc ->
        assertFailsWith<WebRtc.SdpException> {
            val remote = WebRtc.SessionDescription(WebRtc.SessionDescriptionType.OFFER, "invalid description")
            pc.setRemoteDescription(remote)
        }
        assertFailsWith<WebRtc.SdpException> {
            val remote = WebRtc.SessionDescription(WebRtc.SessionDescriptionType.ANSWER, "invalid description")
            pc.setLocalDescription(remote)
        }
        assertEquals(null, pc.localDescription)
        assertEquals(null, pc.remoteDescription)
    }

    @Test
    fun testStatsCollection() = testConnection(realtime = true) { peerConnection ->
        val audioTrack = client.createAudioTrack(WebRtcMedia.AudioTrackConstraints())
        peerConnection.addTrack(audioTrack)

        val stats = Channel<List<WebRtc.Stats>>()

        val statsCollectionJob = launch {
            peerConnection.stats.collect { stats.trySend(it) }
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
                val collectNegotiationEventsJob1 = launch {
                    pc1.negotiationNeeded.collect { negotiationNeededCnt.incrementAndGet() }
                }
                val collectNegotiationEventsJob2 = launch {
                    pc2.negotiationNeeded.collect { negotiationNeededCnt.incrementAndGet() }
                }

                val remoteTracks1 = Channel<TrackEvent>(Channel.UNLIMITED)
                val remoteTracks2 = Channel<TrackEvent>(Channel.UNLIMITED)

                val collectTracks1Job = launch { pc1.trackEvents.collect { remoteTracks1.send(it) } }
                val collectTracks2Job = launch { pc2.trackEvents.collect { remoteTracks2.send(it) } }

                pc1.addTrack(client.createAudioTrack(WebRtcMedia.AudioTrackConstraints()))
                pc1.addTrack(client.createVideoTrack(WebRtcMedia.VideoTrackConstraints()))

                val audioSender = pc2.addTrack(client.createAudioTrack(WebRtcMedia.AudioTrackConstraints()))
                pc2.addTrack(client.createVideoTrack(WebRtcMedia.VideoTrackConstraints()))

                negotiate(pc1, pc2)

                // Check if remote tracks are emitted
                withTimeout(5000) {
                    for (remoteTracks in listOf(remoteTracks1, remoteTracks2)) {
                        val tracks = arrayOf(remoteTracks.receive(), remoteTracks.receive())
                        assertTrue(tracks.all { it is TrackEvent.Add })
                        assertEquals(1, tracks.filter { it.track.kind === WebRtcMedia.TrackType.AUDIO }.size)
                        assertEquals(1, tracks.filter { it.track.kind === WebRtcMedia.TrackType.VIDEO }.size)
                    }
                }

                // assert that remote tracks are replayed
                val remoteTracks3 = Channel<TrackEvent>(Channel.UNLIMITED)
                val collectTracks3Job = launch { pc2.trackEvents.collect { remoteTracks3.send(it) } }
                withTimeout(5000) {
                    val tracks = arrayOf(remoteTracks3.receive(), remoteTracks3.receive())
                    assertTrue(tracks.all { it is TrackEvent.Add })
                    assertEquals(1, tracks.filter { it.track.kind === WebRtcMedia.TrackType.AUDIO }.size)
                    assertEquals(1, tracks.filter { it.track.kind === WebRtcMedia.TrackType.VIDEO }.size)
                }

                // remove audio track at pc2, needs renegotiation to work
                assertTrue(negotiationNeededCnt.value >= 1)
                pc2.removeTrack(audioSender)
                negotiate(pc1, pc2)

                // Check if the remote track is removed
                withTimeout(5000) {
                    val removedTrack = remoteTracks1.receive()
                    assertTrue(removedTrack is TrackEvent.Remove)
                    assertEquals(WebRtcMedia.TrackType.AUDIO, removedTrack.track.kind)
                }

                collectTracks1Job.cancel()
                collectTracks2Job.cancel()
                collectTracks3Job.cancel()
                collectNegotiationEventsJob1.cancel()
                collectNegotiationEventsJob2.cancel()
            }
        }
    }
}
