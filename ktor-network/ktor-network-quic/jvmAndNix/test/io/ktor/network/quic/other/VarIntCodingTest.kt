/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.other

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.util.*
import io.ktor.utils.io.core.*
import kotlin.math.*
import kotlin.test.*

@ExperimentalUnsignedTypes
class VarIntCodingTest {
    @Test
    fun testEncodeVarInt() {
        val builder = BytePacketBuilder()
        with(builder) {
            writeVarInt(0)
            writeVarInt(1)
            writeVarInt(POW_2_06 - 1)
            writeVarInt(POW_2_06)
            writeVarInt(POW_2_14 - 10)
            writeVarInt(POW_2_14 - 1)
            writeVarInt(POW_2_14)
            writeVarInt(POW_2_30 - 42)
            writeVarInt(POW_2_30 - 1)
            writeVarInt(POW_2_30)
            writeVarInt(POW_2_62 - 7)
            writeVarInt(POW_2_62 - 1)

            assertFails { writeVarInt(POW_2_62) }
            assertFails { writeVarInt(Long.MAX_VALUE) }
            assertFails { writeVarInt(-1) }
        }

        val packet = builder.build()

        with(packet) {
            assertEquals(0, readRawVarInt(1))
            assertEquals(1, readRawVarInt(1))
            assertEquals(POW_2_06 - 1, readRawVarInt(1))
            assertEquals(POW_2_06 or (1 shl 14), readRawVarInt(2))
            assertEquals((POW_2_14 - 10) or (1 shl 14), readRawVarInt(2))
            assertEquals((POW_2_14 - 1) or (1 shl 14), readRawVarInt(2))
            assertEquals(POW_2_14 or (1L shl 31), readRawVarInt(4))
            assertEquals((POW_2_30 - 42) or (1L shl 31), readRawVarInt(4))
            assertEquals((POW_2_30 - 1) or (1L shl 31), readRawVarInt(4))
            assertEquals(POW_2_30 or (3L shl 62), readRawVarInt(8))
            assertEquals((POW_2_62 - 7) or (3L shl 62), readRawVarInt(8))
            assertEquals((POW_2_62 - 1) or (3L shl 62), readRawVarInt(8))
        }
    }

    @Test
    fun testDecodeVarint() {
        val builder = BytePacketBuilder()
        with(builder) {
            writeRawVarInt(0, 1)
            writeRawVarInt(1, 1)
            writeRawVarInt(POW_2_06 - 1, 1)
            writeRawVarInt(POW_2_06 or (1 shl 14), 2)
            writeRawVarInt((POW_2_14 - 10) or (1 shl 14), 2)
            writeRawVarInt((POW_2_14 - 1) or (1 shl 14), 2)
            writeRawVarInt(POW_2_14 or (1L shl 31), 4)
            writeRawVarInt((POW_2_30 - 42) or (1L shl 31), 4)
            writeRawVarInt((POW_2_30 - 1) or (1L shl 31), 4)
            writeRawVarInt(POW_2_30 or (3L shl 62), 8)
            writeRawVarInt((POW_2_62 - 7) or (3L shl 62), 8)
            writeRawVarInt((POW_2_62 - 1) or (3L shl 62), 8)

            writeUByte(0xffu)
        }

        val packet = builder.build()
        with(packet) {
            assertEquals(0, readVarIntOrElse())
            assertEquals(1, readVarIntOrElse())
            assertEquals(POW_2_06 - 1, readVarIntOrElse())
            assertEquals(POW_2_06, readVarIntOrElse())
            assertEquals(POW_2_14 - 10, readVarIntOrElse())
            assertEquals(POW_2_14 - 1, readVarIntOrElse())
            assertEquals(POW_2_14, readVarIntOrElse())
            assertEquals(POW_2_30 - 42, readVarIntOrElse())
            assertEquals(POW_2_30 - 1, readVarIntOrElse())
            assertEquals(POW_2_30, readVarIntOrElse())
            assertEquals(POW_2_62 - 7, readVarIntOrElse())
            assertEquals(POW_2_62 - 1, readVarIntOrElse())
            assertEquals(-1, readVarIntOrElse())
        }
    }

    private fun ByteReadPacket.readVarIntOrElse() = readVarIntOrElse { -1 }

    private fun ByteReadPacket.readRawVarInt(length: Int): Long {
        var long = 0L
        for (i in 0 until length) {
            val next = readUInt8 { error("Unexpected EOF") }.toLong()
            long = (long shl 8) + next
        }
        return long
    }

    private fun BytePacketBuilder.writeRawVarInt(value: Long, length: Int) {
        val prefix = log2(length.toFloat()).toLong() shl 6
        var first = true

        for (i in 0 until length) {
            val byte = ((value ushr ((length - i - 1) * 8)) and 0xffL).let {
                if (first) {
                    first = false
                    it or prefix
                } else {
                    it
                }
            }.toUByte()
            writeUByte(byte)
        }
    }
}
