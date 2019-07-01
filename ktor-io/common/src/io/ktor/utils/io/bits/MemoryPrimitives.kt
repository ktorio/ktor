@file:Suppress("NOTHING_TO_INLINE")

package io.ktor.utils.io.bits

/**
 * Read short signed 16bit integer in the network byte order (Big Endian)
 */
expect inline fun Memory.loadShortAt(offset: Int): Short

/**
 * Read short signed 16bit integer in the network byte order (Big Endian)
 */
expect inline fun Memory.loadShortAt(offset: Long): Short

/**
 * Write short signed 16bit integer in the network byte order (Big Endian)
 */
expect inline fun Memory.storeShortAt(offset: Int, value: Short)

/**
 * Write short signed 16bit integer in the network byte order (Big Endian)
 */
expect inline fun Memory.storeShortAt(offset: Long, value: Short)

/**
 * Read short unsigned 16bit integer in the network byte order (Big Endian)
 */
inline fun Memory.loadUShortAt(offset: Int): UShort = loadShortAt(offset).toUShort()

/**
 * Read short unsigned 16bit integer in the network byte order (Big Endian)
 */
inline fun Memory.loadUShortAt(offset: Long): UShort = loadShortAt(offset).toUShort()

/**
 * Write short unsigned 16bit integer in the network byte order (Big Endian)
 */
inline fun Memory.storeUShortAt(offset: Int, value: UShort): Unit = storeShortAt(offset, value.toShort())

/**
 * Write short unsigned 16bit integer in the network byte order (Big Endian)
 */
inline fun Memory.storeUShortAt(offset: Long, value: UShort): Unit = storeShortAt(offset, value.toShort())

/**
 * Read regular signed 32bit integer in the network byte order (Big Endian)
 */
expect inline fun Memory.loadIntAt(offset: Int): Int

/**
 * Read regular signed 32bit integer in the network byte order (Big Endian)
 */
expect inline fun Memory.loadIntAt(offset: Long): Int

/**
 * Write regular signed 32bit integer in the network byte order (Big Endian)
 */
expect inline fun Memory.storeIntAt(offset: Int, value: Int)

/**
 * Write regular signed 32bit integer in the network byte order (Big Endian)
 */
expect inline fun Memory.storeIntAt(offset: Long, value: Int)

/**
 * Read regular unsigned 32bit integer in the network byte order (Big Endian)
 */
inline fun Memory.loadUIntAt(offset: Int): UInt = loadIntAt(offset).toUInt()

/**
 * Read regular unsigned 32bit integer in the network byte order (Big Endian)
 */
inline fun Memory.loadUIntAt(offset: Long): UInt = loadIntAt(offset).toUInt()

/**
 * Write regular unsigned 32bit integer in the network byte order (Big Endian)
 */
inline fun Memory.storeUIntAt(offset: Int, value: UInt): Unit = storeIntAt(offset, value.toInt())

/**
 * Write regular unsigned 32bit integer in the network byte order (Big Endian)
 */
inline fun Memory.storeUIntAt(offset: Long, value: UInt): Unit = storeIntAt(offset, value.toInt())

/**
 * Read short signed 64bit integer in the network byte order (Big Endian)
 */
expect inline fun Memory.loadLongAt(offset: Int): Long

/**
 * Read short signed 64bit integer in the network byte order (Big Endian)
 */
expect inline fun Memory.loadLongAt(offset: Long): Long

/**
 * Write short signed 64bit integer in the network byte order (Big Endian)
 */
expect inline fun Memory.storeLongAt(offset: Int, value: Long)

/**
 * write short signed 64bit integer in the network byte order (Big Endian)
 */
expect inline fun Memory.storeLongAt(offset: Long, value: Long)

/**
 * Read short signed 64bit integer in the network byte order (Big Endian)
 */
inline fun Memory.loadULongAt(offset: Int): ULong = loadLongAt(offset).toULong()

/**
 * Read short signed 64bit integer in the network byte order (Big Endian)
 */
inline fun Memory.loadULongAt(offset: Long): ULong = loadLongAt(offset).toULong()

/**
 * Write short signed 64bit integer in the network byte order (Big Endian)
 */
inline fun Memory.storeULongAt(offset: Int, value: ULong): Unit = storeLongAt(offset, value.toLong())

/**
 * Write short signed 64bit integer in the network byte order (Big Endian)
 */
inline fun Memory.storeULongAt(offset: Long, value: ULong): Unit = storeLongAt(offset, value.toLong())

/**
 * Read short signed 32bit floating point number in the network byte order (Big Endian)
 */
expect inline fun Memory.loadFloatAt(offset: Int): Float

/**
 * Read short signed 32bit floating point number in the network byte order (Big Endian)
 */
expect inline fun Memory.loadFloatAt(offset: Long): Float

/**
 * Write short signed 32bit floating point number in the network byte order (Big Endian)
 */
expect inline fun Memory.storeFloatAt(offset: Int, value: Float)

/**
 * Write short signed 32bit floating point number in the network byte order (Big Endian)
 */
expect inline fun Memory.storeFloatAt(offset: Long, value: Float)

/**
 * Read short signed 64bit floating point number in the network byte order (Big Endian)
 */
expect inline fun Memory.loadDoubleAt(offset: Int): Double

/**
 * Read short signed 64bit floating point number in the network byte order (Big Endian)
 */
expect inline fun Memory.loadDoubleAt(offset: Long): Double

/**
 * Write short signed 64bit floating point number in the network byte order (Big Endian)
 */
expect inline fun Memory.storeDoubleAt(offset: Int, value: Double)

/**
 * Write short signed 64bit floating point number in the network byte order (Big Endian)
 */
expect inline fun Memory.storeDoubleAt(offset: Long, value: Double)
