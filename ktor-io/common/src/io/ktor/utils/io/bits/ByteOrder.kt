package io.ktor.utils.io.bits

import io.ktor.utils.io.core.ExperimentalIoApi

/**
 * Reverse number's byte order
 */
expect fun Short.reverseByteOrder(): Short

/**
 * Reverse number's byte order
 */
expect fun Int.reverseByteOrder(): Int

/**
 * Reverse number's byte order
 */
expect fun Long.reverseByteOrder(): Long

/**
 * Reverse number's byte order
 */
expect fun Float.reverseByteOrder(): Float

/**
 * Reverse number's byte order
 */
expect fun Double.reverseByteOrder(): Double

/**
 * Reverse number's byte order
 */
@ExperimentalUnsignedTypes
fun UShort.reverseByteOrder(): UShort = toShort().reverseByteOrder().toUShort()

/**
 * Reverse number's byte order
 */
@ExperimentalUnsignedTypes
fun UInt.reverseByteOrder(): UInt = toInt().reverseByteOrder().toUInt()

/**
 * Reverse number's byte order
 */
@ExperimentalUnsignedTypes
fun ULong.reverseByteOrder(): ULong = toLong().reverseByteOrder().toULong()

@ExperimentalIoApi
inline val Short.highByte: Byte get() = ((toInt() and 0xff) shr 8).toByte()

@ExperimentalIoApi
inline val Short.lowByte: Byte get() = (toInt() and 0xff).toByte()

@ExperimentalIoApi
inline val Int.highShort: Short get() = (this ushr 16).toShort()

@ExperimentalIoApi
inline val Int.lowShort: Short get() = (this and 0xffff).toShort()

@ExperimentalIoApi
inline val Long.highInt: Int get() = (this ushr 32).toInt()

@ExperimentalIoApi
inline val Long.lowInt: Int get() = (this and 0xffffffffL).toInt()
