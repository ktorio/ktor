/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("NOTHING_TO_INLINE", "unused")

package io.ktor.network.quic.bytes

import io.ktor.utils.io.core.*

internal typealias UInt8 = UByte
internal typealias UInt16 = UShort
internal typealias UInt24 = UInt
internal typealias UInt32 = UInt
internal typealias UInt64 = ULong

internal inline fun UInt8.toUInt64() = toULong()
internal inline fun UInt16.toUInt64() = toULong()
internal inline fun UInt32.toUInt64() = toULong()
internal inline fun UInt8.toUInt32() = toUInt()
internal inline fun UInt16.toUInt32() = toUInt()
internal inline fun UInt8.toUInt16() = toUShort()

@OptIn(ExperimentalUnsignedTypes::class)
internal inline fun ByteReadPacket.readUInt8(): UInt8 = readUByte()
@OptIn(ExperimentalUnsignedTypes::class)
internal inline fun ByteReadPacket.readUInt16(): UInt16 = readUShort()
@OptIn(ExperimentalUnsignedTypes::class)
internal inline fun ByteReadPacket.readUInt24(): UInt24 =  (readUShort().toUInt() shl 8) + readUByte()
@OptIn(ExperimentalUnsignedTypes::class)
internal inline fun ByteReadPacket.readUInt32(): UInt32 = readUInt()
@OptIn(ExperimentalUnsignedTypes::class)
internal inline fun ByteReadPacket.readUInt64(): UInt64 = readULong()


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
 * Reads an unsigned 16-bit int from a [ByteReadPacket]. Executes elseBlock if EOF
 */
internal inline fun ByteReadPacket.readUInt16(elseBlock: () -> UInt16): UInt16 {
    if (remaining < 2) {
        return elseBlock()
    }
    return readUInt16()
}

/**
 * Reads an unsigned 24-bit int from a [ByteReadPacket]. Executes elseBlock if EOF
 */
internal inline fun ByteReadPacket.readUInt24(elseBlock: () -> UInt24): UInt24 {
    if (remaining < 3) {
        return elseBlock()
    }
    return readUInt24()
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

/**
 * Reads an unsigned 32-bit int from a [ByteReadPacket]. Executes elseBlock if EOF
 */
internal inline fun ByteReadPacket.readUInt64(elseBlock: () -> UInt64): UInt64 {
    if (remaining < 8) {
        return elseBlock()
    }
    return readUInt64()
}
