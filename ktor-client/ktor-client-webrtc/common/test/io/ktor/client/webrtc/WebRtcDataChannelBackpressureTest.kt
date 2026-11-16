/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import kotlin.test.*

class WebRtcDataChannelBackpressureTest {

    @Test
    fun `default receive capacity is bounded`() = runTest {
        val channel = TestWebRtcDataChannel()
        val capacity = DataChannelReceiveOptions.DEFAULT_CAPACITY

        repeat(capacity) { assertTrue(channel.emit("message-$it").isSuccess) }
        assertTrue(channel.emit("overflow").isFailure)
    }

    @Test
    fun `overflow closes the channel and emits a single error`() = runTest {
        val undelivered = mutableListOf<String>()
        val channel = TestWebRtcDataChannel {
            capacity = 2
            onUndeliveredElement = { undelivered.add(it.textOrThrow()) }
        }

        channel.emit("first")
        channel.emit("second")
        channel.emit("overflow")
        channel.emit("after-close")

        assertEquals(1, channel.closeTransportCalls)
        val errors = channel.events.dataChannelEvents.replayCache
        assertEquals(1, errors.size)
        val error = assertIs<DataChannelEvent.Error>(errors.single())
        assertSame(channel, error.channel)
        assertContains(error.reason, "'test'")
        assertEquals(listOf("overflow"), undelivered)

        assertEquals("first", channel.receiveText())
        assertEquals("second", channel.receiveText())
        val exception = assertFailsWith<WebRtc.DataChannelClosedException> { channel.receive() }
        assertIs<WebRtc.IOException>(exception.cause)
    }

    @Test
    fun `drop oldest keeps the channel open`() = runTest {
        val channel = TestWebRtcDataChannel {
            capacity = 2
            onOverflow = DataChannelBufferOverflow.DropOldest
        }

        repeat(4) { assertTrue(channel.emit("message-$it").isSuccess) }

        assertEquals(0, channel.closeTransportCalls)
        assertTrue(channel.events.dataChannelEvents.replayCache.isEmpty())
        assertEquals("message-2", channel.receiveText())
        assertEquals("message-3", channel.receiveText())
    }

    @Test
    fun `drop latest keeps the channel open`() = runTest {
        val channel = TestWebRtcDataChannel {
            capacity = 2
            onOverflow = DataChannelBufferOverflow.DropLatest
        }

        repeat(4) { assertTrue(channel.emit("message-$it").isSuccess) }

        assertEquals(0, channel.closeTransportCalls)
        assertEquals("message-0", channel.receiveText())
        assertEquals("message-1", channel.receiveText())
    }

    @Test
    fun `unlimited capacity never overflows`() = runTest {
        val channel = TestWebRtcDataChannel { capacity = Channel.UNLIMITED }

        repeat(DataChannelReceiveOptions.DEFAULT_CAPACITY * 2) { assertTrue(channel.emit("message-$it").isSuccess) }
        assertEquals(0, channel.closeTransportCalls)
    }

    @Test
    fun `non-positive capacity is rejected`() {
        assertFailsWith<IllegalArgumentException> { TestWebRtcDataChannel { capacity = Channel.RENDEZVOUS } }
        assertFailsWith<IllegalArgumentException> { TestWebRtcDataChannel { capacity = Channel.CONFLATED } }
    }

    @Suppress("DEPRECATION")
    @Test
    fun `deprecated onBufferOverflow maps to onOverflow`() {
        val options = DataChannelReceiveOptions()
        assertEquals(BufferOverflow.SUSPEND, options.onBufferOverflow)

        options.onBufferOverflow = BufferOverflow.DROP_OLDEST
        assertEquals(DataChannelBufferOverflow.DropOldest, options.onOverflow)

        options.onBufferOverflow = BufferOverflow.DROP_LATEST
        assertEquals(DataChannelBufferOverflow.DropLatest, options.onOverflow)

        options.onOverflow = DataChannelBufferOverflow.DropOldest
        assertEquals(BufferOverflow.DROP_OLDEST, options.onBufferOverflow)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `deprecated suspend is rejected for bounded capacity`() {
        assertFailsWith<IllegalArgumentException> {
            TestWebRtcDataChannel { onBufferOverflow = BufferOverflow.SUSPEND }
        }
        assertFailsWith<IllegalArgumentException> {
            TestWebRtcDataChannel {
                capacity = 8
                onBufferOverflow = BufferOverflow.SUSPEND
            }
        }
    }

    private class TestWebRtcDataChannel(
        receiveOptions: DataChannelReceiveOptions.() -> Unit = {}
    ) : WebRtcDataChannel(
        WebRtcConnectionEventsEmitter(WebRtcConnectionConfig()),
        DataChannelReceiveOptions().apply(receiveOptions)
    ) {
        val events: WebRtcConnectionEvents get() = eventsEmitter
        var closeTransportCalls = 0

        override val id: Int? = null
        override val label: String = "test"
        override var state: WebRtc.DataChannel.State = WebRtc.DataChannel.State.OPEN
        override val bufferedAmount: Long = 0
        override val bufferedAmountLowThreshold: Long = 0
        override val maxPacketLifeTime: Int? = null
        override val maxRetransmits: Int? = null
        override val negotiated: Boolean = false
        override val ordered: Boolean = true
        override val protocol: String = ""

        fun emit(text: String) = emitMessage(WebRtc.DataChannel.Message.Text(text))

        override fun setBufferedAmountLowThreshold(threshold: Long) = TODO()

        override suspend fun send(text: String) = TODO()

        override suspend fun send(bytes: ByteArray) = TODO()

        override fun closeTransport() {
            closeTransportCalls++
            state = WebRtc.DataChannel.State.CLOSED
            stopReceivingMessages()
        }
    }
}
