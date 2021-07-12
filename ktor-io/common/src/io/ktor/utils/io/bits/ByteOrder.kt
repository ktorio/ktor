package io.ktor.utils.io.bits

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
public fun UShort.reverseByteOrder(): UShort = toShort().reverseByteOrder().toUShort()

/**
 * Reverse number's byte order
 */
public fun UInt.reverseByteOrder(): UInt = toInt().reverseByteOrder().toUInt()

/**
 * Reverse number's byte order
 */
public fun ULong.reverseByteOrder(): ULong = toLong().reverseByteOrder().toULong()

public inline val Short.highByte: Byte get() = (toInt() ushr 8).toByte()

public inline val Short.lowByte: Byte get() = (toInt() and 0xff).toByte()

public inline val Int.highShort: Short get() = (this ushr 16).toShort()

public inline val Int.lowShort: Short get() = (this and 0xffff).toShort()

public inline val Long.highInt: Int get() = (this ushr 32).toInt()

public inline val Long.lowInt: Int get() = (this and 0xffffffffL).toInt()
