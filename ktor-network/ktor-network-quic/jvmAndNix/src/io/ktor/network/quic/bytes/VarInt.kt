/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.bytes

import io.ktor.utils.io.core.*
import kotlin.jvm.*

@JvmInline
internal value class VarInt(val value: Long)

internal inline fun Long.toVarInt() = VarInt(this)

internal inline fun Int.toVarInt() = VarInt(this.toLong())


/**
 * Reads variable-length non-negative integer value. Returns null if EOF.
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-variable-length-integer-enc)
 */
internal fun ByteReadPacket.readVarIntOrNull(): VarInt? {
    val value = readByteOrNull()?.toInt() ?: return null
    val length = 1 shl (value ushr 6)

    var varInt = (value and 0x3f).toLong()
    var i = 0
    while (i < length) {
        varInt = (varInt shl 8) + (readByteOrNull()?.toLong() ?: return null)
        i++
    }
    return varInt.toVarInt()
}

/**
 * Writes variable-length non-negative integer value
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-variable-length-integer-enc)
 */
internal fun BytePacketBuilder.writeVarInt(varInt: VarInt) {
    val value = varInt.value
    require(value >= 0) { "Variable-length integer cannot be negative" }
    require(value <= 4611686018427387903L) { "Max value of variable-length integer value is 4611686018427387903, actual: $value" }

    var shift = when {
        value < 64L -> 0
        value < 16384L -> 8
        value < 1073741824L -> 16
        value < 4611686018427387904L -> 24
        else -> error("unreachable")
    }

    val byteMask = 0xffL shl shift
    var mask = 0x3fL shl shift
    var first = true

    while (mask > 0) {
        val byte = ((value and mask) ushr shift).let {
            // write length
            if (first) it and ((shift / 8L) shl 6) else it
        }
        writeByte(byte.toByte())
        shift -= 8
        if (first) {
            mask = byteMask
            first = false
        }
        mask = mask ushr 8
    }
}
