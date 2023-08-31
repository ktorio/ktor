/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.frames

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.connections.*
import io.ktor.network.quic.errors.*
import io.ktor.network.quic.frames.base.*
import io.ktor.network.quic.packets.*
import io.ktor.network.quic.util.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.test.*

class FrameReadWriteTest {
    @Test
    fun testPaddingFrame() = frameTest(
        writeFrames = {
            writePadding()
        }
    )

    @Test
    fun testPingFrame() = frameTest(
        writeFrames = {
            writePing()
        }
    )

    @Test
    fun testAckFrame() {
        val params = quicTransportParameters { ack_delay_exponent = 2 }
        val ranges1 = List(8) { 18L - it * 2 }
        val ranges2 = listOf<Long>(20, 17, 11, 3)
        val ranges3 = listOf<Long>(1 shl 23, 1 shl 22, 1 shl 21, 1 shl 19)
        val ranges4 = listOf<Long>(0, 0)

        var errCnt = 0

        frameTest(
            parameters = params,
            expectedBytesToLeft = 4L,
            writeFrames = { packetBuilder ->
                writeACK(84, params.ack_delay_exponent, ranges1)

                writeACK(POW_2_60, params.ack_delay_exponent, ranges2)

                // first malformed ACK Frame
                writeCustomFrame(packetBuilder, QUICFrameType.ACK, shouldFailOnRead = true) {
                    writeVarInt(10) // Largest Acknowledged
                    writeVarInt(1) // ACK Delay
                    writeVarInt(2) // ACK Range Count
                    writeVarInt(1) // First ACK Range

                    writeVarInt(2) // gap
                    writeVarInt(10) // on read should be error here
                }

                // second malformed ACK Frame
                writeCustomFrame(packetBuilder, QUICFrameType.ACK, shouldFailOnRead = true) {
                    writeVarInt(5) // Largest Acknowledged
                    writeVarInt(1) // ACK Delay
                    writeVarInt(2) // ACK Range Count
                    writeVarInt(1) // First ACK Range

                    writeVarInt(6) // on read should be error here
                }

                writeACKWithECN(4, params.ack_delay_exponent, ranges3, 42, 4242, 424242)

                writeACK(4, params.ack_delay_exponent, ranges4)

                assertFails("Odd ACK ranges") {
                    writeACK(1, 1, listOf(3))
                }

                assertFails("Ascending ACK ranges") {
                    writeACK(1, 1, List(4) { 1L + it })
                }
            },
            validator = {
                validateACK { ackDelay, ackRanges ->
                    assertEquals(84, ackDelay, "ACK delay 1")
                    assertContentEquals(ranges1, ackRanges, "ACK Ranges 1")
                }

                validateACK { ackDelay, ackRanges ->
                    assertEquals(POW_2_60, ackDelay, "ACK delay 2")
                    assertContentEquals(ranges2, ackRanges, "ACK Ranges 2")
                }

                validateACKWithECN { ackDelay, ackRanges, ect0, ect1, ectCE ->
                    assertEquals(4, ackDelay, "ACK delay 3")
                    assertContentEquals(ranges3, ackRanges, "ACK Ranges 3")
                    assertEquals(42, ect0, "ECT0")
                    assertEquals(4242, ect1, "ECT1")
                    assertEquals(424242, ectCE, "ECT_CE")
                }

                validateACK { ackDelay, ackRanges ->
                    assertEquals(4, ackDelay, "ACK delay 4")
                    assertContentEquals(ranges4, ackRanges, "ACK Ranges 4")
                }
            },
            onReaderError = expectError(QUICProtocolTransportError.FRAME_ENCODING_ERROR, QUICFrameType.ACK) {
                errCnt++
            }
        )

        assertEquals(errCnt, 2, "Expected 2 errors to occur")
    }

    @Test
    fun testResetStream() = frameTest(
        writeFrames = {
            writeResetStream(1, AppError(42), 13)
        },
        validator = {
            validateResetStream { streamId, applicationProtocolErrorCode, finalSize ->
                assertEquals(streamId, 1, "Stream id")
                assertEquals(42, applicationProtocolErrorCode.intCode, "Application protocol error code")
                assertEquals(finalSize, 13, "Final size")
            }
        }
    )

    @Test
    fun testStopSending() = frameTest(
        writeFrames = {
            writeStopSending(1, AppError(42))
        },
        validator = {
            validateStopSending { streamId, applicationProtocolErrorCode ->
                assertEquals(streamId, 1, "Stream id")
                assertEquals(42, applicationProtocolErrorCode.intCode, "Application protocol error code")
            }
        }
    )

    @Test
    fun testCrypto() {
        val bytes1 = byteArrayOf(0x00, 0x01, 0x03, 0x04)
        val bytes2 = byteArrayOf(0x00, 0x04, 0x08, 0x12, 0x16)

        var errCount = 0

        frameTest(
            expectedBytesToLeft = 5,
            writeFrames = { packetBuilder ->
                writeCrypto(1, bytes1)

                writeCustomFrame(packetBuilder, QUICFrameType.CRYPTO, shouldFailOnRead = true) {
                    writeVarInt(POW_2_62 - 1) // Offset
                    writeVarInt(5) // Length -- should fail here
                }

                writeCustomFrame(packetBuilder, QUICFrameType.CRYPTO, shouldFailOnRead = true) {
                    writeVarInt(0) // Offset
                    writeVarInt(6) // Length
                    writeFully(bytes2) // Crypto Data -- should fail here
                }

                assertFails("Offset + length exceeds 2^62 - 1") {
                    writeCrypto(POW_2_62, byteArrayOf())
                }
            },
            validator = {
                validateCrypto { offset, cryptoData ->
                    assertEquals(1, offset, "Offset")
                    assertContentEquals(bytes1, cryptoData, "Crypto Data")
                }
            },
            onReaderError = expectError(QUICProtocolTransportError.FRAME_ENCODING_ERROR, QUICFrameType.CRYPTO) {
                errCount++
            }
        )

        assertEquals(2, errCount, "Expected 2 errors to occur")
    }

    @Test
    fun testNewToken() {
        val token1 = byteArrayOf(0x00, 0x01, 0x02)
        var errCnt = 0

        frameTest(
            expectedBytesToLeft = 1,
            writeFrames = { packetBuilder ->
                writeNewToken(token1)

                assertFails("Token is empty") {
                    writeNewToken(byteArrayOf())
                }

                writeCustomFrame(packetBuilder, QUICFrameType.NEW_TOKEN, shouldFailOnRead = true) {
                    writeVarInt(0) // token length -- should fail here
                }

                writeCustomFrame(packetBuilder, QUICFrameType.NEW_TOKEN, shouldFailOnRead = true) {
                    writeVarInt(3) // Token Length
                    writeFully(byteArrayOf(0x00)) // Token -- should fail here
                }
            },
            validator = {
                validateNewToken { token ->
                    assertContentEquals(token1, token, "Token")
                }
            },
            onReaderError = expectError(QUICProtocolTransportError.FRAME_ENCODING_ERROR, QUICFrameType.NEW_TOKEN) {
                errCnt++
            }
        )

        assertEquals(2, errCnt, "Expected 2 errors to occur")
    }

    @Test
    fun testStream() {
        val bytes1 = byteArrayOf(0x00, 0x00)
        val bytes2 = byteArrayOf(0x01, 0x12, 0x11)

        frameTest(
            writeFrames = {
                writeStream(1, 1, specifyLength = true, fin = true, bytes1)

                writeStream(1, null, specifyLength = false, fin = false, bytes2)

                assertFails("Offset + Length exceeds 2^62 - 1") {
                    writeStream(1, POW_2_62 - 2, specifyLength = false, fin = false, bytes2)
                }
            },
            validator = {
                validateStream { streamId, offset, fin, streamData ->
                    assertEquals(1, streamId, "Stream Id 1")
                    assertEquals(1, offset, "Offset 1")
                    assertEquals(true, fin, "FIN 1")
                    assertContentEquals(bytes1, streamData, "Stream Data")
                }

                validateStream { streamId, offset, fin, streamData ->
                    assertEquals(1, streamId, "Stream Id 1")
                    assertEquals(0, offset, "Offset 1")
                    assertEquals(false, fin, "FIN 1")
                    assertContentEquals(bytes2, streamData, "Stream Data")
                }
            }
        )
    }

    @Test
    fun testStream2() {
        val bytes1 = byteArrayOf(0x00, 0x00)

        var errCnt = 0

        frameTest(
            expectedBytesToLeft = 2,
            writeFrames = { packetBuilder ->
                writeCustomFrame(packetBuilder, QUICFrameType.STREAM_LEN, shouldFailOnRead = true) {
                    writeVarInt(1) // Stream Id
                    writeVarInt(3) // Length
                    writeFully(bytes1) // Stream data -- should fail here
                }
            },
            onReaderError = expectError(QUICProtocolTransportError.FRAME_ENCODING_ERROR, QUICFrameType.STREAM_LEN) {
                errCnt++
            }
        )

        assertEquals(1, errCnt, "Expected 1 error to occur")
    }

    @Test
    fun testMaxData() = frameTest(
        writeFrames = {
            writeMaxData(1)
        },
        validator = {
            validateMaxData { maximumData ->
                assertEquals(1, maximumData, "Maximum Data")
            }
        }
    )

    @Test
    fun testMaxStreamData() = frameTest(
        writeFrames = {
            writeMaxStreamData(1, 1)
        },
        validator = {
            validateMaxStreamData { streamId, maximumStreamData ->
                assertEquals(1, streamId, "Stream Id")
                assertEquals(1, maximumStreamData, "Maximum Stream Data")
            }
        }
    )

    @Test
    fun testMaxStreams() {
        var errCnt = 0

        frameTest(
            writeFrames = { packetBuilder ->
                writeMaxStreamsBidirectional(1)
                writeMaxStreamsUnidirectional(1)

                assertFails("Maximum streams exceeds 2^60") {
                    writeMaxStreamsBidirectional(POW_2_60 + 1)
                }
                assertFails("Maximum streams exceeds 2^60") {
                    writeMaxStreamsUnidirectional(POW_2_60 + 1)
                }

                writeCustomFrame(packetBuilder, QUICFrameType.MAX_STREAMS_BIDIRECTIONAL, shouldFailOnRead = true) {
                    writeVarInt(POW_2_60 + 1)
                }

                writeCustomFrame(packetBuilder, QUICFrameType.MAX_STREAMS_UNIDIRECTIONAL, shouldFailOnRead = true) {
                    writeVarInt(POW_2_60 + 1)
                }
            },
            validator = {
                validateMaxStreamsBidirectional { maximumStreams ->
                    assertEquals(1, maximumStreams, "Maximum Streams 1")
                }
                validateMaxStreamsUnidirectional { maximumStreams ->
                    assertEquals(1, maximumStreams, "Maximum Streams 2")
                }
            },
            onReaderError = expectError(
                QUICProtocolTransportError.FRAME_ENCODING_ERROR,
                QUICFrameType.MAX_STREAMS_UNIDIRECTIONAL,
                QUICFrameType.MAX_STREAMS_BIDIRECTIONAL,
            ) {
                errCnt++
            }
        )

        assertEquals(2, errCnt, "Expected 2 error to occur")
    }

    @Test
    fun testdataBlocked() = frameTest(
        writeFrames = {
            writeDataBlocked(1)
        },
        validator = {
            validateDataBlocked { maximumData ->
                assertEquals(1, maximumData, "Maximum Data")
            }
        }
    )

    @Test
    fun testStreamDataBlocked() = frameTest(
        writeFrames = {
            writeStreamDataBlocked(1, 1)
        },
        validator = {
            validateStreamDataBlocked { streamId, maximumStreamData ->
                assertEquals(1, streamId, "Stream Id")
                assertEquals(1, maximumStreamData, "Maximum Stream Data")
            }
        }
    )

    @Test
    fun testStreamsBlocked() {
        var errCnt = 0

        frameTest(
            writeFrames = { packetBuilder ->
                writeStreamsBlockedBidirectional(1)
                writeStreamsBlockedUnidirectional(1)

                assertFails("Maximum streams exceeds 2^60") {
                    writeStreamsBlockedBidirectional(POW_2_60 + 1)
                }
                assertFails("Maximum streams exceeds 2^60") {
                    writeStreamsBlockedUnidirectional(POW_2_60 + 1)
                }

                writeCustomFrame(packetBuilder, QUICFrameType.STREAMS_BLOCKED_BIDIRECTIONAL, shouldFailOnRead = true) {
                    writeVarInt(POW_2_60 + 1)
                }

                writeCustomFrame(packetBuilder, QUICFrameType.STREAMS_BLOCKED_UNIDIRECTIONAL, shouldFailOnRead = true) {
                    writeVarInt(POW_2_60 + 1)
                }
            },
            validator = {
                validateStreamsBlockedBidirectional { maximumStreams ->
                    assertEquals(1, maximumStreams, "Maximum Streams 1")
                }
                validateStreamsBlockedUnidirectional { maximumStreams ->
                    assertEquals(1, maximumStreams, "Maximum Streams 2")
                }
            },
            onReaderError = expectError(
                QUICProtocolTransportError.FRAME_ENCODING_ERROR,
                QUICFrameType.STREAMS_BLOCKED_BIDIRECTIONAL,
                QUICFrameType.STREAMS_BLOCKED_UNIDIRECTIONAL,
            ) {
                errCnt++
            }
        )

        assertEquals(2, errCnt, "Expected 2 errors to occur")
    }

    @Test
    fun testNewConnectionId() {
        val bytes1 = ByteArray(2) { 0x00 }.asCID()
        val bytes2 = ByteArray(16) { 0x00 }

        var errCnt = 0

        frameTest(
            expectedBytesToLeft = 1,
            writeFrames = { packetBuilder ->
                writeNewConnectionId(2, 1, bytes1, bytes2)

                assertFails("Connection id length not in 1..20 (1)") {
                    writeNewConnectionId(2, 1, ByteArray(0).asCID(), bytes2)
                }
                assertFails("Connection id length not in 1..20 (2)") {
                    writeNewConnectionId(2, 1, ByteArray(21).asCID(), bytes2)
                }
                assertFails("Retire Prior To >= Sequence Number") {
                    writeNewConnectionId(1, 2, ByteArray(0).asCID(), bytes2)
                }
                assertFails("Stateless Reset Token != 16 bytes") {
                    writeNewConnectionId(2, 1, bytes1, ByteArray(10))
                }

                writeCustomFrame(packetBuilder, QUICFrameType.NEW_CONNECTION_ID, shouldFailOnRead = true) {
                    writeVarInt(1) // Sequence Number
                    writeVarInt(2) // Retire Prior To -- should fail here
                }

                writeCustomFrame(packetBuilder, QUICFrameType.NEW_CONNECTION_ID, shouldFailOnRead = true) {
                    writeVarInt(2) // Sequence Number
                    writeVarInt(1) // Retire Prior To
                    writeByte(0) // Length -- should fail here
                }

                writeCustomFrame(packetBuilder, QUICFrameType.NEW_CONNECTION_ID, shouldFailOnRead = true) {
                    writeVarInt(2) // Sequence Number
                    writeVarInt(1) // Retire Prior To
                    writeByte(21) // Length -- should fail here
                }

                writeCustomFrame(packetBuilder, QUICFrameType.NEW_CONNECTION_ID, shouldFailOnRead = true) {
                    writeVarInt(2) // Sequence Number
                    writeVarInt(1) // Retire Prior To
                    writeByte(2) // Length
                    writeFully(byteArrayOf(0x00)) // Connection Id -- should fail here
                }
            },
            validator = {
                validateNewConnectionId { sequenceNumber, retirePriorTo, connectionId, resetToken ->
                    assertEquals(2, sequenceNumber, "Sequence Number")
                    assertEquals(1, retirePriorTo, "Retire Prior To")
                    assertContentEquals(bytes1.value, connectionId.value, "Connection Id")
                    assertContentEquals(bytes2, resetToken, "Stateless Reset Token")
                }
            },
            onReaderError = expectError(
                QUICProtocolTransportError.FRAME_ENCODING_ERROR,
                QUICFrameType.NEW_CONNECTION_ID,
            ) {
                errCnt++
            }
        )

        assertEquals(4, errCnt, "Expected 4 errors to occur")
    }

    @Test
    fun testNewConnectionId2() {
        var errCnt = 0

        frameTest(
            expectedBytesToLeft = 3,
            writeFrames = { packetBuilder ->
                writeCustomFrame(packetBuilder, QUICFrameType.NEW_CONNECTION_ID, shouldFailOnRead = true) {
                    writeVarInt(2) // Sequence Number
                    writeVarInt(1) // Retire Prior To
                    writeByte(1) // Length
                    writeFully(byteArrayOf(0x00)) // Connection Id
                    writeFully(byteArrayOf(0x00, 0x00, 0x00)) // Stateless Reset Token -- should fail here
                }
            },
            onReaderError = expectError(
                QUICProtocolTransportError.FRAME_ENCODING_ERROR,
                QUICFrameType.NEW_CONNECTION_ID,
            ) {
                errCnt++
            }
        )

        assertEquals(1, errCnt, "Expected 1 error to occur")
    }

    @Test
    fun testRetireConnectionId() = frameTest(
        writeFrames = {
            writeRetireConnectionId(1)
        },
        validator = {
            validateRetireConnectionId { sequenceNumber ->
                assertEquals(1, sequenceNumber, "Sequence Number")
            }
        }
    )

    @Test
    fun testPathChallenge() {
        val data1 = ByteArray(8) { it.toByte() }
        val data2 = ByteArray(4) { it.toByte() }

        var errCnt = 0

        frameTest(
            expectedBytesToLeft = 4,
            writeFrames = { packetBuilder ->
                writePathChallenge(data1)

                assertFails("Data is not 8 bytes") {
                    writePathChallenge(data2)
                }

                writeCustomFrame(packetBuilder, QUICFrameType.PATH_CHALLENGE, shouldFailOnRead = true) {
                    writeFully(data2)
                }
            },
            validator = {
                validatePathChallenge { data ->
                    assertContentEquals(data1, data, "Data")
                }
            },
            onReaderError = expectError(QUICProtocolTransportError.FRAME_ENCODING_ERROR, QUICFrameType.PATH_CHALLENGE) {
                errCnt++
            }
        )

        assertEquals(1, errCnt, "Expected 1 error to occur")
    }

    @Test
    fun testPathResponse() {
        val data1 = ByteArray(8) { it.toByte() }
        val data2 = ByteArray(4) { it.toByte() }

        var errCnt = 0

        frameTest(
            expectedBytesToLeft = 4,
            writeFrames = { packetBuilder ->
                writePathResponse(data1)

                assertFails("Data is not 8 bytes") {
                    writePathResponse(data2)
                }

                writeCustomFrame(packetBuilder, QUICFrameType.PATH_RESPONSE, shouldFailOnRead = true) {
                    writeFully(data2)
                }
            },
            validator = {
                validatePathResponse { data ->
                    assertContentEquals(data1, data, "Data")
                }
            },
            onReaderError = expectError(QUICProtocolTransportError.FRAME_ENCODING_ERROR, QUICFrameType.PATH_RESPONSE) {
                errCnt++
            }
        )

        assertEquals(1, errCnt, "Expected 1 error to occur")
    }

    @Test
    fun testConnectionClose() {
        val bytes1 = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)

        var errCnt = 0
        frameTest(
            expectedBytesToLeft = 1,
            writeFrames = { packetBuilder ->
                writeConnectionCloseWithAppError(AppError(42), bytes1)

                writeConnectionCloseWithTransportError(
                    errorCode = QUICProtocolTransportError.AEAD_LIMIT_REACHED,
                    frameTypeV1 = QUICFrameType.PATH_RESPONSE,
                    reasonPhrase = bytes1
                )

                writeConnectionCloseWithTransportError(
                    errorCode = QUICProtocolTransportError.AEAD_LIMIT_REACHED,
                    frameTypeV1 = QUICFrameType.PATH_RESPONSE,
                    reasonPhrase = byteArrayOf()
                )

                writeConnectionCloseWithTransportError(
                    errorCode = QUICCryptoHandshakeTransportError(0x01u),
                    frameTypeV1 = null,
                    reasonPhrase = bytes1
                )

                writeCustomFrame(packetBuilder, QUICFrameType.CONNECTION_CLOSE_APP_ERR, shouldFailOnRead = true) {
                    AppError(42).writeToFrame(packetBuilder) // Error code
                    writeVarInt(2) // Reason Phrase Length
                    writeFully(byteArrayOf(0x00)) // Reason Phrase -- should fail here
                }
            },
            validator = {
                validateConnectionCloseWithAppError { errorCode, reasonPhrase ->
                    assertEquals(42, errorCode.intCode, "Error code 1")
                    assertContentEquals(bytes1, reasonPhrase, "Reason phrase 1")
                }
                validateConnectionCloseWithTransportError { errorCode, frameType, reasonPhrase ->
                    assertEquals(QUICProtocolTransportError.AEAD_LIMIT_REACHED, errorCode, "Error code 2")
                    assertEquals(QUICFrameType.PATH_RESPONSE, frameType, "Frame Type 2")
                    assertContentEquals(bytes1, reasonPhrase, "Reason phrase 2")
                }
                validateConnectionCloseWithTransportError { errorCode, frameType, reasonPhrase ->
                    assertEquals(QUICProtocolTransportError.AEAD_LIMIT_REACHED, errorCode, "Error code 3")
                    assertEquals(QUICFrameType.PATH_RESPONSE, frameType, "Frame Type 3")
                    assertContentEquals(byteArrayOf(), reasonPhrase, "Reason phrase 3")
                }
                validateConnectionCloseWithTransportError { errorCode, frameType, reasonPhrase ->
                    assertTrue(errorCode is QUICCryptoHandshakeTransportError, "Error is crypto 4")
                    assertEquals(0x01u, errorCode.tlsAlertCode, "Error code 4")
                    assertEquals(QUICFrameType.PADDING, frameType, "Frame Type 4")
                    assertContentEquals(bytes1, reasonPhrase, "Reason phrase 4")
                }
            },
            onReaderError = expectError(
                QUICProtocolTransportError.FRAME_ENCODING_ERROR,
                QUICFrameType.CONNECTION_CLOSE_APP_ERR,
            ) {
                errCnt++
            }
        )

        assertEquals(1, errCnt, "Expected 1 error to occur")
    }

    @Test
    fun testHandshakeDone() = frameTest(
        writeFrames = {
            writeHandshakeDone()
        }
    )

    private fun frameTest(
        parameters: QUICTransportParameters = quicTransportParameters(),
        expectedBytesToLeft: Long = 0,
        writeFrames: suspend TestFrameWriter.(BytePacketBuilder) -> Unit,
        validator: ReadFramesValidator.() -> Unit = {},
        onReaderError: (QUICTransportError, QUICFrameType) -> Unit = { _, _ -> },
    ) = runBlocking {
        val builder = BytePacketBuilder()
        val writer = TestFrameWriter(TestPacketSendHandler(builder))

        writer.writeFrames(builder)

        val payload = builder.build()
        val frameValidator = ReadFramesValidator().apply(validator)
        val processor = TestFrameProcessor(frameValidator, writer.expectedFrames)

        val packet = QUICOneRTTPacket(
            destinationConnectionID = QUICConnectionID.EMPTY,
            spinBit = false,
            keyPhase = false,
            packetNumber = 0,
            payload = payload
        )

        for (i in 0 until writer.writtenFramesCnt) {
            FrameReader.readFrame(processor, packet, parameters, maxCIDLength = 20u, onReaderError)
        }

        assertEquals(expectedBytesToLeft, payload.remaining, "Wrong number of remaining bytes")

        processor.assertNoExpectedFramesLeft()
    }

    @Suppress("SameParameterValue")
    private fun expectError(
        expectedError: QUICTransportError,
        vararg expectedFrames: QUICFrameType,
        onErr: () -> Unit,
    ): (QUICTransportError, QUICFrameType) -> Unit {
        return { actualError, actualFrame ->
            if (actualError == expectedError && actualFrame in expectedFrames) {
                onErr()
            } else {
                fail("Unexpected error ${actualError.toDebugString()} while reading $actualFrame frame")
            }
        }
    }
}
