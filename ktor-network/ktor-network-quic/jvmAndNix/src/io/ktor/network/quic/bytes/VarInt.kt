/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("NOTHING_TO_INLINE")

package io.ktor.network.quic.bytes

import io.ktor.network.quic.util.*
import io.ktor.utils.io.core.*

/**
 * Reads variable-length non-negative integer value.
 * Executes [elseBlock] if EOF.
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-variable-length-integer-enc)
 *
 * @see [ByteReadPacket.readVarInt]
 */
internal inline fun ByteReadPacket.readVarIntOrElse(elseBlock: () -> Long): Long {
    val value = readVarInt()
    if (value == -1L) {
        return elseBlock()
    }
    return value
}

/**
 * Reads variable-length non-negative integer value.
 * Returns -1 if EOF.
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-variable-length-integer-enc)
 */
@OptIn(ExperimentalUnsignedTypes::class)
private fun ByteReadPacket.readVarInt(): Long {
    val value = tryPeek()
    if (value == -1) return -1

    val length = 1 shl (value ushr 6)

    if (length > remaining) {
        return -1
    }

    return when (length) {
        1 -> readUByte().toLong()
        2 -> readShort().toLong() and 0x3fff
        4 -> readInt().toLong() and 0x3fffffff
        8 -> readLong() and 0x3fffffffffffffff
        else -> -1 // unreachable
    }
}

/**
 * Writes variable-length non-negative integer value
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-variable-length-integer-enc)
 */
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
        value < POW_2_06 -> writeByte(value.toByte())
        value < POW_2_14 -> writeShort((value or (1L shl 14)).toShort())
        value < POW_2_30 -> writeInt((value or (1L shl 31)).toInt())
        value < POW_2_62 -> writeLong(value or (3L shl 62))
    }
}
