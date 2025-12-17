package io.ktor.client.webrtc

import io.ktor.client.webrtc.utils.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.withTimeout
import kotlin.test.*

@IgnoreJvm
@IgnoreDesktop
@OptIn(ExperimentalKtorApi::class)
class WebRtcMediaTest {

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
    fun testCreateAudioTrack() = runTestWithPermissions {
        client.createAudioTrack().use { audioTrack ->
            assertNotNull(audioTrack, "Audio track should be created successfully")
            assertEquals(WebRtcMedia.TrackType.AUDIO, audioTrack.kind)
            assertTrue(audioTrack.enabled, "Audio track should be enabled by default")
        }
    }

    @Test
    fun testCreateVideoTrack() = runTestWithPermissions {
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
    fun testEnableDisableTrack() = runTestWithPermissions {
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

            pc1.addTrack(client.createAudioTrack())
            pc1.addTrack(client.createVideoTrack())

            val audioSender = pc2.addTrack(client.createAudioTrack())
            pc2.addTrack(client.createVideoTrack())

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
            val remoteTracks3 = pc2.trackEvents.collectToChannel(this, jobs)
            withTimeout(5000) {
                val tracks = arrayOf(remoteTracks3.receive(), remoteTracks3.receive())
                assertTrue(tracks.all { it is TrackEvent.Add })
                assertEquals(1, tracks.filter { it.track.kind === WebRtcMedia.TrackType.AUDIO }.size)
                assertEquals(1, tracks.filter { it.track.kind === WebRtcMedia.TrackType.VIDEO }.size)
            }

            pc2.removeTrack(audioSender)

            // remove audio track at pc2, needs renegotiation to work
            negotiate(pc1, pc2)

            // Check if the remote track is removed
            withTimeout(5000) {
                val removedTrack = remoteTracks1.receive()
                assertTrue(removedTrack is TrackEvent.Remove)
                assertEquals(WebRtcMedia.TrackType.AUDIO, removedTrack.track.kind)
            }
        }
    }
}
