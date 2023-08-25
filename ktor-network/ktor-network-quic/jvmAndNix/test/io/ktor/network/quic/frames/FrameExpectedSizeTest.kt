/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.frames

import io.ktor.network.quic.connections.*
import io.ktor.network.quic.errors.*
import io.ktor.network.quic.util.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.test.*

class FrameExpectedSizeTest {
    @Test
    fun padding() = sizeTest {
        assertExpectedSize(1, "Padding frame") {
            writePadding()
        }
    }

    @Test
    fun ping() = sizeTest {
        assertExpectedSize(1, "Ping frame") {
            writePadding()
        }
    }

    @Test
    fun ack() = sizeTest {
        assertExpectedSize(23, "Ack frame") {
            writeACK(
                ackDelay = 4100,
                ack_delay_exponent = 5,
                ackRanges = listOf(67, 15, 12, 3),
            )
        }

        assertExpectedSize(21 + (POW_2_14 / 2 - 1) * 16, "Ack frame 2") {
            writeACK(
                ackDelay = 4100,
                ack_delay_exponent = 5,
                ackRanges = List(POW_2_14.toInt()) { (it * POW_2_30) }.reversed(),
            )
        }

        assertExpectedSize(26, "Ack frame ect") {
            writeACKWithECN(
                ackDelay = 4100,
                ack_delay_exponent = 5,
                ackRanges = listOf(67, 15, 12, 3),
                ect0 = 1,
                ect1 = 1,
                ectCE = 1,
            )
        }
    }

    @Test
    fun resetStream() = sizeTest {
        assertExpectedSize(5, "ResetStream frame") {
            writeResetStream(4, AppError(1), 100)
        }

        assertExpectedSize(15, "ResetStream frame 2") {
            writeResetStream(POW_2_30, AppError(100), POW_2_14)
        }
    }

    @Test
    fun stopSending() = sizeTest {
        assertExpectedSize(3, "StopSending frame") {
            writeStopSending(4, AppError(1))
        }

        assertExpectedSize(17, "StopSending frame 2") {
            writeStopSending(POW_2_30, AppError(POW_2_30))
        }
    }

    @Test
    fun crypto() = sizeTest {
        assertExpectedSize(6, "Crypto frame") {
            writeCrypto(1, ByteArray(3) { 0x00 })
        }

        assertExpectedSize(6 + POW_2_14, "Crypto frame 2") {
            writeCrypto(1, ByteArray((POW_2_14).toInt()) { 0x00 })
        }
    }

    @Test
    fun newToken() = sizeTest {
        assertExpectedSize(3, "NewToken frame") {
            writeNewToken(byteArrayOf(0x00))
        }
    }

    @Test
    fun stream() = sizeTest {
        assertExpectedSize(5, "Stream frame") {
            writeStream(1, 1, specifyLength = true, fin = true, data = byteArrayOf(0x00))
        }

        assertExpectedSize(4, "Stream frame 2") {
            writeStream(1, null, specifyLength = true, fin = true, data = byteArrayOf(0x00))
        }

        assertExpectedSize(3, "Stream frame 3") {
            writeStream(1, null, specifyLength = false, fin = true, data = byteArrayOf(0x00))
        }
    }

    @Test
    fun maxData() = sizeTest {
        assertExpectedSize(2, "MaxData frame") {
            writeMaxData(1)
        }
    }

    @Test
    fun maxStreamData() = sizeTest {
        assertExpectedSize(3, "MaxStreamData frame") {
            writeMaxStreamData(1, 1)
        }
    }

    @Test
    fun maxStreams() = sizeTest {
        assertExpectedSize(5, "MaxStreams bi frame") {
            writeMaxStreamsBidirectional(POW_2_14)
        }

        assertExpectedSize(9, "MaxStreams uni frame") {
            writeMaxStreamsUnidirectional(POW_2_30)
        }
    }

    @Test
    fun dataBlocked() = sizeTest {
        assertExpectedSize(3, "dataBlocked frame") {
            writeDataBlocked(POW_2_06)
        }
    }

    @Test
    fun streamDataBlocked() = sizeTest {
        assertExpectedSize(7, "StreamDataBlocked frame") {
            writeStreamDataBlocked(POW_2_06, POW_2_14)
        }
    }

    @Test
    fun streamsBlocked() = sizeTest {
        assertExpectedSize(3, "StreamsBlocked bi frame") {
            writeStreamsBlockedBidirectional(POW_2_06)
        }

        assertExpectedSize(5, "StreamsBlocked uni frame") {
            writeStreamsBlockedUnidirectional(POW_2_14)
        }
    }

    @Test
    fun newConnectionId() = sizeTest {
        assertExpectedSize(21, "NewConnectionId frame") {
            writeNewConnectionId(1, 1, QUICConnectionID(byteArrayOf(0x00)), ByteArray(16) { 0x00 })
        }

        assertExpectedSize(26, "NewConnectionId frame 2") {
            writeNewConnectionId(POW_2_14, POW_2_06, QUICConnectionID(byteArrayOf(0x00, 0x00)), ByteArray(16) { 0x00 })
        }
    }

    @Test
    fun retireConnectionId() = sizeTest {
        assertExpectedSize(5, "RetireConnectionId frame") {
            writeRetireConnectionId(POW_2_14)
        }
    }

    @Test
    fun pathChallenge() = sizeTest {
        assertExpectedSize(9, "PathChallenge frame") {
            writePathChallenge(ByteArray(8) { 0x00 })
        }
    }

    @Test
    fun pathResponse() = sizeTest {
        assertExpectedSize(9, "PathResponse frame") {
            writePathResponse(ByteArray(8) { 0x00 })
        }
    }

    @Test
    fun connectionClose() = sizeTest {
        assertExpectedSize(23, "ConnectionClose app frame") {
            writeConnectionCloseWithAppError(AppError(POW_2_14), ByteArray(17) { 0x00 })
        }

        assertExpectedSize(147, "ConnectionClose transport frame") {
            writeConnectionCloseWithTransportError(QUICProtocolTransportError.FRAME_ENCODING_ERROR, null, ByteArray(142) { 0x00 }) // ktlint-disable max-line-length
        }

        assertExpectedSize(148, "ConnectionClose transport frame") {
            writeConnectionCloseWithTransportError(QUICCryptoHandshakeTransportError(0x00u), QUICFrameType.CRYPTO, ByteArray(142) { 0x00 }) // ktlint-disable max-line-length
        }
    }

    @Test
    fun handshakeDone() = sizeTest {
        assertExpectedSize(1, "HandshakeDone frame") {
            writeHandshakeDone()
        }
    }

    private fun sizeTest(body: suspend SizeTest.() -> Unit) = runBlocking {
        SizeTest().body()
    }

    private class SizeTest : PacketSendHandler {
        private var expectedExpectedSizeValue: Int = -1
        private var testMessage: String = ""
        private var invoked = 0

        private val writer by lazy { FrameWriterImpl(this) }

        suspend fun assertExpectedSize(expectedValue: Long, message: String, body: suspend FrameWriter.() -> Unit) {
            assertExpectedSize(expectedValue.toInt(), message, body)
        }

        suspend fun assertExpectedSize(expectedValue: Int, message: String, body: suspend FrameWriter.() -> Unit) {
            expectedExpectedSizeValue = expectedValue
            testMessage = message
            invoked = 0

            writer.body()

            if (invoked != 1) {
                fail("writeFrame method was not invoked or invoked more than once, $testMessage")
            }
        }

        override suspend fun writeFrame(expectedFrameSize: Int, handler: BytePacketBuilder.() -> Unit): Long {
            assertEquals(
                expectedExpectedSizeValue,
                expectedFrameSize,
                "Wrong expectedFrameSize value: $testMessage"
            ) // ktlint-disable max-line-length

            val actualFrameSize = buildPacket(handler).remaining
            assertTrue("Actual size must not be greater than expectedFrameSize value, $testMessage: expectedFrameSize: $expectedFrameSize, actual: $actualFrameSize") { actualFrameSize <= expectedFrameSize } // ktlint-disable max-line-length

            invoked++

            return 0
        }
    }
}
