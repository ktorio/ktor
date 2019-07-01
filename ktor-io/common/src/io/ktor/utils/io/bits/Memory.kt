@file:Suppress("NOTHING_TO_INLINE")

package io.ktor.utils.io.bits

/**
 * Represents a linear range of bytes.
 * All operations are guarded by range-checks by default however at some platforms they could be disabled
 * in release builds.
 *
 * Instance of this class has no additional state except the bytes themselves.
 */
expect class Memory {
    /**
     * Size of memory range in bytes.
     */
    val size: Long

    /**
     * Size of memory range in bytes represented as signed 32bit integer
     * @throws IllegalStateException when size doesn't fit into a signed 32bit integer
     */
    val size32: Int

    /**
     * Returns byte at [index] position.
     */
    inline fun loadAt(index: Int): Byte

    /**
     * Returns byte at [index] position.
     */
    inline fun loadAt(index: Long): Byte

    /**
     * Write [value] at the specified [index].
     */
    inline fun storeAt(index: Int, value: Byte)

    /**
     * Write [value] at the specified [index]
     */
    inline fun storeAt(index: Long, value: Byte)

    /**
     * Returns memory's subrange. On some platforms it could do range checks but it is not guaranteed to be safe.
     * It also could lead to memory allocations on some platforms.
     */
    fun slice(offset: Int, length: Int): Memory

    /**
     * Returns memory's subrange. On some platforms it could do range checks but it is not guaranteed to be safe.
     * It also could lead to memory allocations on some platforms.
     */
    fun slice(offset: Long, length: Long): Memory

    /**
     * Copies bytes from this memory range from the specified [offset] and [length]
     * to the [destination] at [destinationOffset].
     * Copying bytes from a memory to itself is allowed.
     */
    fun copyTo(destination: Memory, offset: Int, length: Int, destinationOffset: Int)

    /**
     * Copies bytes from this memory range from the specified [offset] and [length]
     * to the [destination] at [destinationOffset].
     * Copying bytes from a memory to itself is allowed.
     */
    fun copyTo(destination: Memory, offset: Long, length: Long, destinationOffset: Long)

    companion object {
        /**
         * Represents an empty memory region
         */
        val Empty: Memory
    }
}

/**
 * Read byte at the specified [index].
 */
inline operator fun Memory.get(index: Int): Byte = loadAt(index)

/**
 * Read byte at the specified [index].
 */
inline operator fun Memory.get(index: Long): Byte = loadAt(index)

/**
 * Index write operator to write [value] at the specified [index]
 */
inline operator fun Memory.set(index: Long, value: Byte) = storeAt(index, value)

/**
 * Index write operator to write [value] at the specified [index]
 */
inline operator fun Memory.set(index: Int, value: Byte) = storeAt(index, value)

/**
 * Index write operator to write [value] at the specified [index]
 */
inline fun Memory.storeAt(index: Long, value: UByte) = storeAt(index, value.toByte())

/**
 * Index write operator to write [value] at the specified [index]
 */
inline fun Memory.storeAt(index: Int, value: UByte) = storeAt(index, value.toByte())

/**
 * Fill memory range starting at the specified [offset] with [value] repeated [count] times.
 */
expect fun Memory.fill(offset: Long, count: Long, value: Byte)

/**
 * Fill memory range starting at the specified [offset] with [value] repeated [count] times.
 */
expect fun Memory.fill(offset: Int, count: Int, value: Byte)

/**
 * Copies bytes from this memory range from the specified [offset] and [length]
 * to the [destination] at [destinationOffset].
 */
expect fun Memory.copyTo(destination: ByteArray, offset: Int, length: Int, destinationOffset: Int = 0)

/**
 * Copies bytes from this memory range from the specified [offset] and [length]
 * to the [destination] at [destinationOffset].
 */
expect fun Memory.copyTo(destination: ByteArray, offset: Long, length: Int, destinationOffset: Int = 0)
