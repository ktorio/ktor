@file:Suppress("NOTHING_TO_INLINE")

package io.ktor.utils.io.bits

/**
 * Memory instance with 0 size.
 */
public expect val MEMORY_EMPTY: Memory

/**
 * Represents a linear range of bytes.
 * All operations are guarded by range-checks by default however at some platforms they could be disabled
 * in release builds.
 *
 * Instance of this class has no additional state except the bytes themselves.
 */
public expect abstract class Memory

/**
 * Size of memory range in bytes.
 */
public expect val Memory.size: Long

/**
 * Size of memory range in bytes represented as signed 32bit integer
 * @throws IllegalStateException when size doesn't fit into a signed 32bit integer
 */
public expect val Memory.size32: Int

/**
 * Returns byte at [index] position.
 */
public expect inline fun Memory.loadAt(index: Int): Byte

/**
 * Returns byte at [index] position.
 */
public expect inline fun Memory.loadAt(index: Long): Byte

/**
 * Write [value] at the specified [index].
 */
public expect inline fun Memory.storeAt(index: Int, value: Byte)

/**
 * Write [value] at the specified [index]
 */
public expect inline fun Memory.storeAt(index: Long, value: Byte)

/**
 * Returns memory's subrange. On some platforms it could do range checks but it is not guaranteed to be safe.
 * It also could lead to memory allocations on some platforms.
 */
public expect fun Memory.slice(offset: Int, length: Int): Memory

/**
 * Returns memory's subrange. On some platforms it could do range checks but it is not guaranteed to be safe.
 * It also could lead to memory allocations on some platforms.
 */
public expect fun Memory.slice(offset: Long, length: Long): Memory

/**
 * Copies bytes from this memory range from the specified [offset] and [length]
 * to the [destination] at [destinationOffset].
 * Copying bytes from a memory to itself is allowed.
 */
public expect fun Memory.copyTo(destination: Memory, offset: Int, length: Int, destinationOffset: Int)

/**
 * Copies bytes from this memory range from the specified [offset] and [length]
 * to the [destination] at [destinationOffset].
 * Copying bytes from a memory to itself is allowed.
 */
public expect fun Memory.copyTo(destination: Memory, offset: Long, length: Long, destinationOffset: Long)

/**
 * Read byte at the specified [index].
 */
public inline operator fun Memory.get(index: Int): Byte = loadAt(index)

/**
 * Read byte at the specified [index].
 */
public inline operator fun Memory.get(index: Long): Byte = loadAt(index)

/**
 * Index write operator to write [value] at the specified [index]
 */
public inline operator fun Memory.set(index: Long, value: Byte): Unit = storeAt(index, value)

/**
 * Index write operator to write [value] at the specified [index]
 */
public inline operator fun Memory.set(index: Int, value: Byte): Unit = storeAt(index, value)

/**
 * Index write operator to write [value] at the specified [index]
 */
public inline fun Memory.storeAt(index: Long, value: UByte): Unit = storeAt(index, value.toByte())

/**
 * Index write operator to write [value] at the specified [index]
 */
public inline fun Memory.storeAt(index: Int, value: UByte): Unit = storeAt(index, value.toByte())

/**
 * Fill memory range starting at the specified [offset] with [value] repeated [count] times.
 */
public expect fun Memory.fill(offset: Long, count: Long, value: Byte)

/**
 * Fill memory range starting at the specified [offset] with [value] repeated [count] times.
 */
public expect fun Memory.fill(offset: Int, count: Int, value: Byte)

/**
 * Copies bytes from this memory range from the specified [offset] and [length]
 * to the [destination].
 */
public fun Memory.copyTo(destination: ByteArray, offset: Int, length: Int) {
    copyTo(destination, offset, length, destinationOffset = 0)
}

/**
 * Copies bytes from this memory range from the specified [offset] and [length]
 * to the [destination] at [destinationOffset].
 */
public expect fun Memory.copyTo(destination: ByteArray, offset: Int, length: Int, destinationOffset: Int)

/**
 * Copies bytes from this memory range from the specified [offset] and [length]
 * to the [destination].
 */
public fun Memory.copyTo(destination: ByteArray, offset: Long, length: Int) {
    copyTo(destination, offset, length, destinationOffset = 0)
}

/**
 * Copies bytes from this memory range from the specified [offset] and [length]
 * to the [destination] at [destinationOffset].
 */
public expect fun Memory.copyTo(destination: ByteArray, offset: Long, length: Int, destinationOffset: Int)
