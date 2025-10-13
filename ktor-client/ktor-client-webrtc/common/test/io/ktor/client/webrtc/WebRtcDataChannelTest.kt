/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.client.webrtc.utils.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.withTimeout
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalKtorApi::class)
class WebRtcDataChannelTest {

    private lateinit var client: WebRtcClient

    @BeforeTest
    fun setup() {
        client = createTestWebRtcClient()
    }

    @AfterTest
    fun cleanup() {
        client.close()
    }

    private inline fun testDataChannel(
        realtime: Boolean = true,
        crossinline block: suspend CoroutineScope.(WebRtcPeerConnection, WebRtcPeerConnection, MutableList<Job>) -> Unit
    ): TestResult {
        return runTestWithPermissions(audio = false, video = false, realtime) { jobs ->
            client.createPeerConnection().use { pc1 ->
                client.createPeerConnection().use { pc2 ->
                    block(pc1, pc2, jobs)
                }
            }
        }
    }

    private suspend fun waitForChannel(events: Channel<DataChannelEvent>) = withTimeout(5000) {
        val event = events.receive()
        assertTrue(event is DataChannelEvent.Open, "Expected DataChannelEvent.Open, got $event")
        assertEquals(WebRtc.DataChannel.State.OPEN, event.channel.state)
        event.channel
    }

    private suspend fun WebRtcDataChannel.waitForClose(events: Channel<DataChannelEvent>) {
        val closeEvent = withTimeout(2000) {
            while (true) {
                val event = events.receive()
                if (event is DataChannelEvent.Closed) {
                    return@withTimeout event
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("Expected DataChannelEvent.Closed")
        }
        assertEquals(this, closeEvent.channel)
        assertEquals(WebRtc.DataChannel.State.CLOSED, state)
    }

    @Test
    fun testDataChannelCommunication() = testDataChannel { pc1, pc2, jobs ->
        val dataChannelEvents = pc2.dataChannelEvents.collectToChannel(this, jobs)

        // Create data channel on pc1
        val dataChannel1 = pc1.createDataChannel("test-channel") {
            protocol = "test-protocol"
        }
        assertEquals(WebRtc.DataChannel.State.CONNECTING, dataChannel1.state)

        connect(pc1, pc2, jobs)

        val dataChannel2 = waitForChannel(dataChannelEvents)

        // Verify data channel properties
        assertTrue(dataChannel1.ordered)
        // Ordered is not available on Android yet :(
        // assertTrue(dataChannel2.ordered)
        assertEquals("test-channel", dataChannel1.label)
        assertEquals("test-channel", dataChannel2.label)
        assertEquals("test-protocol", dataChannel1.protocol)
        // Protocol is not available on Android yet :(
        // assertEquals("test-protocol", dataChannel2.protocol)
        assertEquals(WebRtc.DataChannel.State.OPEN, dataChannel1.state)
        assertEquals(WebRtc.DataChannel.State.OPEN, dataChannel2.state)

        assertEquals(null, dataChannel2.tryReceive())
        assertEquals(null, dataChannel1.tryReceiveText())
        assertEquals(null, dataChannel2.tryReceiveBinary())

        // Test text message communication
        val testMessage = "Hello from pc1!"
        dataChannel1.send(testMessage)

        val receivedMessage = withTimeout(5000) {
            val message = dataChannel2.receive()
            assertTrue(message is WebRtc.DataChannel.Message.Text, "Expected string message")
            message.data
        }
        assertEquals(testMessage, receivedMessage)

        // Test binary message communication
        val testBinaryData = byteArrayOf(1, 2, 3, 4, 5)
        dataChannel2.send(testBinaryData)

        val receivedBinaryMessage = withTimeout(5000) {
            val message = dataChannel1.receive()
            assertTrue(message is WebRtc.DataChannel.Message.Binary)
            message.data
        }
        assertContentEquals(testBinaryData, receivedBinaryMessage)

        // Test bidirectional communication
        dataChannel1.send("Message from pc1")
        dataChannel2.send("Message from pc2")

        val msg1 = withTimeout(2000) { dataChannel2.receiveText() }
        val msg2 = withTimeout(2000) { dataChannel1.receiveText() }

        assertEquals("Message from pc1", msg1)
        assertEquals("Message from pc2", msg2)

        // Test channel closing
        dataChannel1.closeTransport()
        dataChannel2.waitForClose(dataChannelEvents)
    }

    @Test
    fun testDataChannelOptions() = testDataChannel { pc1, pc2, jobs ->
        val dataChannel = pc1.createDataChannel(label = "options-test") {
            id = 42
            ordered = false
            maxRetransmits = 3
            negotiated = false
            protocol = "custom-protocol"
        }
        assertEquals(null, dataChannel.id, "Expected id to be null before negotiation")
        assertEquals("options-test", dataChannel.label)
        assertEquals("custom-protocol", dataChannel.protocol)
        assertFalse(dataChannel.ordered)
        assertEquals(3, dataChannel.maxRetransmits)
        assertFalse(dataChannel.negotiated)

        connect(pc1, pc2, jobs)

        assertTrue(dataChannel.id!! >= 0, "Expected id to be non-negative after negotiation")

        val dataChannel2 = pc1.createDataChannel(label = "options-test2") {
            id = 42
            negotiated = true
        }
        // negotiated id is set immediately
        assertEquals(42, dataChannel2.id)

        assertFails {
            // maxRetransmits and maxPacketLifeTime can't be specified at the same time
            pc1.createDataChannel(label = "options-test") {
                maxRetransmits = 2
                maxPacketLifeTime = 1000.milliseconds
            }
        }
    }

    @Test
    fun testDataChannelSendManyMessages() = testDataChannel { pc1, pc2, jobs ->
        val dataChannelEvents = pc2.dataChannelEvents.collectToChannel(this, jobs)
        val dataChannel1 = pc1.createDataChannel("multi-message-test")
        connect(pc1, pc2, jobs)

        val dataChannel2 = waitForChannel(dataChannelEvents)

        // Send multiple messages rapidly
        val messageCount = 1000
        repeat(messageCount) { i ->
            dataChannel1.send("Message $i")
        }

        // Receive all messages
        withTimeout(10_000) {
            repeat(messageCount) { i ->
                assertEquals("Message $i", dataChannel2.receiveText())
            }
        }
    }

    @Test
    fun testDataChannelCloseHandling() = testDataChannel { pc1, pc2, jobs ->
        val dataChannel1 = pc1.createDataChannel("close-test")

        val dataChannelEvents1 = pc1.dataChannelEvents.collectToChannel(this, jobs)
        val dataChannelEvents2 = pc2.dataChannelEvents.collectToChannel(this, jobs)

        connect(pc1, pc2, jobs)

        val dataChannel2 = waitForChannel(dataChannelEvents2)

        // Test sending on a closed channel
        dataChannel1.closeTransport()
        dataChannel1.waitForClose(dataChannelEvents1)
        dataChannel2.waitForClose(dataChannelEvents2)

        assertFails { dataChannel1.send("Hello") }
        assertFails { dataChannel2.send("Hello") }
        assertFails { dataChannel1.receive() }
        assertEquals(null, dataChannel2.tryReceive())
    }

    @Test
    @IgnoreJvm
    fun testDataChannelBufferedAmountLowEvent() = testDataChannel { pc1, pc2, jobs ->
        val dataChannelEvents1 = pc1.dataChannelEvents.collectToChannel(this, jobs)
        val dataChannelEvents2 = pc2.dataChannelEvents.collectToChannel(this, jobs)

        // Create data channel on pc1
        val dataChannel1 = pc1.createDataChannel("buffered-amount-test")
        connect(pc1, pc2, jobs)

        val dataChannel2 = waitForChannel(dataChannelEvents2)

        val threshold = 1000L
        dataChannel1.setBufferedAmountLowThreshold(threshold)

        val largeData = List(1111) { it.toByte() }.toByteArray()
        dataChannel1.send(largeData)

        // Now wait for the BufferedAmountLow event
        val bufferedAmountLowEvent = withTimeout(5000) {
            while (true) {
                val event = dataChannelEvents1.receive()
                if (event is DataChannelEvent.BufferedAmountLow) {
                    // assert there was only one event fired
                    assertTrue(dataChannelEvents1.tryReceive().isFailure)
                    return@withTimeout event
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("Expected DataChannelEvent.BufferedAmountLow")
        }

        // Verify the event
        assertEquals(dataChannel1, bufferedAmountLowEvent.channel)
        assertTrue(
            dataChannel1.bufferedAmount <= threshold,
            "Buffered amount should be below threshold when event is fired"
        )

        // Clean up
        dataChannel1.closeTransport()
        dataChannel2.waitForClose(dataChannelEvents2)
    }
}
