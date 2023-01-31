/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.frames

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.errors.*
import io.ktor.network.quic.frames.base.*
import io.ktor.network.quic.util.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.test.*

class FrameReadWriteTest {
    @Test
    fun testPaddingFrame() = frameTest(
        writeFrames = { packetBuilder ->
            writePadding(packetBuilder)
        }
    )

    @Test
    fun testPingFrame() = frameTest(
        writeFrames = { packetBuilder ->
            writePing(packetBuilder)
        }
    )

    @Test
    fun testAckFrame() {
        val params = TestPacketTransportParameters(ack_delay_exponent = 2)
        val ranges1 = LongArray(8) { 18L - it * 2 }
        val ranges2 = longArrayOf(20, 17, 11, 3)
        val ranges3 = longArrayOf(1 shl 23, 1 shl 22, 1 shl 21, 1 shl 19)

        var errCnt = 0

        frameTest(
            parameters = params,
            expectedBytesToLeft = 4L,
            writeFrames = { packetBuilder ->
                writeACK(packetBuilder, 84, params.ack_delay_exponent, ranges1)

                writeACK(packetBuilder, POW_2_60, params.ack_delay_exponent, ranges2)

                // first malformed ACK Frame
                writeCustomFrame(packetBuilder, FrameType_v1.ACK, shouldFailOnRead = true) {
                    writeVarInt(10) // Largest Acknowledged
                    writeVarInt(1) // ACK Delay
                    writeVarInt(2) // ACK Range Count
                    writeVarInt(1) // First ACK Range

                    writeVarInt(2) // gap
                    writeVarInt(10) // on read should be error here
                }

                // second malformed ACK Frame
                writeCustomFrame(packetBuilder, FrameType_v1.ACK, shouldFailOnRead = true) {
                    writeVarInt(5) // Largest Acknowledged
                    writeVarInt(1) // ACK Delay
                    writeVarInt(2) // ACK Range Count
                    writeVarInt(1) // First ACK Range

                    writeVarInt(6) // on read should be error here
                }

                writeACKWithECN(packetBuilder, 4, params.ack_delay_exponent, ranges3, 42, 4242, 424242)

                assertFails("Odd ACK ranges") {
                    writeACK(packetBuilder, 1, 1, LongArray(3))
                }

                assertFails("Ascending ACK ranges") {
                    writeACK(packetBuilder, 1, 1, LongArray(4) { 1L + it })
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
            },
            onReaderError = expectError(TransportError_v1.FRAME_ENCODING_ERROR, FrameType_v1.ACK) {
                errCnt++
            }
        )

        assertEquals(errCnt, 2, "Expected 2 errors to occur")
    }

    @Test
    fun testResetStream() = frameTest(
        writeFrames = { packetBuilder ->
            writeResetStream(packetBuilder, 1, AppError(42), 13)
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
        writeFrames = { packetBuilder ->
            writeStopSending(packetBuilder, 1, AppError(42))
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
                writeCrypto(packetBuilder, 1, bytes1)

                writeCustomFrame(packetBuilder, FrameType_v1.CRYPTO, shouldFailOnRead = true) {
                    writeVarInt(POW_2_62 - 1) // Offset
                    writeVarInt(5) // Length -- should fail here
                }

                writeCustomFrame(packetBuilder, FrameType_v1.CRYPTO, shouldFailOnRead = true) {
                    writeVarInt(0) // Offset
                    writeVarInt(6) // Length
                    writeFully(bytes2) // Crypto Data -- should fail here
                }

                assertFails("Offset + length exceeds 2^62 - 1") {
                    writeCrypto(packetBuilder, POW_2_62, byteArrayOf())
                }
            },
            validator = {
                validateCrypto { offset, cryptoData ->
                    assertEquals(1, offset, "Offset")
                    assertContentEquals(bytes1, cryptoData, "Crypto Data")
                }
            },
            onReaderError = expectError(TransportError_v1.FRAME_ENCODING_ERROR, FrameType_v1.CRYPTO) {
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
                writeNewToken(packetBuilder, token1)

                assertFails("Token is empty") {
                    writeNewToken(packetBuilder, byteArrayOf())
                }

                writeCustomFrame(packetBuilder, FrameType_v1.NEW_TOKEN, shouldFailOnRead = true) {
                    writeVarInt(0) // token length -- should fail here
                }

                writeCustomFrame(packetBuilder, FrameType_v1.NEW_TOKEN, shouldFailOnRead = true) {
                    writeVarInt(3) // Token Length
                    writeFully(byteArrayOf(0x00)) // Token -- should fail here
                }
            },
            validator = {
                validateNewToken { token ->
                    assertContentEquals(token1, token, "Token")
                }
            },
            onReaderError = expectError(TransportError_v1.FRAME_ENCODING_ERROR, FrameType_v1.NEW_TOKEN) {
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
            writeFrames = { packetBuilder ->
                writeStream(packetBuilder, 1, 1, specifyLength = true, fin = true, bytes1)

                writeStream(packetBuilder, 1, null, specifyLength = false, fin = false, bytes2)

                assertFails("Offset + Length exceeds 2^62 - 1") {
                    writeStream(packetBuilder, 1, POW_2_62 - 2, specifyLength = false, fin = false, bytes2)
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
                writeCustomFrame(packetBuilder, FrameType_v1.STREAM_LEN, shouldFailOnRead = true) {
                    writeVarInt(1) // Stream Id
                    writeVarInt(3) // Length
                    writeFully(bytes1) // Stream data -- should fail here
                }
            },
            onReaderError = expectError(TransportError_v1.FRAME_ENCODING_ERROR, FrameType_v1.STREAM_LEN) {
                errCnt++
            }
        )

        assertEquals(1, errCnt, "Expected 1 error to occur")
    }

    @Test
    fun testMaxData() = frameTest(
        writeFrames = { packetBuilder ->
            writeMaxData(packetBuilder, 1)
        },
        validator = {
            validateMaxData { maximumData ->
                assertEquals(1, maximumData, "Maximum Data")
            }
        }
    )

    @Test
    fun testMaxStreamData() = frameTest(
        writeFrames = { packetBuilder ->
            writeMaxStreamData(packetBuilder, 1, 1)
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
                writeMaxStreamsBidirectional(packetBuilder, 1)
                writeMaxStreamsUnidirectional(packetBuilder, 1)

                assertFails("Maximum streams exceeds 2^60") {
                    writeMaxStreamsBidirectional(packetBuilder, POW_2_60 + 1)
                }
                assertFails("Maximum streams exceeds 2^60") {
                    writeMaxStreamsUnidirectional(packetBuilder, POW_2_60 + 1)
                }

                writeCustomFrame(packetBuilder, FrameType_v1.MAX_STREAMS_BIDIRECTIONAL, shouldFailOnRead = true) {
                    writeVarInt(POW_2_60 + 1)
                }

                writeCustomFrame(packetBuilder, FrameType_v1.MAX_STREAMS_UNIDIRECTIONAL, shouldFailOnRead = true) {
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
                TransportError_v1.FRAME_ENCODING_ERROR,
                FrameType_v1.MAX_STREAMS_UNIDIRECTIONAL,
                FrameType_v1.MAX_STREAMS_BIDIRECTIONAL,
            ) {
                errCnt++
            }
        )

        assertEquals(2, errCnt, "Expected 2 error to occur")
    }

    @Test
    fun testdataBlocked() = frameTest(
        writeFrames = { packetBuilder ->
            writeDataBlocked(packetBuilder, 1)
        },
        validator = {
            validateDataBlocked { maximumData ->
                assertEquals(1, maximumData, "Maximum Data")
            }
        }
    )

    @Test
    fun testStreamDataBlocked() = frameTest(
        writeFrames = { packetBuilder ->
            writeStreamDataBlocked(packetBuilder, 1, 1)
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
                writeStreamsBlockedBidirectional(packetBuilder, 1)
                writeStreamsBlockedUnidirectional(packetBuilder, 1)

                assertFails("Maximum streams exceeds 2^60") {
                    writeStreamsBlockedBidirectional(packetBuilder, POW_2_60 + 1)
                }
                assertFails("Maximum streams exceeds 2^60") {
                    writeStreamsBlockedUnidirectional(packetBuilder, POW_2_60 + 1)
                }

                writeCustomFrame(packetBuilder, FrameType_v1.STREAMS_BLOCKED_BIDIRECTIONAL, shouldFailOnRead = true) {
                    writeVarInt(POW_2_60 + 1)
                }

                writeCustomFrame(packetBuilder, FrameType_v1.STREAMS_BLOCKED_UNIDIRECTIONAL, shouldFailOnRead = true) {
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
                TransportError_v1.FRAME_ENCODING_ERROR,
                FrameType_v1.STREAMS_BLOCKED_BIDIRECTIONAL,
                FrameType_v1.STREAMS_BLOCKED_UNIDIRECTIONAL,
            ) {
                errCnt++
            }
        )

        assertEquals(2, errCnt, "Expected 2 errors to occur")
    }

    @Test
    fun testNewConnectionId() {
        val bytes1 = ByteArray(2) { 0x00 }
        val bytes2 = ByteArray(16) { 0x00 }

        var errCnt = 0

        frameTest(
            expectedBytesToLeft = 1,
            writeFrames = { packetBuilder ->
                writeNewConnectionId(packetBuilder, 2, 1, bytes1, bytes2)

                assertFails("Connection id length not in 1..20 (1)") {
                    writeNewConnectionId(packetBuilder, 2, 1, ByteArray(0), bytes2)
                }
                assertFails("Connection id length not in 1..20 (2)") {
                    writeNewConnectionId(packetBuilder, 2, 1, ByteArray(21), bytes2)
                }
                assertFails("Retire Prior To >= Sequence Number") {
                    writeNewConnectionId(packetBuilder, 1, 2, ByteArray(0), bytes2)
                }
                assertFails("Stateless Reset Token != 16 bytes") {
                    writeNewConnectionId(packetBuilder, 2, 1, bytes1, ByteArray(10))
                }

                writeCustomFrame(packetBuilder, FrameType_v1.NEW_CONNECTION_ID, shouldFailOnRead = true) {
                    writeVarInt(1) // Sequence Number
                    writeVarInt(2) // Retire Prior To -- should fail here
                }

                writeCustomFrame(packetBuilder, FrameType_v1.NEW_CONNECTION_ID, shouldFailOnRead = true) {
                    writeVarInt(2) // Sequence Number
                    writeVarInt(1) // Retire Prior To
                    writeByte(0) // Length -- should fail here
                }

                writeCustomFrame(packetBuilder, FrameType_v1.NEW_CONNECTION_ID, shouldFailOnRead = true) {
                    writeVarInt(2) // Sequence Number
                    writeVarInt(1) // Retire Prior To
                    writeByte(21) // Length -- should fail here
                }

                writeCustomFrame(packetBuilder, FrameType_v1.NEW_CONNECTION_ID, shouldFailOnRead = true) {
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
                    assertContentEquals(bytes1, connectionId, "Connection Id")
                    assertContentEquals(bytes2, resetToken, "Stateless Reset Token")
                }
            },
            onReaderError = expectError(TransportError_v1.FRAME_ENCODING_ERROR, FrameType_v1.NEW_CONNECTION_ID) {
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
                writeCustomFrame(packetBuilder, FrameType_v1.NEW_CONNECTION_ID, shouldFailOnRead = true) {
                    writeVarInt(2) // Sequence Number
                    writeVarInt(1) // Retire Prior To
                    writeByte(1) // Length
                    writeFully(byteArrayOf(0x00)) // Connection Id
                    writeFully(byteArrayOf(0x00, 0x00, 0x00)) // Stateless Reset Token -- should fail here
                }
            },
            onReaderError = expectError(TransportError_v1.FRAME_ENCODING_ERROR, FrameType_v1.NEW_CONNECTION_ID) {
                errCnt++
            }
        )

        assertEquals(1, errCnt, "Expected 1 error to occur")
    }

    @Test
    fun testRetireConnectionId() = frameTest(
        writeFrames = { packetBuilder ->
            writeRetireConnectionId(packetBuilder, 1)
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
                writePathChallenge(packetBuilder, data1)

                assertFails("Data is not 8 bytes") {
                    writePathChallenge(packetBuilder, data2)
                }

                writeCustomFrame(packetBuilder, FrameType_v1.PATH_CHALLENGE, shouldFailOnRead = true) {
                    writeFully(data2)
                }
            },
            validator = {
                validatePathChallenge { data ->
                    assertContentEquals(data1, data, "Data")
                }
            },
            onReaderError = expectError(TransportError_v1.FRAME_ENCODING_ERROR, FrameType_v1.PATH_CHALLENGE) {
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
                writePathResponse(packetBuilder, data1)

                assertFails("Data is not 8 bytes") {
                    writePathResponse(packetBuilder, data2)
                }

                writeCustomFrame(packetBuilder, FrameType_v1.PATH_RESPONSE, shouldFailOnRead = true) {
                    writeFully(data2)
                }
            },
            validator = {
                validatePathResponse { data ->
                    assertContentEquals(data1, data, "Data")
                }
            },
            onReaderError = expectError(TransportError_v1.FRAME_ENCODING_ERROR, FrameType_v1.PATH_RESPONSE) {
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
                writeConnectionCloseWithAppError(packetBuilder, AppError(42), bytes1)

                writeConnectionCloseWithTransportError(
                    packetBuilder = packetBuilder,
                    errorCode = TransportError_v1.AEAD_LIMIT_REACHED,
                    frameTypeV1 = FrameType_v1.PATH_RESPONSE,
                    reasonPhrase = bytes1
                )

                writeConnectionCloseWithTransportError(
                    packetBuilder = packetBuilder,
                    errorCode = TransportError_v1.AEAD_LIMIT_REACHED,
                    frameTypeV1 = FrameType_v1.PATH_RESPONSE,
                    reasonPhrase = byteArrayOf()
                )

                writeConnectionCloseWithTransportError(
                    packetBuilder = packetBuilder,
                    errorCode = CryptoHandshakeError_v1(0x01u),
                    frameTypeV1 = null,
                    reasonPhrase = bytes1
                )

                writeCustomFrame(packetBuilder, FrameType_v1.CONNECTION_CLOSE_APP_ERR, shouldFailOnRead = true) {
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
                    assertEquals(TransportError_v1.AEAD_LIMIT_REACHED, errorCode, "Error code 2")
                    assertEquals(FrameType_v1.PATH_RESPONSE, frameType, "Frame Type 2")
                    assertContentEquals(bytes1, reasonPhrase, "Reason phrase 2")
                }
                validateConnectionCloseWithTransportError { errorCode, frameType, reasonPhrase ->
                    assertEquals(TransportError_v1.AEAD_LIMIT_REACHED, errorCode, "Error code 3")
                    assertEquals(FrameType_v1.PATH_RESPONSE, frameType, "Frame Type 3")
                    assertContentEquals(byteArrayOf(), reasonPhrase, "Reason phrase 3")
                }
                validateConnectionCloseWithTransportError { errorCode, frameType, reasonPhrase ->
                    assertTrue(errorCode is CryptoHandshakeError_v1, "Error is crypto 4")
                    assertEquals(0x01u, errorCode.tlsAlertCode, "Error code 4")
                    assertEquals(FrameType_v1.PADDING, frameType, "Frame Type 4")
                    assertContentEquals(bytes1, reasonPhrase, "Reason phrase 4")
                }
            },
            onReaderError = expectError(TransportError_v1.FRAME_ENCODING_ERROR, FrameType_v1.CONNECTION_CLOSE_APP_ERR) {
                errCnt++
            }
        )

        assertEquals(1, errCnt, "Expected 1 error to occur")
    }

    @Test
    fun testHandshakeDone() = frameTest(
        writeFrames = { packetBuilder ->
            writeHandshakeDone(packetBuilder)
        }
    )

    private fun frameTest(
        parameters: TestPacketTransportParameters = TestPacketTransportParameters(),
        expectedBytesToLeft: Long = 0,
        writeFrames: TestFrameWriter.(BytePacketBuilder) -> Unit,
        validator: ReadFramesValidator.() -> Unit = {},
        onReaderError: (QUICTransportError_v1, FrameType_v1) -> Unit = { _, _ -> },
    ) = runBlocking {
        val builder = BytePacketBuilder()
        val writer = TestFrameWriter()

        writer.writeFrames(builder)

        val packet = builder.build()
        val frameValidator = ReadFramesValidator().apply(validator)
        val processor = TestFrameProcessor(frameValidator, writer.expectedFrames)

        for (i in 0 until writer.writtenFramesCnt) {
            FrameReader.readFrame(processor, packet, parameters, onReaderError)
        }

        assertEquals(expectedBytesToLeft, packet.remaining, "Wrong number of remaining bytes")

        processor.assertNoExpectedFramesLeft()
    }

    @Suppress("SameParameterValue")
    private fun expectError(
        expectedError: QUICTransportError_v1,
        vararg expectedFrames: FrameType_v1,
        onErr: () -> Unit,
    ): (QUICTransportError_v1, FrameType_v1) -> Unit {
        return { actualError, actualFrame ->
            if (actualError == expectedError && actualFrame in expectedFrames) {
                onErr()
            } else {
                fail("Unexpected error $actualError while reading $actualFrame frame")
            }
        }
    }
}
