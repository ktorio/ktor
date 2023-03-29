/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("NOTHING_TO_INLINE", "unused")
@file:OptIn(ExperimentalUnsignedTypes::class)

package io.ktor.network.quic.bytes

import io.ktor.network.quic.util.*
import io.ktor.utils.io.core.*

internal typealias UInt8 = UByte
internal typealias UInt16 = UShort
internal typealias UInt24 = UInt
internal typealias UInt32 = UInt
internal typealias UInt64 = ULong

internal inline fun UInt8.toUInt64(): UInt64 = toULong()
internal inline fun UInt16.toUInt64(): UInt64 = toULong()
internal inline fun UInt32.toUInt64(): UInt64 = toULong()
internal inline fun UInt8.toUInt32(): UInt32 = toUInt()
internal inline fun UInt16.toUInt32(): UInt32 = toUInt()
internal inline fun UInt8.toUInt16(): UInt16 = toUShort()

internal inline fun UInt64.toUInt8(): UInt8 = toUByte()
internal inline fun UInt32.toUInt8(): UInt8 = toUByte()
internal inline fun UInt16.toUInt8(): UInt8 = toUByte()
internal inline fun UInt64.toUInt16(): UInt16 = toUShort()
internal inline fun UInt32.toUInt16(): UInt16 = toUShort()
internal inline fun UInt64.toUInt32(): UInt32 = toUInt()

internal inline fun ByteReadPacket.readUInt8(): UInt8 = readUByte()
internal inline fun ByteReadPacket.readUInt16(): UInt16 = readUShort()
internal inline fun ByteReadPacket.readUInt24(): UInt24 = (readUShort().toUInt32() shl 8) + readUByte()
internal inline fun ByteReadPacket.readUInt32(): UInt32 = readUInt()

internal inline fun BytePacketBuilder.writeUInt8(value: UInt8) = writeUByte(value)
internal inline fun BytePacketBuilder.writeUInt16(value: UInt16) = writeUShort(value)
internal inline fun BytePacketBuilder.writeUInt24(value: UInt24) {
    writeUByte((value.toInt() ushr 16).toUByte())
    writeUShort(value.toUInt16())
}

internal inline fun BytePacketBuilder.writeUInt32(value: UInt32) = writeUInt(value)

/**
 * Reads an unsigned 8-bit int from a [ByteReadPacket]. Executes elseBlock if EOF
 */
internal inline fun ByteReadPacket.readUInt8(elseBlock: () -> UInt8): UInt8 {
    if (isEmpty) {
        return elseBlock()
    }
    return readUInt8()
}

/**
 * Reads an unsigned 32-bit int from a [ByteReadPacket]. Executes elseBlock if EOF
 */
internal inline fun ByteReadPacket.readUInt32(elseBlock: () -> UInt32): UInt32 {
    if (remaining < 4) {
        return elseBlock()
    }
    return readUInt32()
}

internal fun ByteArray.toDebugString() = joinToString(" ") { it.toString16Byte() }

internal fun Byte.toString16Byte(): String {
    return toUByte().toString(16).padStart(2, '0')
}

internal fun ByteReadPacket.toDebugString(showAll: Boolean = false): String {
    return if (showAll) copy().readBytes().toDebugString() else "$remaining bytes"
}

internal val EMPTY_BYTE_ARRAY = ByteArray(0)

/**
 * Calculates size of the byte array in the frame with `length` property
 */
internal fun byteArrayFrameSize(size: Int): Int {
    val varIntSize = when {
        size < POW_2_06 -> 1
        size < POW_2_14 -> 2
        size < POW_2_30 -> 4
        else -> 8
    }
    return varIntSize + size
}
