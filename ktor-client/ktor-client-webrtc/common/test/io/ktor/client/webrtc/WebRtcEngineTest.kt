/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.client.webrtc.utils.*
import io.ktor.test.dispatcher.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.test.TestResult
import kotlin.coroutines.CoroutineContext
import kotlin.test.*

@IgnoreJvm
@IgnorePosix
class WebRtcEngineTest {

    private lateinit var client: WebRtcClient

    private fun testConnection(
        realtime: Boolean = false,
        block: suspend CoroutineScope.(WebRtcPeerConnection, MutableList<Job>) -> Unit
    ): TestResult {
        return runTestWithPermissions(audio = true, video = true, realtime) { jobs ->
            client.createPeerConnection().use { block(it, jobs) }
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
    fun testCreatePeerConnection() = testConnection { peerConnection, _ ->
        assertNotNull(peerConnection, "Peer connection should be created successfully")
    }

    @Test
    fun testCreateOffer() = testConnection { peerConnection, _ ->
        val offer = peerConnection.createOffer()

        assertNotNull(offer, "Offer should be created successfully")
        assertEquals(WebRtc.SessionDescriptionType.OFFER, offer.type)
        assertTrue(offer.sdp.isNotEmpty(), "SDP should not be empty")
        assertTrue(offer.sdp.contains("v=0"), "SDP should contain version information")
    }

    @Test
    fun testCreateAnswer() = testConnection(realtime = true) { offerPeerConnection, _ ->
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

            assertEquals(offer, offerPeerConnection.localDescription)
            assertEquals(offer, answerPeerConnection.remoteDescription)
        }
    }

    @Test
    fun testIceCandidateCollection() = testConnection(realtime = true) { peerConnection, jobs ->
        val receivedCandidates = peerConnection.iceCandidates.collectToChannel(this, jobs)

        client.createAudioTrack().use { audioTrack ->
            peerConnection.addTrack(audioTrack)

            // Trigger ICE candidate gathering by creating and setting an offer
            val offer = peerConnection.createOffer()
            peerConnection.setLocalDescription(offer)

            withTimeout(5000) {
                receivedCandidates.receive()
            }
        }
    }

    @Test
    fun testEstablishPeerConnection() = testConnection(realtime = true) { pc1, jobs ->
        client.createPeerConnection().use { pc2 ->

            val cnt = atomic(0)
            val negotiationNeededCnt = Channel<Int>(Channel.CONFLATED)
            launch {
                pc1.negotiationNeeded.collect { negotiationNeededCnt.send(cnt.incrementAndGet()) }
            }.also { jobs.add(it) }
            launch {
                pc2.negotiationNeeded.collect { negotiationNeededCnt.send(cnt.incrementAndGet()) }
            }.also { jobs.add(it) }

            val iceConnectionState1 = pc1.iceConnectionState.collectToChannel(this, jobs)
            val iceConnectionState2 = pc2.iceConnectionState.collectToChannel(this, jobs)
            val iceGatheringState1 = pc1.iceGatheringState.collectToChannel(this, jobs)
            val iceGatheringState2 = pc2.iceGatheringState.collectToChannel(this, jobs)
            val signalingState1 = pc1.signalingState.collectToChannel(this, jobs)
            val signalingState2 = pc2.signalingState.collectToChannel(this, jobs)
            val connectionState1 = pc1.state.collectToChannel(this, jobs)
            val connectionState2 = pc2.state.collectToChannel(this, jobs)

            assertEquals(WebRtc.IceConnectionState.NEW, iceConnectionState1.receive())
            assertEquals(WebRtc.IceConnectionState.NEW, iceConnectionState2.receive())
            assertEquals(WebRtc.IceGatheringState.NEW, iceGatheringState1.receive())
            assertEquals(WebRtc.IceGatheringState.NEW, iceGatheringState2.receive())
            assertEquals(WebRtc.SignalingState.STABLE, signalingState1.receive())
            assertEquals(WebRtc.SignalingState.STABLE, signalingState2.receive())
            assertEquals(WebRtc.ConnectionState.NEW, connectionState1.receive())
            assertEquals(WebRtc.ConnectionState.NEW, connectionState2.receive())

            setupIceExchange(pc1, pc2, jobs)

            // Add audio tracks for both connections
            pc1.addTrack(client.createAudioTrack())
            pc2.addTrack(client.createAudioTrack())

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
                negotiationNeededCnt.receive()
            }
        }
    }

    @Test
    fun testInvalidIceCandidate() = testConnection { pc, _ ->
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
    fun testInvalidDescription() = testConnection { pc, _ ->
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
    fun testStatsCollection() = testConnection(realtime = true) { peerConnection, jobs ->
        client.createAudioTrack().use { audioTrack ->
            peerConnection.addTrack(audioTrack)

            val stats = peerConnection.stats.collectToChannel(this, jobs)

            withTimeout(5000) {
                val firstStats = stats.receive()
                assertEquals(emptyList(), firstStats)

                val realStats = stats.receive()
                assertTrue(realStats.size >= 2)

                assertNotNull(realStats.firstOrNull { it.type == "peer-connection" })

                val mediaSource = realStats.first { it.type == "media-source" }
                assertEquals("audio", mediaSource.props["kind"])
            }
        }
    }

    @Test
    fun testCustomExceptionHandling() = runTestWithRealTime {
        class TestConnection(context: CoroutineContext, config: WebRtcConnectionConfig) :
            MockWebRtcConnection(context, config) {
            override suspend fun getStatistics(): List<WebRtc.Stats> {
                throw IllegalStateException("Ktor is awesome!")
            }
        }

        val mockEngine = object : MockWebRtcEngine() {
            override suspend fun createPeerConnection(config: WebRtcConnectionConfig): WebRtcPeerConnection =
                TestConnection(createConnectionContext(config.coroutineContext), config)
        }

        val channel = Channel<Throwable>(Channel.CONFLATED)
        val exceptionHandler = CoroutineExceptionHandler { _, e -> channel.trySend(e) }

        WebRtcClient(mockEngine).use { client ->
            val connection = client.createPeerConnection {
                coroutineContext = exceptionHandler
                statsRefreshRate = 10
            }

            connection.use {
                withTimeout(1000) {
                    val exception = channel.receive()
                    assertEquals("Ktor is awesome!", exception.message)
                }
            }
        }
    }
}
