/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.client.webrtc.utils.collectToChannel
import io.ktor.client.webrtc.utils.connect
import io.ktor.client.webrtc.utils.createTestWebRtcClient
import io.ktor.client.webrtc.utils.runTestWithPermissions
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.withTimeout
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for the iOS delegate-retention bug.
 *
 * Apple's `RTCDataChannel.delegate` and `RTCPeerConnection.delegate` are both
 * declared `@property(nonatomic, weak)`. Earlier versions of this library passed
 * an anonymous `NSObject` to those setters without keeping a Kotlin-side strong
 * reference, so once Kotlin/Native's ARC observed no remaining references the
 * delegate was deallocated and the framework's weak pointer was nilled out —
 * silently halting message delivery and lifecycle callbacks on the channel.
 *
 * These tests force GC after the channel is established and verify that
 * subsequent traffic continues to flow. They will fail (timeout on `receive()`)
 * against any build of `ktor-client-webrtc` that doesn't hold a strong reference
 * to the iOS delegate.
 */
@OptIn(ExperimentalKtorApi::class, ExperimentalNativeApi::class, NativeRuntimeApi::class)
class IosDelegateRetentionTest {

    private lateinit var client: WebRtcClient

    @BeforeTest
    fun setup() {
        client = createTestWebRtcClient()
    }

    @AfterTest
    fun cleanup() {
        client.close()
    }

    /**
     * Aggressively reclaims unreferenced Kotlin/Native objects so a delegate the
     * library forgot to retain has every opportunity to be freed before we send.
     * The 1 MiB allocation per round goes out of scope before the next iteration's
     * `GC.collect()`, creating real allocation churn to exercise the collector.
     */
    private fun forceGc(rounds: Int = 5) {
        repeat(rounds) {
            @Suppress("UNUSED_VARIABLE")
            val ballast = ByteArray(1 shl 20) // 1 MiB
            GC.collect()
        }
    }

    @Test
    fun `data channel delegate survives GC after setup`(): TestResult = runTestWithPermissions(
        audio = false,
        video = false,
        realTime = true,
    ) { jobs ->
        client.createPeerConnection().use { pc1 ->
            client.createPeerConnection().use { pc2 ->
                val pc2DataChannels = pc2.dataChannelEvents.collectToChannel(this, jobs)

                val local = pc1.createDataChannel("delegate-retention-local")
                connect(pc1, pc2, jobs)

                // Wait for pc2 to receive the remote channel.
                val remote = withTimeout(5000) {
                    val event = pc2DataChannels.receive()
                    assertTrue(event is DataChannelEvent.Open, "Expected Open, got $event")
                    event.channel
                }
                assertEquals(WebRtc.DataChannel.State.OPEN, local.state)
                assertEquals(WebRtc.DataChannel.State.OPEN, remote.state)

                // The bug-reproducing step: nothing in user code is holding the delegates
                // alive — collect now and verify the channel still works in BOTH directions.
                // (Local-channel delegate fires on pc1's send-side `bufferedAmount` etc.;
                // remote-channel delegate fires on pc2's `didReceiveMessage`.)
                forceGc()

                val pc1ToPc2 = "hello after GC"
                local.send(pc1ToPc2)
                val receivedAtPc2 = withTimeout(5000) { remote.receiveText() }
                assertEquals(pc1ToPc2, receivedAtPc2)

                val pc2ToPc1 = "reply after GC"
                remote.send(pc2ToPc1)
                val receivedAtPc1 = withTimeout(5000) { local.receiveText() }
                assertEquals(pc2ToPc1, receivedAtPc1)

                // Second round: GC between every message to make sure delivery stays
                // healthy across repeated reclamation cycles, not just one.
                repeat(8) { i ->
                    forceGc(rounds = 2)
                    local.send("msg-$i")
                    val got = withTimeout(5000) { remote.receiveText() }
                    assertEquals("msg-$i", got)
                }
            }
        }
    }

    @Test
    fun `peer connection delegate survives GC after setup`(): TestResult = runTestWithPermissions(
        audio = false,
        video = false,
        realTime = true,
    ) { jobs ->
        client.createPeerConnection().use { pc1 ->
            client.createPeerConnection().use { pc2 ->
                val pc2DataChannels = pc2.dataChannelEvents.collectToChannel(this, jobs)

                pc1.createDataChannel("pc-delegate-test")

                // GC before negotiation so any unretained PC delegate gets reclaimed
                // before the state-transition callbacks would have fired.
                forceGc()

                connect(pc1, pc2, jobs)

                // Drain pc2's channels flow so the connection settles into CONNECTED.
                withTimeout(5000) { pc2DataChannels.receive() }

                // We must observe CONNECTED on pc1; that only arrives if the PC delegate
                // is still alive to fire `didChangeConnectionState`. `state` is a
                // StateFlow whose current value is replayed to new collectors, so
                // `first { }` will see CONNECTED even if it was reached earlier.
                withTimeout(5000) {
                    pc1.state.first { it == WebRtc.ConnectionState.CONNECTED }
                }
            }
        }
    }
}
