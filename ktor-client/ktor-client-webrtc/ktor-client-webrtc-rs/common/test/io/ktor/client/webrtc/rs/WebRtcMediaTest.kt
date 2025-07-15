/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.rs

import io.ktor.client.webrtc.*
import io.ktor.client.webrtc.rs.utils.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class WebRtcMediaTest {

    private lateinit var client: WebRtcClient

    private fun testConnection(
        realtime: Boolean = false,
        block: suspend CoroutineScope.(WebRtcPeerConnection, MutableList<Job>) -> Unit
    ) {
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
            val readJobs = withTimeout(5000) {
                listOf(remoteTracks1, remoteTracks2).flatMap { remoteTracks ->
                    val tracks = listOf(remoteTracks.receive(), remoteTracks.receive())
                    assertTrue(tracks.all { it is TrackEvent.Add })
                    assertEquals(1, tracks.filter { it.track.kind === WebRtcMedia.TrackType.AUDIO }.size)
                    assertEquals(1, tracks.filter { it.track.kind === WebRtcMedia.TrackType.VIDEO }.size)
                    tracks.map {
                        launch { it.track.getNative().readAll() }.also { job -> jobs.add(job) }
                    }
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
            negotiate(pc1, pc2)

            readJobs.joinAll() // ensure all reads are completed

            // Check if the remote track is removed
            val removedTrack = remoteTracks1.receive()
            assertTrue(removedTrack is TrackEvent.Remove)
            assertEquals(WebRtcMedia.TrackType.AUDIO, removedTrack.track.kind)
        }
    }
}
