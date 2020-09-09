package io.ktor.utils.io.bits

import io.ktor.utils.io.core.ExperimentalIoApi

/**
 * Reverse number's byte order
 */
public expect fun Short.reverseByteOrder(): Short

/**
 * Reverse number's byte order
 */
public expect fun Int.reverseByteOrder(): Int

/**
 * Reverse number's byte order
 */
public expect fun Long.reverseByteOrder(): Long

/**
 * Reverse number's byte order
 */
public expect fun Float.reverseByteOrder(): Float

/**
 * Reverse number's byte order
 */
public expect fun Double.reverseByteOrder(): Double

/**
 * Reverse number's byte order
 */
@ExperimentalUnsignedTypes
public fun UShort.reverseByteOrder(): UShort = toShort().reverseByteOrder().toUShort()

/**
 * Reverse number's byte order
 */
@ExperimentalUnsignedTypes
public fun UInt.reverseByteOrder(): UInt = toInt().reverseByteOrder().toUInt()

/**
 * Reverse number's byte order
 */
@ExperimentalUnsignedTypes
public fun ULong.reverseByteOrder(): ULong = toLong().reverseByteOrder().toULong()

@ExperimentalIoApi
public inline val Short.highByte: Byte get() = (toInt() ushr 8).toByte()

@ExperimentalIoApi
public inline val Short.lowByte: Byte get() = (toInt() and 0xff).toByte()

@ExperimentalIoApi
public inline val Int.highShort: Short get() = (this ushr 16).toShort()

@ExperimentalIoApi
public inline val Int.lowShort: Short get() = (this and 0xffff).toShort()

@ExperimentalIoApi
public inline val Long.highInt: Int get() = (this ushr 32).toInt()

@ExperimentalIoApi
public inline val Long.lowInt: Int get() = (this and 0xffffffffL).toInt()
