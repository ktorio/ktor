@file:Suppress("NOTHING_TO_INLINE")

/*
* Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.bits

import io.ktor.utils.io.core.internal.*

/**
 * Copies short integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
public actual fun Memory.loadShortArray(
    offset: Int,
    destination: ShortArray,
    destinationOffset: Int,
    count: Int
) {
    repeat(count) { index ->
        destination[index + destinationOffset] = loadShortAt(index * 2 + offset)
    }
}

/**
 * Copies short integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
public actual fun Memory.loadShortArray(
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
public actual fun Memory.loadIntArray(
    offset: Int,
    destination: IntArray,
    destinationOffset: Int,
    count: Int
) {
    repeat(count) { index ->
        destination[index + destinationOffset] = loadIntAt(index * 4 + offset)
    }
}

/**
 * Copies regular integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
public actual fun Memory.loadIntArray(
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
public actual fun Memory.loadLongArray(
    offset: Int,
    destination: LongArray,
    destinationOffset: Int,
    count: Int
) {
    repeat(count) { index ->
        destination[index + destinationOffset] = loadLongAt(index * 8 + offset)
    }
}

/**
 * Copies regular integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
public actual fun Memory.loadLongArray(
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
public actual fun Memory.loadFloatArray(
    offset: Int,
    destination: FloatArray,
    destinationOffset: Int,
    count: Int
) {
    repeat(count) { index ->
        destination[index + destinationOffset] = loadFloatAt(index * 4 + offset)
    }
}

/**
 * Copies floating point numbers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
public actual fun Memory.loadFloatArray(
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
public actual fun Memory.loadDoubleArray(
    offset: Int,
    destination: DoubleArray,
    destinationOffset: Int,
    count: Int
) {
    repeat(count) { index ->
        destination[index + destinationOffset] = loadDoubleAt(index * 8 + offset)
    }
}

/**
 * Copies floating point numbers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
public actual fun Memory.loadDoubleArray(
    offset: Long,
    destination: DoubleArray,
    destinationOffset: Int,
    count: Int
) {
    loadDoubleArray(offset.toIntOrFail("offset"), destination, destinationOffset, count)
}

/**
 * Copies short integers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
public actual fun Memory.storeShortArray(
    offset: Int,
    source: ShortArray,
    sourceOffset: Int,
    count: Int
) {
    repeat(count) { index ->
        storeShortAt(index * 2 + offset, source[index + sourceOffset])
    }
}

/**
 * Copies short integers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
public actual fun Memory.storeShortArray(
    offset: Long,
    source: ShortArray,
    sourceOffset: Int,
    count: Int
) {
    storeShortArray(offset.toIntOrFail("offset"), source, sourceOffset, count)
}

/**
 * Copies regular integers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
public actual fun Memory.storeIntArray(
    offset: Int,
    source: IntArray,
    sourceOffset: Int,
    count: Int
) {
    repeat(count) { index ->
        storeIntAt(index * 4 + offset, source[index + sourceOffset])
    }
}

/**
 * Copies regular integers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
public actual fun Memory.storeIntArray(
    offset: Long,
    source: IntArray,
    sourceOffset: Int,
    count: Int
) {
    storeIntArray(offset.toIntOrFail("offset"), source, sourceOffset, count)
}

/**
 * Copies regular integers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
public actual fun Memory.storeLongArray(
    offset: Int,
    source: LongArray,
    sourceOffset: Int,
    count: Int
) {
    repeat(count) { index ->
        storeLongAt(index * 8 + offset, source[index + sourceOffset])
    }
}

/**
 * Copies regular integers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
public actual fun Memory.storeLongArray(
    offset: Long,
    source: LongArray,
    sourceOffset: Int,
    count: Int
) {
    storeLongArray(offset.toIntOrFail("offset"), source, sourceOffset, count)
}

/**
 * Copies floating point numbers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
public actual fun Memory.storeFloatArray(
    offset: Int,
    source: FloatArray,
    sourceOffset: Int,
    count: Int
) {
    repeat(count) { index ->
        storeFloatAt(index * 4 + offset, source[index + sourceOffset])
    }
}

/**
 * Copies floating point numbers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
public actual fun Memory.storeFloatArray(
    offset: Long,
    source: FloatArray,
    sourceOffset: Int,
    count: Int
) {
    storeFloatArray(offset.toIntOrFail("offset"), source, sourceOffset, count)
}

/**
 * Copies floating point numbers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
public actual fun Memory.storeDoubleArray(
    offset: Int,
    source: DoubleArray,
    sourceOffset: Int,
    count: Int
) {
    repeat(count) { index ->
        storeDoubleAt(index * 8 + offset, source[index + sourceOffset])
    }
}

/**
 * Copies floating point numbers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
public actual fun Memory.storeDoubleArray(
    offset: Long,
    source: DoubleArray,
    sourceOffset: Int,
    count: Int
) {
    storeDoubleArray(offset.toIntOrFail("offset"), source, sourceOffset, count)
}
