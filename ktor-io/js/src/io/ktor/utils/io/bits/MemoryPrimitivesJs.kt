@file:Suppress("NOTHING_TO_INLINE")

package io.ktor.utils.io.bits

import io.ktor.utils.io.core.internal.*


actual inline fun Memory.loadShortAt(offset: Int): Short = view.getInt16(offset, false)

actual inline fun Memory.loadShortAt(offset: Long): Short = loadShortAt(offset.toIntOrFail("offset"))

actual inline fun Memory.loadIntAt(offset: Int): Int = view.getInt32(offset, false)

actual inline fun Memory.loadIntAt(offset: Long): Int = loadIntAt(offset.toIntOrFail("offset"))

actual inline fun Memory.loadLongAt(offset: Int): Long =
    (view.getUint32(offset, false).toLong() shl 32) or
        view.getUint32(offset + 4, false).toLong()

actual inline fun Memory.loadLongAt(offset: Long): Long = loadLongAt(offset.toIntOrFail("offset"))

actual inline fun Memory.loadFloatAt(offset: Int): Float = view.getFloat32(offset, false)

actual inline fun Memory.loadFloatAt(offset: Long): Float = loadFloatAt(offset.toIntOrFail("offset"))

actual inline fun Memory.loadDoubleAt(offset: Int): Double = view.getFloat64(offset, false)

actual inline fun Memory.loadDoubleAt(offset: Long): Double = loadDoubleAt(offset.toIntOrFail("offset"))

/**
 * Write regular signed 32bit integer in the network byte order (Big Endian)
 */
actual inline fun Memory.storeIntAt(offset: Int, value: Int) {
    view.setInt32(offset, value, littleEndian = false)
}

/**
 * Write regular signed 32bit integer in the network byte order (Big Endian)
 */
actual inline fun Memory.storeIntAt(offset: Long, value: Int) {
    view.setInt32(offset.toIntOrFail("offset"), value, littleEndian = false)
}

/**
 * Write short signed 16bit integer in the network byte order (Big Endian)
 */
actual inline fun Memory.storeShortAt(offset: Int, value: Short) {
    view.setInt16(offset, value, littleEndian = false)
}

/**
 * Write short signed 16bit integer in the network byte order (Big Endian)
 */
actual inline fun Memory.storeShortAt(offset: Long, value: Short) {
    view.setInt16(offset.toIntOrFail("offset"), value, littleEndian = false)
}

/**
 * Write short signed 64bit integer in the network byte order (Big Endian)
 */
actual inline fun Memory.storeLongAt(offset: Int, value: Long) {
    view.setInt32(offset, (value shr 32).toInt(), littleEndian = false)
    view.setInt32(offset + 4, (value and 0xffffffffL).toInt(), littleEndian = false)
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
    view.setFloat32(offset, value, littleEndian = false)
}

/**
 * Write short signed 32bit floating point number in the network byte order (Big Endian)
 */
actual inline fun Memory.storeFloatAt(offset: Long, value: Float) {
    view.setFloat32(offset.toIntOrFail("offset"), value, littleEndian = false)
}

/**
 * Write short signed 64bit floating point number in the network byte order (Big Endian)
 */
actual inline fun Memory.storeDoubleAt(offset: Int, value: Double) {
    view.setFloat64(offset, value, littleEndian = false)
}

/**
 * Write short signed 64bit floating point number in the network byte order (Big Endian)
 */
actual inline fun Memory.storeDoubleAt(offset: Long, value: Double) {
    view.setFloat64(offset.toIntOrFail("offset"), value, littleEndian = false)
}
