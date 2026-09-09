/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import kotlinx.coroutines.test.*
import kotlin.test.*

class WebRtcDataChannelIteratorTest {

    @Test
    fun testIteratorDrainsBufferedMessagesAfterChannelIsClosed() = runTest {
        val channel = TestWebRtcDataChannel()
        channel.emit(WebRtc.DataChannel.Message.Text("first"))
        channel.emit(WebRtc.DataChannel.Message.Text("second"))
        channel.closeReceiving()

        val messages = channel.toList()

        assertEquals(listOf("first", "second"), messages.map { it.textOrThrow() })
    }

    @Test
    fun testIteratorStopsOnClosedChannelWithoutBufferedMessages() = runTest {
        val channel = TestWebRtcDataChannel()
        channel.closeReceiving()

        assertTrue(channel.toList().isEmpty())
    }

    @Test
    fun testIteratorPreservesMessageOrderAndType() = runTest {
        val channel = TestWebRtcDataChannel()
        val bytes = byteArrayOf(1, 2, 3)
        channel.emit(WebRtc.DataChannel.Message.Text("text"))
        channel.emit(WebRtc.DataChannel.Message.Binary(bytes))
        channel.closeReceiving()

        val messages = channel.toList()

        assertEquals("text", messages[0].textOrThrow())
        assertContentEquals(bytes, messages[1].binaryOrThrow())
    }

    private suspend fun WebRtcDataChannel.toList(): List<WebRtc.DataChannel.Message> = buildList {
        for (message in this@toList) add(message)
    }

    private class TestWebRtcDataChannel : WebRtcDataChannel(DataChannelReceiveOptions()) {
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

        fun emit(message: WebRtc.DataChannel.Message) {
            check(emitMessage(message).isSuccess)
        }

        override fun setBufferedAmountLowThreshold(threshold: Long) = TODO()

        override suspend fun send(text: String) = TODO()

        override suspend fun send(bytes: ByteArray) = TODO()

        fun closeReceiving() {
            state = WebRtc.DataChannel.State.CLOSED
            stopReceivingMessages()
        }

        override fun closeTransport() {
            closeReceiving()
        }
    }
}
