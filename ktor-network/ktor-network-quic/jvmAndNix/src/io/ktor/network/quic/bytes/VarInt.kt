/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.bytes

import io.ktor.network.quic.util.*
import io.ktor.utils.io.core.*

/**
 * Reads variable-length non-negative integer value. Returns null if EOF.
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-variable-length-integer-enc)
 */
@OptIn(ExperimentalUnsignedTypes::class)
internal fun ByteReadPacket.readVarIntOrNull(): Long? {
    val value = readUByteOrNull()?.toInt() ?: return null
    val length = 1 shl (value ushr 6)

    var varInt = (value and 0x3f).toLong()
    var i = 0
    while (i < length - 1) {
        varInt = (varInt shl 8) + (readUByteOrNull()?.toLong() ?: return null)
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

    var shift: Int
    val length: Long
    when {
        value < POW_2_06 -> {
            shift = 0
            length = 0
        }
        value < POW_2_14 -> {
            shift = 8
            length = 1
        }
        value < POW_2_30 -> {
            shift = 24
            length = 2
        }
        value < POW_2_62 -> {
            shift = 56
            length = 3
        }
        else -> error("unreachable")
    }

    var first = true

    while (shift >= 0) {
        val byte = ((value ushr shift) and 0xffL).let {
            if (first) {
                first = false

                // write length bits
                it or (length shl 6)
            } else it
        }

        writeByte(byte.toByte())
        shift -= 8
    }
}
