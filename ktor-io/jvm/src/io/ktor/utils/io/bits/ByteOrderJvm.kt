@file:JvmName("ByteOrderJVMKt")
package io.ktor.utils.io.bits

/**
 * Reverse number's byte order
 */
@Suppress("NOTHING_TO_INLINE")
actual inline fun Short.reverseByteOrder(): Short = java.lang.Short.reverseBytes(this)


/**
 * Reverse number's byte order
 */
@Suppress("NOTHING_TO_INLINE")
actual inline fun Int.reverseByteOrder(): Int = java.lang.Integer.reverseBytes(this)


/**
 * Reverse number's byte order
 */
@Suppress("NOTHING_TO_INLINE")
actual inline fun Long.reverseByteOrder(): Long = java.lang.Long.reverseBytes(this)


/**
 * Reverse number's byte order
 */
@Suppress("NOTHING_TO_INLINE")
actual inline fun Float.reverseByteOrder(): Float =
    java.lang.Float.intBitsToFloat(
        java.lang.Integer.reverseBytes(
            java.lang.Float.floatToRawIntBits(this)
        )
    )

/**
 * Reverse number's byte order
 */
@Suppress("NOTHING_TO_INLINE")
actual inline fun Double.reverseByteOrder(): Double =
    java.lang.Double.longBitsToDouble(
        java.lang.Long.reverseBytes(
            java.lang.Double.doubleToRawLongBits(this)
        )
    )

