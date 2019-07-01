@file:Suppress("NOTHING_TO_INLINE")

package io.ktor.utils.io.bits

import io.ktor.utils.io.core.internal.*

actual inline fun Memory.loadShortAt(offset: Int): Short {
    return buffer.getShort(offset)
}

actual inline fun Memory.loadShortAt(offset: Long): Short {
    return buffer.getShort(offset.toIntOrFail("offset"))
}

actual inline fun Memory.loadIntAt(offset: Int): Int {
    return buffer.getInt(offset)
}

actual inline fun Memory.loadIntAt(offset: Long): Int {
    return buffer.getInt(offset.toIntOrFail("offset"))
}

actual inline fun Memory.loadLongAt(offset: Int): Long {
    return buffer.getLong(offset)
}

actual inline fun Memory.loadLongAt(offset: Long): Long {
    return buffer.getLong(offset.toIntOrFail("offset"))
}

actual inline fun Memory.loadFloatAt(offset: Int): Float {
    return buffer.getFloat(offset)
}

actual inline fun Memory.loadFloatAt(offset: Long): Float {
    return buffer.getFloat(offset.toIntOrFail("offset"))
}

actual inline fun Memory.loadDoubleAt(offset: Int): Double {
    return buffer.getDouble(offset)
}

actual inline fun Memory.loadDoubleAt(offset: Long): Double {
    return buffer.getDouble(offset.toIntOrFail("offset"))
}

/**
 * Write regular signed 32bit integer in the network byte order (Big Endian)
 */
actual inline fun Memory.storeIntAt(offset: Int, value: Int) {
    buffer.putInt(offset, value)
}

/**
 * Write regular signed 32bit integer in the network byte order (Big Endian)
 */
actual inline fun Memory.storeIntAt(offset: Long, value: Int) {
    storeIntAt(offset.toIntOrFail("offset"), value)
}

/**
 * Write short signed 16bit integer in the network byte order (Big Endian)
 */
actual inline fun Memory.storeShortAt(offset: Int, value: Short) {
    buffer.putShort(offset, value)
}

/**
 * Write short signed 16bit integer in the network byte order (Big Endian)
 */
actual inline fun Memory.storeShortAt(offset: Long, value: Short) {
    storeShortAt(offset.toIntOrFail("offset"), value)
}

/**
 * Write short signed 64bit integer in the network byte order (Big Endian)
 */
actual inline fun Memory.storeLongAt(offset: Int, value: Long) {
    buffer.putLong(offset, value)
}

/**
 * Write short signed 64bit integer in the network byte order (Big Endian)
 */
actual inline fun Memory.storeLongAt(offset: Long, value: Long) {
    storeLongAt(offset.toIntOrFail("offset"), value)
}

/**
 * Write short signed 32bit floating point number in the network byte order (Big Endian)
 */
actual inline fun Memory.storeFloatAt(offset: Int, value: Float) {
    buffer.putFloat(offset, value)
}

/**
 * Write short signed 32bit floating point number in the network byte order (Big Endian)
 */
actual inline fun Memory.storeFloatAt(offset: Long, value: Float) {
    storeFloatAt(offset.toIntOrFail("offset"), value)
}

/**
 * Write short signed 64bit floating point number in the network byte order (Big Endian)
 */
actual inline fun Memory.storeDoubleAt(offset: Int, value: Double) {
    buffer.putDouble(offset, value)
}

/**
 * Write short signed 64bit floating point number in the network byte order (Big Endian)
 */
actual inline fun Memory.storeDoubleAt(offset: Long, value: Double) {
    storeDoubleAt(offset.toIntOrFail("offset"), value)
}
