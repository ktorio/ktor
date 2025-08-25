/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.rs

import io.ktor.client.webrtc.*
import io.ktor.client.webrtc.rs.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.TestResult
import uniffi.ktor_client_webrtc.MediaHandler
import uniffi.ktor_client_webrtc.MediaSample
import uniffi.ktor_client_webrtc.createVideoH264Track
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class WebRtcMediaTest {

    private lateinit var client: WebRtcClient

    private fun testConnection(
        realtime: Boolean = false,
        block: suspend CoroutineScope.(WebRtcPeerConnection, MutableList<Job>) -> Unit
    ): TestResult {
        return runTestWithTimeout(realtime) { jobs ->
            client.createPeerConnection().use { block(it, jobs) }
        }
    }

    @BeforeTest
    fun setup() {
        client = WebRtcClient(RustWebRtc) {
            mediaTrackFactory = MockMediaDevices()
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

                val audioTransferJob = launch {
                    repeat(10) {
                        audioTrack1.getNative().writeData(ByteArray(960), 20.milliseconds) // 20ms of audio data
                        audioTrack2.getNative().writeData(ByteArray(960), 20.milliseconds)
                        delay(20)
                    }
                }.also { jobs.add(it) }

                val videoTransferJob = launch {
                    repeat(10) {
                        videoTrack1.getNative().writeData(ByteArray(1250), 33.milliseconds) // ~30fps video frame
                        videoTrack2.getNative().writeData(ByteArray(1250), 33.milliseconds)
                        delay(33)
                    }
                }.also { jobs.add(it) }

                // Check if remote tracks are emitted
                withTimeout(5000) {
                    listOf(remoteTracks1, remoteTracks2).forEach { remoteTracks ->
                        val tracks = listOf(remoteTracks.receive(), remoteTracks.receive())
                        assertTrue(tracks.all { it is TrackEvent.Add })
                        assertEquals(1, tracks.filter { it.track.kind === WebRtcMedia.TrackType.AUDIO }.size)
                        assertEquals(1, tracks.filter { it.track.kind === WebRtcMedia.TrackType.VIDEO }.size)
                    }
                }

                // assert that remote tracks are replayed
                val remoteTracks3 = pc2.trackEvents.collectToChannel(this, jobs)
                withTimeout(5000) {
                    val tracks = arrayOf(remoteTracks3.receive(), remoteTracks3.receive())
                    assertTrue(tracks.all { it is TrackEvent.Add })
                    assertEquals(1, tracks.filter { it.track.kind === WebRtcMedia.TrackType.AUDIO }.size)
                    assertEquals(1, tracks.filter { it.track.kind === WebRtcMedia.TrackType.VIDEO }.size)
                }

                audioTransferJob.join()
                videoTransferJob.join()

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

                override fun onClose() {
                    receivedSamples.close()
                }
            },
            startReadingRtp = true
        )
        return receivedSamples
    }

    private suspend fun WebRtcMedia.Track.repeatSending(data: ByteArray, duration: Duration) {
        repeat(10) {
            // Write sample to local track (this will be sent over RTP)
            getNative().writeData(data, duration)
            delay(duration)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testMediaSinkDataTransfer() = testConnection(realtime = true) { pc1, jobs ->
        client.createPeerConnection().use { pc2 ->
            val remoteTracks2 = pc2.trackEvents.collectToChannel(this, jobs)

            client.createAudioTrack().use { localAudioTrack ->
                pc1.addTrack(localAudioTrack)
                negotiate(pc1, pc2)

                val testData = ByteArray(8) { it.toByte() }
                localAudioTrack.repeatSending(testData, 20.milliseconds)

                // Wait for remote tracks to be available
                val remoteAudioTrack = withTimeout(5000) {
                    val event = remoteTracks2.receive()
                    assertTrue(event is TrackEvent.Add)
                    assertEquals(WebRtcMedia.TrackType.AUDIO, event.track.kind)
                    event.track as RustMediaTrack
                }

                // Create sink for the remote audio track
                val receivedSamples = remoteAudioTrack.collectSamples()

                // Wait for data to be received at the sink
                val receivedSample = withTimeout(5000) { receivedSamples.receive() }

                assertContentEquals(
                    testData,
                    receivedSample.data,
                    "Received data should match sent data"
                )
                assertEquals(0u, receivedSample.prevDroppedPackets)
                assertEquals(0u, receivedSample.prevPaddingPackets)

                pc1.removeTrack(localAudioTrack)
                negotiate(pc1, pc2)

                withTimeout(5000) {
                    assertTrue(receivedSamples.isClosedForSend)
                }
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

                launch {
                    localVideoTrack.repeatSending(MockMediaDevices.H264_FRAME.value, 33.milliseconds)
                }.also { jobs.add(it) }

                // Wait for remote video track
                val remoteVideoTrack = withTimeout(5000) {
                    val event = remoteTracks2.receive()
                    assertTrue(event is TrackEvent.Add)
                    assertEquals(WebRtcMedia.TrackType.VIDEO, event.track.kind)
                    event.track as RustMediaTrack
                }

                val receivedVideoSamples = remoteVideoTrack.collectSamples()

                // Wait for video data to be received
                val receivedSample = withTimeout(5000) { receivedVideoSamples.receive() }

                assertContentEquals(MockMediaDevices.H264_FRAME.value, receivedSample.data, "Video data should match")
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

                // Start data transmission, so the other peer emits `on_add_track` event
                // Only after that, we can set a sink on the track.
                // Before the sink is set, a couple of samples could be lost (usually 2-3).
                val samplesCount = 10
                launch {
                    repeat(samplesCount) { index ->
                        localAudioTrack
                            .getNative()
                            .writeData(ByteArray(8) { (index + 1).toByte() }, 20.milliseconds)
                        delay(20.milliseconds)
                    }
                }

                val remoteAudioTrack = withTimeout(5000) {
                    remoteTracks2.receive().track as RustMediaTrack
                }
                val receivedSamples = remoteAudioTrack.collectSamples()

                // Wait for all samples to be received
                withTimeout(10_000) {
                    var receivedSample = receivedSamples.receive()
                    val firstReceivedIndex = receivedSample.data[0]
                    assertTrue(firstReceivedIndex <= 5, "Should lose up to 5 samples")

                    // the last one is not emitted
                    for (i in (firstReceivedIndex + 1)..<samplesCount) {
                        receivedSample = receivedSamples.receive()
                        assertTrue(receivedSample.data.all { it == i.toByte() })
                    }
                }
            }
        }
    }
}
