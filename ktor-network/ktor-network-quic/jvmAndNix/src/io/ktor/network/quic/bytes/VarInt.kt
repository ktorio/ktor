/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("NOTHING_TO_INLINE")

package io.ktor.network.quic.bytes

import io.ktor.network.quic.util.*
import io.ktor.utils.io.core.*

internal inline fun ByteReadPacket.readVarIntOrElse(elseBlock: () -> Long): Long {
    val value = readVarInt()
    if (value == -1L) {
        elseBlock()
    }
    return value
}

/**
 * Reads variable-length non-negative integer value. Returns null if EOF.
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-variable-length-integer-enc)
 */
@OptIn(ExperimentalUnsignedTypes::class)
private fun ByteReadPacket.readVarInt(): Long {
    val value = readUByteOrElse { return -1 }.toInt()
    val length = 1 shl (value ushr 6)

    var varInt = (value and 0x3f).toLong()
    var i = 0
    while (i < length - 1) {
        varInt = (varInt shl 8) + readUByteOrElse { return -1 }.toLong()
        i++
    }
    return varInt
}

internal fun BytePacketBuilder.writeVarInt(varInt: Int) = writeVarInt(varInt.toLong())

/**
 * Writes variable-length non-negative integer value
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-variable-length-integer-enc)
 */
internal fun BytePacketBuilder.writeVarInt(value: Long) {
    require(value >= 0) { "Variable-length integer cannot be negative, actual: $value" }
    require(value < POW_2_62) { "Max value of variable-length integer value is ${POW_2_62 - 1}, actual: $value" }

    when {
        value < POW_2_06 -> {
            writeByteOf(value, 0)
        }

        value < POW_2_14 -> {
            writeByteOf(value or (1L shl 14), 1)
            writeByteOf(value, 0)
        }

        value < POW_2_30 -> {
            writeByteOf(value or (1L shl 31), 3)
            writeByteOf(value, 2)
            writeByteOf(value, 1)
            writeByteOf(value, 0)
        }

        value < POW_2_62 -> {
            writeByteOf(value or (3L shl 62), 7)
            writeByteOf(value, 6)
            writeByteOf(value, 5)
            writeByteOf(value, 4)
            writeByteOf(value, 3)
            writeByteOf(value, 2)
            writeByteOf(value, 1)
            writeByteOf(value, 0)
        }
    }
}

/**
 * Writes byte of the [Long] value to [BytePacketBuilder]
 * Bytes are numerated right to left starting from 0
 */
private inline fun BytePacketBuilder.writeByteOf(value: Long, byteNo: Int) {
    writeByte(((value ushr (byteNo * 8)) and 0xff).toByte())
}
