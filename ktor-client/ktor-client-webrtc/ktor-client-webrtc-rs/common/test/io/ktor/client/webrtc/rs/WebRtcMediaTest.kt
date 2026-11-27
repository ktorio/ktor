/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.rs

import io.ktor.client.webrtc.*
import io.ktor.client.webrtc.rs.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uniffi.ktor_client_webrtc.MediaHandler
import uniffi.ktor_client_webrtc.MediaSample
import uniffi.ktor_client_webrtc.createVideoH264Track
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class WebRtcMediaTest {

    private lateinit var client: WebRtcClient

    val audioDuration = 20.milliseconds
    val videoDuration = 33.milliseconds

    private fun testConnection(
        realtime: Boolean = false,
        block: suspend CoroutineScope.(WebRtcPeerConnection, MutableList<Job>) -> Unit
    ) {
        runTestWithTimeout(realtime) { jobs ->
            client.createPeerConnection().use { block(it, jobs) }
        }
    }

    @BeforeTest
    fun setup() {
        client = WebRtcClient(RustWebRtc) {
            mediaTrackFactory = MockMediaDevices()
            defaultConnectionConfig = {
                // propagate any background exceptions to the test
                exceptionHandler = CoroutineExceptionHandler { _, e -> throw e }
            }
        }
    }

    @AfterTest
    fun cleanup() {
        client.close()
    }

    @Test
    fun testCreateAudioTrack() = runTestWithTimeout {
        client.createAudioTrack().use { audioTrack ->
            assertNotNull(audioTrack, "Audio track should be created successfully")
            assertEquals(WebRtcMedia.TrackType.AUDIO, audioTrack.kind)
            assertTrue(audioTrack.enabled, "Audio track should be enabled by default")
        }
    }

    @Test
    fun testCreateVideoTrack() = runTestWithTimeout {
        client.createVideoTrack().use { videoTrack ->
            assertNotNull(videoTrack, "Video track should be created successfully")
            assertEquals(WebRtcMedia.TrackType.VIDEO, videoTrack.kind)
            assertTrue(videoTrack.enabled, "Video track should be enabled by default")
        }
    }

    @Test
    fun testAddRemoveTrack() = testConnection { pc, _ ->
        client.createAudioTrack().use { audioTrack ->
            val sender = pc.addTrack(audioTrack)

            // Create offer after adding track
            val offerWithTrack = pc.createOffer()
            assertTrue(offerWithTrack.sdp.contains("audio"), "SDP should contain audio media section")

            // Remove track
            pc.removeTrack(sender)
        }
    }

    @Test
    fun testEnableDisableTrack() = runTestWithTimeout {
        client.createAudioTrack().use { audioTrack ->
            assertTrue(audioTrack.enabled, "Track should be enabled by default")

            audioTrack.enable(false)
            assertFalse(audioTrack.enabled, "Track should be disabled after calling enable(false)")

            audioTrack.enable(true)
            assertTrue(audioTrack.enabled, "Track should be enabled after calling enable(true)")
        }
    }

    @Test
    fun receiveRemoteTracks() = testConnection(realtime = true) { pc1, jobs ->
        client.createPeerConnection().use { pc2 ->
            val remoteTracks1 = pc1.trackEvents.collectToChannel(this, jobs)
            val remoteTracks2 = pc2.trackEvents.collectToChannel(this, jobs)

            val audioTrack1 = client.createAudioTrack()
            val videoTrack1 = client.createVideoTrack()
            val audioTrack2 = client.createAudioTrack()
            val videoTrack2 = client.createVideoTrack()

            try {
                pc1.addTrack(audioTrack1)
                pc1.addTrack(videoTrack1)

                val audioSender = pc2.addTrack(audioTrack2)
                pc2.addTrack(videoTrack2)

                negotiate(pc1, pc2)

                val allTracksReceived = MutableStateFlow(false)
                val audioData = ByteArray(960)
                val videoData = ByteArray(1250)
                val audioTransferJobs = listOf(audioTrack1, audioTrack2).map { track ->
                    jobs.launchIn(scope = this) {
                        track.repeatSending(audioData, audioDuration, until = allTracksReceived)
                    }
                }
                val videoTransferJobs = listOf(videoTrack1, videoTrack2).map { track ->
                    jobs.launchIn(scope = this) {
                        track.repeatSending(videoData, videoDuration, until = allTracksReceived)
                    }
                }

                // Check if remote tracks are emitted
                withTimeout(10_000) {
                    listOf(remoteTracks1, remoteTracks2).forEach { remoteTracks ->
                        val tracks = listOf(remoteTracks.receive(), remoteTracks.receive())
                        assertTrue(tracks.all { it is TrackEvent.Add })
                        assertEquals(1, tracks.filter { it.track.kind === WebRtcMedia.TrackType.AUDIO }.size)
                        assertEquals(1, tracks.filter { it.track.kind === WebRtcMedia.TrackType.VIDEO }.size)
                    }
                }
                allTracksReceived.value = true

                // assert that remote tracks are replayed
                val remoteTracks3 = pc2.trackEvents.collectToChannel(this, jobs)
                withTimeout(10_000) {
                    val tracks = arrayOf(remoteTracks3.receive(), remoteTracks3.receive())
                    assertTrue(tracks.all { it is TrackEvent.Add })
                    assertEquals(1, tracks.filter { it.track.kind === WebRtcMedia.TrackType.AUDIO }.size)
                    assertEquals(1, tracks.filter { it.track.kind === WebRtcMedia.TrackType.VIDEO }.size)
                }

                audioTransferJobs.joinAll()
                videoTransferJobs.joinAll()

                // remove audio track at pc2, needs renegotiation to work
                pc2.removeTrack(audioSender)
                pc1.removeTrack(videoTrack1)
                negotiate(pc1, pc2)

                // Check if the remote track is removed
                val removeAudioEvent = remoteTracks1.receive()
                assertTrue(removeAudioEvent is TrackEvent.Remove)
                assertEquals(audioTrack2.id, removeAudioEvent.track.id)
                assertEquals(WebRtcMedia.TrackType.AUDIO, removeAudioEvent.track.kind)

                val removeVideoEvent = remoteTracks2.receive()
                assertTrue(removeVideoEvent is TrackEvent.Remove)
                assertEquals(videoTrack1.id, removeVideoEvent.track.id)
                assertEquals(WebRtcMedia.TrackType.VIDEO, removeVideoEvent.track.kind)
            } finally {
                audioTrack1.close()
                videoTrack1.close()
                audioTrack2.close()
                videoTrack2.close()
            }
        }
    }

    private fun RustMediaTrack.collectSamples(): Channel<MediaSample> {
        val receivedSamples = Channel<MediaSample>(Channel.UNLIMITED)
        setMediaHandler(
            handler = object : MediaHandler {
                override fun onNextSample(sample: MediaSample) {
                    receivedSamples.trySend(sample)
                }
            },
            readPacketsInBackground = true
        )
        return receivedSamples
    }

    private suspend fun WebRtcMedia.Track.repeatSending(
        data: ByteArray,
        interval: Duration,
        until: StateFlow<Boolean>
    ) {
        while (!until.value) {
            getNative().writeData(data, duration = interval)
            delay(duration = interval)
        }
    }

    @Test
    fun testMediaSinkDataTransfer() = testConnection(realtime = true) { pc1, jobs ->
        client.createPeerConnection().use { pc2 ->
            val remoteTracks2 = pc2.trackEvents.collectToChannel(this, jobs)

            client.createAudioTrack().use { localAudioTrack ->
                pc1.addTrack(localAudioTrack)
                negotiate(pc1, pc2)

                val testData = ByteArray(8) { it.toByte() }
                val frameReceived = MutableStateFlow(false)
                jobs.launchIn(scope = this) {
                    localAudioTrack.repeatSending(testData, audioDuration, until = frameReceived)
                }

                // Wait for remote tracks to be available
                val remoteAudioTrack = withTimeout(10_000) {
                    val event = remoteTracks2.receive()
                    assertTrue(event is TrackEvent.Add)
                    assertEquals(WebRtcMedia.TrackType.AUDIO, event.track.kind)
                    event.track as RustMediaTrack
                }

                // Create sink for the remote audio track
                val receivedSamples = remoteAudioTrack.collectSamples()

                // Wait for data to be received at the sink
                val receivedSample = withTimeout(10_000) { receivedSamples.receive() }
                frameReceived.value = true

                assertContentEquals(
                    testData,
                    receivedSample.data,
                    "Received data should match sent data"
                )
                assertEquals(0u, receivedSample.prevDroppedPackets)
                assertEquals(0u, receivedSample.prevPaddingPackets)
            }
        }
    }

    @Test
    fun testVideoSinkDataTransfer() = testConnection(realtime = true) { pc1, jobs ->
        client.createPeerConnection().use { pc2 ->
            val remoteTracks2 = pc2.trackEvents.collectToChannel(this, jobs)
            val h264Track = createVideoH264Track("ktor-track-h264", "ktor-stream")

            RustVideoTrack(nativeTrack = h264Track, coroutineScope = null).use { localVideoTrack ->
                pc1.addTrack(localVideoTrack)
                negotiate(pc1, pc2)

                val frameReceived = MutableStateFlow(false)
                jobs.launchIn(scope = this) {
                    localVideoTrack.repeatSending(
                        data = MockMediaDevices.H264_FRAME.value,
                        interval = videoDuration,
                        until = frameReceived
                    )
                }

                // Wait for remote video track
                val remoteVideoTrack = withTimeout(10_000) {
                    val event = remoteTracks2.receive()
                    assertTrue(event is TrackEvent.Add)
                    assertEquals(WebRtcMedia.TrackType.VIDEO, event.track.kind)
                    event.track as RustMediaTrack
                }

                val receivedVideoSamples = remoteVideoTrack.collectSamples()

                // Wait for video data to be received
                val receivedSample = withTimeout(10_000) { receivedVideoSamples.receive() }
                frameReceived.value = true

                assertContentEquals(
                    MockMediaDevices.H264_FRAME.value,
                    receivedSample.data,
                    "Video data should match"
                )
                assertEquals(0u, receivedSample.prevDroppedPackets, "Dropped packets should still be 0")
                assertEquals(0u, receivedSample.prevPaddingPackets, "Padding packets should still be 0")
            }
        }
    }

    @Test
    fun testSinkMultipleSamples() = testConnection(realtime = true) { pc1, jobs ->
        client.createPeerConnection().use { pc2 ->
            val remoteTracks2 = pc2.trackEvents.collectToChannel(this, jobs)
            client.createAudioTrack().use { localAudioTrack ->
                pc1.addTrack(localAudioTrack)
                negotiate(pc1, pc2)

                val sinkAdded = MutableStateFlow(false)
                jobs.launchIn(scope = this) {
                    val zeroBytes = ByteArray(8) { 0 }
                    localAudioTrack.repeatSending(zeroBytes, audioDuration, sinkAdded)
                }

                val remoteAudioTrack = withTimeout(10_000) {
                    remoteTracks2.receive().track as RustMediaTrack
                }
                val receivedSamples = remoteAudioTrack.collectSamples()
                sinkAdded.value = true

                val samplesIds = 1..10
                jobs.launchIn(scope = this) {
                    for (id in samplesIds) {
                        val data = ByteArray(id) { id.toByte() }
                        localAudioTrack.getNative().writeData(data, audioDuration)
                        delay(audioDuration)
                    }
                }

                // Wait for all samples to be received
                withTimeout(10_000) {
                    do {
                        val sample = receivedSamples.receive() // skip initial samples
                    } while (sample.data[0].toInt() != samplesIds.first)

                    // the last one is not emitted because of WebRTC internal buffering
                    for (i in samplesIds.first + 1 until samplesIds.last) {
                        val sample = receivedSamples.receive()
                        assertTrue(sample.data.all { it.toInt() == i })
                    }
                }
            }
        }
    }
}
