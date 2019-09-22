@file:Suppress("NOTHING_TO_INLINE")

package io.ktor.utils.io.bits

/**
 * Copies bytes from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset].
 */
inline fun Memory.loadByteArray(
    offset: Int,
    destination: ByteArray,
    destinationOffset: Int = 0,
    count: Int = destination.size - destinationOffset
) {
    copyTo(destination, offset, count, destinationOffset)
}

/**
 * Copies unsigned shorts integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
inline fun Memory.loadByteArray(
    offset: Long,
    destination: ByteArray,
    destinationOffset: Int = 0,
    count: Int = destination.size - destinationOffset
) {
    copyTo(destination, offset, count, destinationOffset)
}

/**
 * Copies unsigned shorts integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
inline fun Memory.loadUByteArray(
    offset: Int,
    destination: UByteArray,
    destinationOffset: Int = 0,
    count: Int = destination.size - destinationOffset
) {
    copyTo(destination.asByteArray(), offset, count, destinationOffset)
}

/**
 * Copies unsigned shorts integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
inline fun Memory.loadUByteArray(
    offset: Long,
    destination: UByteArray,
    destinationOffset: Int = 0,
    count: Int = destination.size - destinationOffset
) {
    copyTo(destination.asByteArray(), offset, count, destinationOffset)
}

/**
 * Copies shorts integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
expect fun Memory.loadShortArray(
    offset: Int,
    destination: ShortArray,
    destinationOffset: Int = 0,
    count: Int = destination.size - destinationOffset
)

/**
 * Copies shorts integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
expect fun Memory.loadShortArray(
    offset: Long,
    destination: ShortArray,
    destinationOffset: Int = 0,
    count: Int = destination.size - destinationOffset
)

/**
 * Copies unsigned shorts integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
inline fun Memory.loadUShortArray(
    offset: Int,
    destination: UShortArray,
    destinationOffset: Int = 0,
    count: Int = destination.size - destinationOffset
) {
    loadShortArray(offset, destination.asShortArray(), destinationOffset, count)
}

/**
 * Copies unsigned shorts integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
inline fun Memory.loadUShortArray(
    offset: Long,
    destination: UShortArray,
    destinationOffset: Int = 0,
    count: Int = destination.size - destinationOffset
) {
    loadShortArray(offset, destination.asShortArray(), destinationOffset, count)
}

/**
 * Copies regular integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
expect fun Memory.loadIntArray(
    offset: Int,
    destination: IntArray,
    destinationOffset: Int = 0,
    count: Int = destination.size - destinationOffset
)

/**
 * Copies regular integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
expect fun Memory.loadIntArray(
    offset: Long,
    destination: IntArray,
    destinationOffset: Int = 0,
    count: Int = destination.size - destinationOffset
)

/**
 * Copies unsigned integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
inline fun Memory.loadUIntArray(
    offset: Int,
    destination: UIntArray,
    destinationOffset: Int = 0,
    count: Int = destination.size - destinationOffset
) {
    loadIntArray(offset, destination.asIntArray(), destinationOffset, count)
}

/**
 * Copies unsigned integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
inline fun Memory.loadUIntArray(
    offset: Long,
    destination: UIntArray,
    destinationOffset: Int = 0,
    count: Int = destination.size - destinationOffset
) {
    loadIntArray(offset, destination.asIntArray(), destinationOffset, count)
}

/**
 * Copies long integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
expect fun Memory.loadLongArray(
    offset: Int,
    destination: LongArray,
    destinationOffset: Int = 0,
    count: Int = destination.size - destinationOffset
)

/**
 * Copies long integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
expect fun Memory.loadLongArray(
    offset: Long,
    destination: LongArray,
    destinationOffset: Int = 0,
    count: Int = destination.size - destinationOffset
)

/**
 * Copies unsigned long integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
inline fun Memory.loadULongArray(
    offset: Int,
    destination: ULongArray,
    destinationOffset: Int = 0,
    count: Int = destination.size - destinationOffset
) {
    loadLongArray(offset, destination.asLongArray(), destinationOffset, count)
}

/**
 * Copies unsigned long integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
inline fun Memory.loadULongArray(
    offset: Long,
    destination: ULongArray,
    destinationOffset: Int = 0,
    count: Int = destination.size - destinationOffset
) {
    loadLongArray(offset, destination.asLongArray(), destinationOffset, count)
}

/**
 * Copies floating point numbers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
expect fun Memory.loadFloatArray(
    offset: Int,
    destination: FloatArray,
    destinationOffset: Int = 0,
    count: Int = destination.size - destinationOffset
)

/**
 * Copies floating point numbers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
expect fun Memory.loadFloatArray(
    offset: Long,
    destination: FloatArray,
    destinationOffset: Int = 0,
    count: Int = destination.size - destinationOffset
)

/**
 * Copies floating point numbers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
expect fun Memory.loadDoubleArray(
    offset: Int,
    destination: DoubleArray,
    destinationOffset: Int = 0,
    count: Int = destination.size - destinationOffset
)

/**
 * Copies floating point numbers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
expect fun Memory.loadDoubleArray(
    offset: Long,
    destination: DoubleArray,
    destinationOffset: Int = 0,
    count: Int = destination.size - destinationOffset
)

/**
 * Copies unsigned shorts integers from the [source] array at [sourceOffset] to this memory at the specified [offset].
 * @param sourceOffset items
 */
inline fun Memory.storeByteArray(
    offset: Int,
    source: ByteArray,
    sourceOffset: Int = 0,
    count: Int = source.size - sourceOffset
) {
    source.useMemory(sourceOffset, count) { sourceMemory ->
        sourceMemory.copyTo(this, 0, count, offset)
    }
}

/**
 * Copies unsigned shorts integers from the [source] array at [sourceOffset] to this memory at the specified [offset].
 * @param sourceOffset items
 */
inline fun Memory.storeByteArray(
    offset: Long,
    source: ByteArray,
    sourceOffset: Int = 0,
    count: Int = source.size - sourceOffset
) {
    source.useMemory(sourceOffset, count) { sourceMemory ->
        sourceMemory.copyTo(this, 0L, count.toLong(), offset)
    }
}

/**
 * Copies unsigned shorts integers from the [source] array at [sourceOffset] to this memory at the specified [offset].
 * @param sourceOffset items
 */
inline fun Memory.storeUByteArray(
    offset: Int,
    source: UByteArray,
    sourceOffset: Int = 0,
    count: Int = source.size - sourceOffset
) {
    storeByteArray(offset, source.asByteArray(), sourceOffset, count)
}

/**
 * Copies unsigned shorts integers from the [source] array at [sourceOffset] to this memory at the specified [offset].
 * @param sourceOffset items
 */
inline fun Memory.storeUByteArray(
    offset: Long,
    source: UByteArray,
    sourceOffset: Int = 0,
    count: Int = source.size - sourceOffset
) {
    storeByteArray(offset, source.asByteArray(), sourceOffset, count)
}

/**
 * Copies shorts integers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
expect fun Memory.storeShortArray(
    offset: Int,
    source: ShortArray,
    sourceOffset: Int = 0,
    count: Int = source.size - sourceOffset
)

/**
 * Copies shorts integers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
expect fun Memory.storeShortArray(
    offset: Long,
    source: ShortArray,
    sourceOffset: Int = 0,
    count: Int = source.size - sourceOffset
)

/**
 * Copies unsigned shorts integers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
inline fun Memory.storeUShortArray(
    offset: Int,
    source: UShortArray,
    sourceOffset: Int = 0,
    count: Int = source.size - sourceOffset
) {
    storeShortArray(offset, source.asShortArray(), sourceOffset, count)
}

/**
 * Copies unsigned shorts integers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
inline fun Memory.storeUShortArray(
    offset: Long,
    source: UShortArray,
    sourceOffset: Int = 0,
    count: Int = source.size - sourceOffset
) {
    storeShortArray(offset, source.asShortArray(), sourceOffset, count)
}

/**
 * Copies regular integers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
expect fun Memory.storeIntArray(
    offset: Int,
    source: IntArray,
    sourceOffset: Int = 0,
    count: Int = source.size - sourceOffset
)

/**
 * Copies regular integers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
expect fun Memory.storeIntArray(
    offset: Long,
    source: IntArray,
    sourceOffset: Int = 0,
    count: Int = source.size - sourceOffset
)

/**
 * Copies unsigned integers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
inline fun Memory.storeUIntArray(
    offset: Int,
    source: UIntArray,
    sourceOffset: Int = 0,
    count: Int = source.size - sourceOffset
) {
    storeIntArray(offset, source.asIntArray(), sourceOffset, count)
}

/**
 * Copies unsigned integers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
inline fun Memory.storeUIntArray(
    offset: Long,
    source: UIntArray,
    sourceOffset: Int = 0,
    count: Int = source.size - sourceOffset
) {
    storeIntArray(offset, source.asIntArray(), sourceOffset, count)
}

/**
 * Copies long integers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
expect fun Memory.storeLongArray(
    offset: Int,
    source: LongArray,
    sourceOffset: Int = 0,
    count: Int = source.size - sourceOffset
)

/**
 * Copies long integers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
expect fun Memory.storeLongArray(
    offset: Long,
    source: LongArray,
    sourceOffset: Int = 0,
    count: Int = source.size - sourceOffset
)

/**
 * Copies unsigned long integers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
inline fun Memory.storeULongArray(
    offset: Int,
    source: ULongArray,
    sourceOffset: Int = 0,
    count: Int = source.size - sourceOffset
) {
    storeLongArray(offset, source.asLongArray(), sourceOffset, count)
}

/**
 * Copies unsigned long integers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
inline fun Memory.storeULongArray(
    offset: Long,
    source: ULongArray,
    sourceOffset: Int = 0,
    count: Int = source.size - sourceOffset
) {
    storeLongArray(offset, source.asLongArray(), sourceOffset, count)
}

/**
 * Copies floating point numbers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
expect fun Memory.storeFloatArray(
    offset: Int,
    source: FloatArray,
    sourceOffset: Int = 0,
    count: Int = source.size - sourceOffset
)

/**
 * Copies floating point numbers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
expect fun Memory.storeFloatArray(
    offset: Long,
    source: FloatArray,
    sourceOffset: Int = 0,
    count: Int = source.size - sourceOffset
)

/**
 * Copies floating point numbers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
expect fun Memory.storeDoubleArray(
    offset: Int,
    source: DoubleArray,
    sourceOffset: Int = 0,
    count: Int = source.size - sourceOffset
)

/**
 * Copies floating point numbers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
expect fun Memory.storeDoubleArray(
    offset: Long,
    source: DoubleArray,
    sourceOffset: Int = 0,
    count: Int = source.size - sourceOffset
)

