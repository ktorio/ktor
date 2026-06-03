/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.client.webrtc.utils.*
import io.ktor.utils.io.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@IgnoreJvm
@IgnoreDesktop
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
        crossinline block: suspend BackgroundTasksScope.(WebRtcPeerConnection, WebRtcPeerConnection) -> Unit
    ): TestResult {
        return runTestWithPermissions(audio = false, video = false, realtime) {
            client.createPeerConnection().use { pc1 ->
                client.createPeerConnection().use { pc2 ->
                    block(pc1, pc2)
                }
            }
        }
    }

    private suspend fun waitForChannel(events: Channel<DataChannelEvent>) = withTimeout(5.seconds) {
        val event = events.receive()
        assertTrue(event is DataChannelEvent.Open, "Expected DataChannelEvent.Open, got $event")
        assertEquals(WebRtc.DataChannel.State.OPEN, event.channel.state)
        event.channel
    }

    private suspend fun WebRtcDataChannel.waitForClose(
        events: Channel<DataChannelEvent>,
        validateState: Boolean = true
    ) {
        val closeEvent = withTimeout(2.seconds) {
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
        if (validateState) {
            assertEquals(WebRtc.DataChannel.State.CLOSED, state)
        }
    }

    @Test
    fun testDataChannelCommunication() = testDataChannel { pc1, pc2 ->
        val dataChannelEvents = pc2.dataChannelEvents.collectToChannel()

        // Create data channel on pc1
        val dataChannel1 = pc1.createDataChannel("test-channel") {
            protocol = "test-protocol"
        }
        assertEquals(WebRtc.DataChannel.State.CONNECTING, dataChannel1.state)

        connect(pc1, pc2)

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

        val receivedMessage = withTimeout(5.seconds) {
            val message = dataChannel2.receive()
            assertTrue(message is WebRtc.DataChannel.Message.Text, "Expected string message")
            message.data
        }
        assertEquals(testMessage, receivedMessage)

        // Test binary message communication
        val testBinaryData = byteArrayOf(1, 2, 3, 4, 5)
        dataChannel2.send(testBinaryData)

        val receivedBinaryMessage = withTimeout(5.seconds) {
            val message = dataChannel1.receive()
            assertTrue(message is WebRtc.DataChannel.Message.Binary)
            message.data
        }
        assertContentEquals(testBinaryData, receivedBinaryMessage)

        // Test bidirectional communication
        dataChannel1.send("Message from pc1")
        dataChannel2.send("Message from pc2")

        val msg1 = withTimeout(2.seconds) { dataChannel2.receiveText() }
        val msg2 = withTimeout(2.seconds) { dataChannel1.receiveText() }

        assertEquals("Message from pc1", msg1)
        assertEquals("Message from pc2", msg2)

        // Test channel closing
        dataChannel1.closeTransport()
        dataChannel2.waitForClose(dataChannelEvents)
    }

    @Test
    fun testDataChannelOptions() = testDataChannel { pc1, pc2 ->
        val dataChannelEvents1 = pc1.dataChannelEvents.collectToChannel()
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

        connect(pc1, pc2)
        waitForChannel(dataChannelEvents1)

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
    fun testDataChannelSendManyMessages() = testDataChannel { pc1, pc2 ->
        val dataChannelEvents = pc2.dataChannelEvents.collectToChannel()
        val dataChannel1 = pc1.createDataChannel("multi-message-test")
        connect(pc1, pc2)

        val dataChannel2 = waitForChannel(dataChannelEvents)

        // Send multiple messages rapidly
        val messageCount = 1000
        repeat(messageCount) { i ->
            dataChannel1.send("Message $i")
        }

        // Receive all messages
        withTimeout(10.seconds) {
            repeat(messageCount) { i ->
                assertEquals("Message $i", dataChannel2.receiveText())
            }
        }
    }

    @Test
    fun testReceivePropagatesCancellationException() = runTest {
        client.createPeerConnection().use { pc1 ->
            val channel = pc1.createDataChannel("test-label")
            val job = launch { channel.receive() }
            yield() // ensure receive() is actually suspended
            job.cancel()
            job.join()
            assertTrue(job.isCancelled)
        }
    }

    @Test
    fun testDataChannelCloseHandling() = testDataChannel { pc1, pc2 ->
        val dataChannel1 = pc1.createDataChannel("close-test")

        val dataChannelEvents1 = pc1.dataChannelEvents.collectToChannel()
        val dataChannelEvents2 = pc2.dataChannelEvents.collectToChannel()

        connect(pc1, pc2)

        val dataChannel2 = waitForChannel(dataChannelEvents2)
        waitForChannel(dataChannelEvents1)

        // Test sending on a closed channel
        dataChannel1.closeTransport()
        dataChannel1.waitForClose(dataChannelEvents1)
        dataChannel2.waitForClose(dataChannelEvents2)

        val sendException1 = assertFailsWith<WebRtcDataChannelClosedException> { dataChannel1.send("Hello") }
        assertNull(sendException1.cause)

        val sendException2 = assertFailsWith<WebRtcDataChannelClosedException> { dataChannel2.send("Hello") }
        assertNull(sendException2.cause)

        val receiveException1 = assertFailsWith<WebRtcDataChannelClosedException> { dataChannel1.receive() }
        assertIs<ClosedReceiveChannelException>(receiveException1.cause)

        val receiveException2 = assertFailsWith<WebRtcDataChannelClosedException> { dataChannel2.receive() }
        assertIs<ClosedReceiveChannelException>(receiveException2.cause)

        assertEquals(null, dataChannel1.tryReceive())
        assertEquals(null, dataChannel2.tryReceive())
    }

    @Test
    fun testCloseIsIdempotent() = testDataChannel { pc1, pc2 ->
        val ch1 = pc1.createDataChannel("idempotent-close")
        val events1 = pc1.dataChannelEvents.collectToChannel()
        val events2 = pc2.dataChannelEvents.collectToChannel()
        connect(pc1, pc2)
        val ch2 = waitForChannel(events2)
        waitForChannel(events1)

        // Multiple close() calls must not throw
        ch1.close()
        ch1.close()

        // don't check state because object is in undefined state after close
        // and could throw exception on field access etc.
        ch1.waitForClose(events1, validateState = false)
        ch2.waitForClose(events2, validateState = false)

        ch1.close()
        ch2.close()
        ch2.close()
    }

    @Test
    fun testDataChannelBufferedAmountLowEvent() = testDataChannel { pc1, pc2 ->
        val dataChannelEvents1 = pc1.dataChannelEvents.collectToChannel()
        val dataChannelEvents2 = pc2.dataChannelEvents.collectToChannel()

        // Create data channel on pc1
        val dataChannel1 = pc1.createDataChannel("buffered-amount-test")
        connect(pc1, pc2)

        val dataChannel2 = waitForChannel(dataChannelEvents2)
        waitForChannel(dataChannelEvents1)

        val threshold = 1000L
        dataChannel1.setBufferedAmountLowThreshold(threshold)

        val largeData = List(1111) { it.toByte() }.toByteArray()
        dataChannel1.send(largeData)

        // Now wait for the BufferedAmountLow event
        val bufferedAmountLowEvent = withTimeout(5.seconds) {
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
