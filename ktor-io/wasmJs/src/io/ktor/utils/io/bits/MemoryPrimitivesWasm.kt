@file:Suppress("NOTHING_TO_INLINE")

/*
* Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.bits

import io.ktor.utils.io.core.internal.*

public actual inline fun Memory.loadShortAt(offset: Int): Short =
    ((data[offset + 0].toUByte().toInt() shl 8) or (data[offset + 1].toUByte().toInt() and 0xff)).toShort()

public actual inline fun Memory.loadShortAt(offset: Long): Short =
    loadShortAt(offset.toIntOrFail("offset")).toShort()

public actual inline fun Memory.loadIntAt(offset: Int): Int =
    (
        (data[offset + 0].toUByte().toInt() shl 24) or
            (data[offset + 1].toUByte().toInt() shl 16) or
            (data[offset + 2].toUByte().toInt() shl 8) or
            (data[offset + 3].toUByte().toInt() and 0xff)
        )

public actual inline fun Memory.loadIntAt(offset: Long): Int =
    loadIntAt(offset.toIntOrFail("offset"))

public actual inline fun Memory.loadLongAt(offset: Int): Long =
    (
        (data[offset + 0].toUByte().toLong() shl 56) or
            (data[offset + 1].toUByte().toLong() shl 48) or
            (data[offset + 2].toUByte().toLong() shl 40) or
            (data[offset + 3].toUByte().toLong() shl 32) or
            (data[offset + 4].toUByte().toLong() shl 24) or
            (data[offset + 5].toUByte().toLong() shl 16) or
            (data[offset + 6].toUByte().toLong() shl 8) or
            (data[offset + 7].toUByte().toLong() and 0xffL)
        )

public actual inline fun Memory.loadLongAt(offset: Long): Long =
    loadLongAt(offset.toIntOrFail("offset"))

public actual inline fun Memory.loadFloatAt(offset: Int): Float =
    Float.fromBits(loadIntAt(offset))

public actual inline fun Memory.loadFloatAt(offset: Long): Float =
    loadFloatAt(loadIntAt(offset.toIntOrFail("offset")))

public actual inline fun Memory.loadDoubleAt(offset: Int): Double =
    kotlin.Double.fromBits(loadLongAt(offset))

public actual inline fun Memory.loadDoubleAt(offset: Long): Double =
    loadDoubleAt(offset.toIntOrFail("offset"))

/**
 * Write regular signed 32bit integer in the network byte order (Big Endian)
 */
public actual inline fun Memory.storeIntAt(offset: Int, value: Int) {
    data[offset + 0] = (value ushr 24).toByte()
    data[offset + 1] = (value ushr 16).toByte()
    data[offset + 2] = (value ushr 8).toByte()
    data[offset + 3] = (value and 0xff).toByte()
}

/**
 * Write regular signed 32bit integer in the network byte order (Big Endian)
 */
public actual inline fun Memory.storeIntAt(offset: Long, value: Int) {
    storeIntAt(offset.toIntOrFail("offset"), value)
}

/**
 * Write short signed 16bit integer in the network byte order (Big Endian)
 */
public actual inline fun Memory.storeShortAt(offset: Int, value: Short) {
    data[offset + 0] = (value.toInt() ushr 8).toByte()
    data[offset + 1] = (value.toInt() and 0xff).toByte()
}

/**
 * Write short signed 16bit integer in the network byte order (Big Endian)
 */
public actual inline fun Memory.storeShortAt(offset: Long, value: Short) {
    storeShortAt(offset.toIntOrFail("offset"), value)
}

/**
 * Write short signed 64bit integer in the network byte order (Big Endian)
 */
public actual inline fun Memory.storeLongAt(offset: Int, value: Long) {
    data[offset + 0] = (value ushr 56).toByte()
    data[offset + 1] = (value ushr 48).toByte()
    data[offset + 2] = (value ushr 40).toByte()
    data[offset + 3] = (value ushr 32).toByte()
    data[offset + 4] = (value ushr 24).toByte()
    data[offset + 5] = (value ushr 16).toByte()
    data[offset + 6] = (value ushr 8).toByte()
    data[offset + 7] = (value and 0xff).toByte()
}

/**
 * Write short signed 64bit integer in the network byte order (Big Endian)
 */
public actual inline fun Memory.storeLongAt(offset: Long, value: Long) {
    storeLongAt(offset.toIntOrFail("offset"), value)
}

/**
 * Write short signed 32bit floating point number in the network byte order (Big Endian)
 */
public actual inline fun Memory.storeFloatAt(offset: Int, value: Float) {
    storeIntAt(offset, value.toRawBits())
}

/**
 * Write short signed 32bit floating point number in the network byte order (Big Endian)
 */
public actual inline fun Memory.storeFloatAt(offset: Long, value: Float) {
    storeFloatAt(offset.toIntOrFail("offset"), value)
}

/**
 * Write short signed 64bit floating point number in the network byte order (Big Endian)
 */
public actual inline fun Memory.storeDoubleAt(offset: Int, value: Double) {
    storeLongAt(offset, value.toRawBits())
}

/**
 * Write short signed 64bit floating point number in the network byte order (Big Endian)
 */
public actual inline fun Memory.storeDoubleAt(offset: Long, value: Double) {
    storeDoubleAt(offset.toIntOrFail("offset"), value)
}
