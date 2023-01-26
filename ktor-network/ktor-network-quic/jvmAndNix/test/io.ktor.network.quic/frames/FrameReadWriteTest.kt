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

                assertFails("Odd ACK ranges") {
                    writeACK(packetBuilder, 1, 1, LongArray(3))
                }

                assertFails("Ascending ACK ranges") {
                    writeACK(packetBuilder, 1, 1, LongArray(4) { 1L + it })
                }
            },
            validator = {
                validateACK { ackDelay, ackRanges ->
                    assertEquals(84, ackDelay, "ACK delay")
                    assertContentEquals(ranges1, ackRanges, "ACK Ranges")
                }

                validateACK { ackDelay, ackRanges ->
                    assertEquals(POW_2_60, ackDelay, "ACK delay")
                    assertContentEquals(ranges2, ackRanges, "ACK Ranges")
                }
            },
            onReaderError = { error, frameType ->
                if (error == TransportError_v1.FRAME_ENCODING_ERROR && frameType == FrameType_v1.ACK) {
                    errCnt++
                } else {
                    fail("Unexpected error $error while reading $frameType frame")
                }
            }
        )

        assertEquals(errCnt, 2, "Expected 2 errors to occur")
    }

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
}
