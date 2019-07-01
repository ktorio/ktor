@file:Suppress("NOTHING_TO_INLINE")

package io.ktor.utils.io.bits

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import org.khronos.webgl.*

private val isLittleEndianPlatform = ByteOrder.nativeOrder() === ByteOrder.LITTLE_ENDIAN

/**
 * Copies shorts integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
actual fun Memory.loadShortArray(
    offset: Int,
    destination: ShortArray,
    destinationOffset: Int,
    count: Int
) {
    val typed = Int16Array(view.buffer, view.byteOffset + offset, count)

    if (isLittleEndianPlatform) {
        // TODO investigate this implementation vs DataView.getInt16(...)
        repeat(count) { index ->
            destination[index + destinationOffset] = typed[index].reverseByteOrder()
        }
    } else {
        repeat(count) { index ->
            destination[index + destinationOffset] = typed[index]
        }
    }
}

/**
 * Copies shorts integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
actual fun Memory.loadShortArray(
    offset: Long,
    destination: ShortArray,
    destinationOffset: Int,
    count: Int
) {
    loadShortArray(offset.toIntOrFail("offset"), destination, destinationOffset, count)
}

/**
 * Copies regular integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
actual fun Memory.loadIntArray(
    offset: Int,
    destination: IntArray,
    destinationOffset: Int,
    count: Int
) {
    val typed = Int32Array(view.buffer, view.byteOffset + offset, count)

    if (isLittleEndianPlatform) {
        repeat(count) { index ->
            destination[index + destinationOffset] = typed[index].reverseByteOrder()
        }
    } else {
        repeat(count) { index ->
            destination[index + destinationOffset] = typed[index]
        }
    }
}

/**
 * Copies regular integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
actual fun Memory.loadIntArray(
    offset: Long,
    destination: IntArray,
    destinationOffset: Int,
    count: Int
) {
    loadIntArray(offset.toIntOrFail("offset"), destination, destinationOffset, count)
}

/**
 * Copies regular integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
actual fun Memory.loadLongArray(
    offset: Int,
    destination: LongArray,
    destinationOffset: Int,
    count: Int
) {
    val typed = Int32Array(view.buffer, view.byteOffset + offset, count * 2)

    if (isLittleEndianPlatform) {
        for (index in 0 until count * 2 step 2) {
            destination[index / 2 + destinationOffset] =
                (typed[index + 1].reverseByteOrder().toLong() and 0xffffffffL) or
                    (typed[index].reverseByteOrder().toLong() shl 32)
        }
    } else {
        for (index in 0 until count * 2 step 2) {
            destination[index / 2 + destinationOffset] = (typed[index].toLong() and 0xffffffffL) or
                (typed[index + 1].toLong() shl 32)
        }
    }
}

/**
 * Copies regular integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
actual fun Memory.loadLongArray(
    offset: Long,
    destination: LongArray,
    destinationOffset: Int,
    count: Int
) {
    loadLongArray(offset.toIntOrFail("offset"), destination, destinationOffset, count)
}

/**
 * Copies floating point numbers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
actual fun Memory.loadFloatArray(
    offset: Int,
    destination: FloatArray,
    destinationOffset: Int,
    count: Int
) {
    val typed = Float32Array(view.buffer, view.byteOffset + offset, count)

    if (isLittleEndianPlatform) {
        repeat(count) { index ->
            destination[index + destinationOffset] = typed[index].reverseByteOrder()
        }
    } else {
        repeat(count) { index ->
            destination[index + destinationOffset] = typed[index]
        }
    }
}

/**
 * Copies floating point numbers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
actual fun Memory.loadFloatArray(
    offset: Long,
    destination: FloatArray,
    destinationOffset: Int,
    count: Int
) {
    loadFloatArray(offset.toIntOrFail("offset"), destination, destinationOffset, count)
}

/**
 * Copies floating point numbers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
actual fun Memory.loadDoubleArray(
    offset: Int,
    destination: DoubleArray,
    destinationOffset: Int,
    count: Int
) {
    val typed = Float64Array(view.buffer, view.byteOffset + offset, count)

    if (isLittleEndianPlatform) {
        repeat(count) { index ->
            destination[index + destinationOffset] = typed[index].reverseByteOrder()
        }
    } else {
        repeat(count) { index ->
            destination[index + destinationOffset] = typed[index]
        }
    }
}

/**
 * Copies floating point numbers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
actual fun Memory.loadDoubleArray(
    offset: Long,
    destination: DoubleArray,
    destinationOffset: Int,
    count: Int
) {
    loadDoubleArray(offset.toIntOrFail("offset"), destination, destinationOffset, count)
}

/**
 * Copies shorts integers from from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
actual fun Memory.storeShortArray(
    offset: Int,
    source: ShortArray,
    sourceOffset: Int,
    count: Int
) {
    val typed = Int16Array(view.buffer, view.byteOffset + offset, count)

    if (isLittleEndianPlatform) {
        // TODO investigate this implementation vs DataView.getInt16(...)
        repeat(count) { index ->
            typed[index] = source[index + sourceOffset].reverseByteOrder()
        }
    } else {
        repeat(count) { index ->
            typed[index] = source[index + sourceOffset]
        }
    }
}

/**
 * Copies shorts integers from from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
actual fun Memory.storeShortArray(
    offset: Long,
    source: ShortArray,
    sourceOffset: Int,
    count: Int
) {
    storeShortArray(offset.toIntOrFail("offset"), source, sourceOffset, count)
}

/**
 * Copies regular integers from from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
actual fun Memory.storeIntArray(
    offset: Int,
    source: IntArray,
    sourceOffset: Int,
    count: Int
) {
    val typed = Int32Array(view.buffer, view.byteOffset + offset, count)

    if (isLittleEndianPlatform) {
        repeat(count) { index ->
            typed[index] = source[index + sourceOffset].reverseByteOrder()
        }
    } else {
        repeat(count) { index ->
            typed[index] = source[index + sourceOffset]
        }
    }
}

/**
 * Copies regular integers from from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
actual fun Memory.storeIntArray(
    offset: Long,
    source: IntArray,
    sourceOffset: Int,
    count: Int
) {
    storeIntArray(offset.toIntOrFail("offset"), source, sourceOffset, count)
}

/**
 * Copies regular integers from from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
actual fun Memory.storeLongArray(
    offset: Int,
    source: LongArray,
    sourceOffset: Int,
    count: Int
) {
    val typed = Int32Array(view.buffer, view.byteOffset + offset, count * 2)

    if (isLittleEndianPlatform) {
        for (index in 0 until count * 2 step 2) {
            val sourceIndex = index / 2 + sourceOffset
            val sourceValue = source[sourceIndex]
            typed[index] = (sourceValue ushr 32).toInt().reverseByteOrder()
            typed[index + 1] = (sourceValue and 0xffffffffL).toInt().reverseByteOrder()
        }
    } else {
        for (index in 0 until count * 2 step 2) {
            val sourceIndex = index / 2 + sourceOffset
            val sourceValue = source[sourceIndex]
            typed[index] = (sourceValue ushr 32).toInt()
            typed[index + 1] = (sourceValue and 0xffffffffL).toInt()
        }
    }
}

/**
 * Copies regular integers from from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
actual fun Memory.storeLongArray(
    offset: Long,
    source: LongArray,
    sourceOffset: Int,
    count: Int
) {
    storeLongArray(offset.toIntOrFail("offset"), source, sourceOffset, count)
}

/**
 * Copies floating point numbers from from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
actual fun Memory.storeFloatArray(
    offset: Int,
    source: FloatArray,
    sourceOffset: Int,
    count: Int
) {
    val typed = Float32Array(view.buffer, view.byteOffset + offset, count)

    if (isLittleEndianPlatform) {
        repeat(count) { index ->
            typed[index] = source[index + sourceOffset].reverseByteOrder()
        }
    } else {
        repeat(count) { index ->
            typed[index] = source[index + sourceOffset]
        }
    }
}

/**
 * Copies floating point numbers from from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
actual fun Memory.storeFloatArray(
    offset: Long,
    source: FloatArray,
    sourceOffset: Int,
    count: Int
) {
    storeFloatArray(offset.toIntOrFail("offset"), source, sourceOffset, count)
}

/**
 * Copies floating point numbers from from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
actual fun Memory.storeDoubleArray(
    offset: Int,
    source: DoubleArray,
    sourceOffset: Int,
    count: Int
) {
    val typed = Float64Array(view.buffer, view.byteOffset + offset, count)

    if (isLittleEndianPlatform) {
        repeat(count) { index ->
            typed[index] = source[index + sourceOffset].reverseByteOrder()
        }
    } else {
        repeat(count) { index ->
            typed[index] = source[index + sourceOffset]
        }
    }
}

/**
 * Copies floating point numbers from from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
actual fun Memory.storeDoubleArray(
    offset: Long,
    source: DoubleArray,
    sourceOffset: Int,
    count: Int
) {
    storeDoubleArray(offset.toIntOrFail("offset"), source, sourceOffset, count)
}
