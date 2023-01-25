/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.frames

import io.ktor.network.quic.errors.*
import io.ktor.network.quic.frames.base.*
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
        val params = TestPacketTransportParameters(ack_delay_exponent = 1)
        val ranges = LongArray(8) { 18L - it * 2 }
        frameTest(
            writeFrames = { packetBuilder ->
                writeACK(packetBuilder, 42, params.ack_delay_exponent, ranges)

                assertFails("Odd ACK ranges") {
                    writeACK(packetBuilder, 1, 1, LongArray(3))
                }

//                assertFails("Ascending ACK ranges") {
//                    writeACK(packetBuilder, 1, 1, LongArray(4) { 1L + it })
//                }
            },
            validator = {
                validateACK = { ackDelay, ackRanges ->
                    assertEquals(42, ackDelay, "ACK delay")
                    assertEquals(ranges, ackRanges, "ACK Ranges")
                }
            }
        )
    }

    private fun frameTest(
        writeFrames: FrameWriter.(BytePacketBuilder) -> Unit,
        writeRaw: BytePacketBuilder.() -> Unit = {},
        parameters: TestPacketTransportParameters = TestPacketTransportParameters(),
        validator: ReadFramesValidator.() -> Unit = {},
        onReaderError: (QUICTransportError_v1, FrameType_v1) -> Unit = { _, _ -> },
    ) = runBlocking {
        val builder = BytePacketBuilder()
        val writer = TestFrameWriter()

        writer.writeFrames(builder)

        val packet = builder.build()
        val frameValidator = ReadFramesValidator().apply(validator)
        val processor = TestFrameProcessor(frameValidator, writer.writtenFrames)

        for (i in 0 until writer.writtenFrames.size) {
            FrameReader.readFrame(processor, packet, parameters, onReaderError)
        }

        assertTrue(packet.isEmpty, "Packet is not empty, remaining: ${packet.remaining}")

        processor.assertNoExpectedFramesLeft()
    }
}
