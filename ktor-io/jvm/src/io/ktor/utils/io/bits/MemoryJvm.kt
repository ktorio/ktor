@file:Suppress("NOTHING_TO_INLINE")

package io.ktor.utils.io.bits

import io.ktor.utils.io.core.internal.*
import java.nio.*

/**
 * Memory instance with 0 size.
 */
public actual val MEMORY_EMPTY: Memory = Memory(ByteBuffer.allocate(0).order(ByteOrder.BIG_ENDIAN))

/**
 * Represents a linear range of bytes.
 * All operations are guarded by range-checks by default however at some platforms they could be disabled
 * in release builds.
 *
 * Instance of this class has no additional state except the bytes themselves.
 */
public actual typealias Memory = ByteBuffer

/**
 * Create memory form [buffer].
 */
public fun Memory(buffer: ByteBuffer): ByteBuffer = buffer.slice().order(ByteOrder.BIG_ENDIAN)

/**
 * Reference to the internal buffer
 */
public val Memory.buffer: ByteBuffer get() = this

/**
 * Size of memory range in bytes.
 */
public actual inline val Memory.size: Long get() = limit().toLong()

/**
 * Size of memory range in bytes represented as signed 32bit integer
 * @throws IllegalStateException when size doesn't fit into a signed 32bit integer
 */
public actual inline val Memory.size32: Int get() = limit()

/**
 * Returns byte at [index] position.
 */
public actual inline fun Memory.loadAt(index: Int): Byte = get(index)

/**
 * Returns byte at [index] position.
 */
public actual inline fun Memory.loadAt(index: Long): Byte = get(index.toIntOrFail("index"))

/**
 * Write [value] at the specified [index].
 */
public actual inline fun Memory.storeAt(index: Int, value: Byte) {
    put(index, value)
}

/**
 * Write [value] at the specified [index]
 */
public actual inline fun Memory.storeAt(index: Long, value: Byte) {
    put(index.toIntOrFail("index"), value)
}

public actual fun Memory.slice(offset: Int, length: Int): Memory =
    Memory(sliceSafe(offset, length))

public actual fun Memory.slice(offset: Long, length: Long): Memory {
    return slice(offset.toIntOrFail("offset"), length.toIntOrFail("length"))
}

/**
 * Copies bytes from this memory range from the specified [offset] and [length]
 * to the [destination] at [destinationOffset].
 * Copying bytes from a memory to itself is allowed.
 */
public actual fun Memory.copyTo(destination: Memory, offset: Int, length: Int, destinationOffset: Int) {
    if (hasArray() && destination.hasArray() &&
        !isReadOnly && !destination.isReadOnly
    ) {
        System.arraycopy(
            array(),
            arrayOffset() + offset,
            destination.array(),
            destination.arrayOffset() + destinationOffset,
            length
        )
        return
    }

    // NOTE: it is ok here to make copy since it will be escaped by JVM
    // while temporary moving position/offset makes memory concurrent unsafe that is unacceptable

    val srcCopy = duplicate().apply {
        position(offset)
        limit(offset + length)
    }
    val dstCopy = destination.duplicate().apply {
        position(destinationOffset)
    }

    dstCopy.put(srcCopy)
}

/**
 * Copies bytes from this memory range from the specified [offset] and [length]
 * to the [destination] at [destinationOffset].
 * Copying bytes from a memory to itself is allowed.
 */
public actual fun Memory.copyTo(destination: Memory, offset: Long, length: Long, destinationOffset: Long) {
    copyTo(
        destination,
        offset.toIntOrFail("offset"),
        length.toIntOrFail("length"),
        destinationOffset.toIntOrFail("destinationOffset")
    )
}

/**
 * Copies bytes from this memory range from the specified [offset] and [length]
 * to the [destination] at [destinationOffset].
 */
public actual fun Memory.copyTo(
    destination: ByteArray,
    offset: Int,
    length: Int,
    destinationOffset: Int
) {
    if (buffer.hasArray() && !buffer.isReadOnly) {
        System.arraycopy(
            buffer.array(),
            buffer.arrayOffset() + offset,
            destination,
            destinationOffset,
            length
        )
        return
    }

    // we need to make a copy to prevent moving position
    buffer.duplicate().get(destination, destinationOffset, length)
}

/**
 * Copies bytes from this memory range from the specified [offset] and [length]
 * to the [destination] at [destinationOffset].
 */
public actual fun Memory.copyTo(
    destination: ByteArray,
    offset: Long,
    length: Int,
    destinationOffset: Int
) {
    copyTo(destination, offset.toIntOrFail("offset"), length, destinationOffset)
}

/**
 * Copies bytes from this memory range from the specified [offset]
 * to the [destination] buffer.
 */
public fun Memory.copyTo(
    destination: ByteBuffer,
    offset: Int
) {
    val size = destination.remaining()

    if (buffer.hasArray() && !buffer.isReadOnly &&
        destination.hasArray() && !destination.isReadOnly
    ) {
        val dstPosition = destination.position()

        System.arraycopy(
            buffer.array(),
            buffer.arrayOffset() + offset,
            destination.array(),
            destination.arrayOffset() + dstPosition,
            size
        )
        destination.position(dstPosition + size)
        return
    }

    // we need to make a copy to prevent moving position
    val source = buffer.duplicate().apply {
        limit(offset + size)
        position(offset)
    }
    destination.put(source)
}

/**
 * Copies bytes from this memory range from the specified [offset]
 * to the [destination] buffer.
 */
public fun Memory.copyTo(destination: ByteBuffer, offset: Long) {
    copyTo(destination, offset.toIntOrFail("offset"))
}

/**
 * Copy byte from this buffer moving it's position to the [destination] at [offset].
 */
public fun ByteBuffer.moveTo(destination: Memory, offset: Int) {
    if (hasArray() && !isReadOnly) {
        destination.storeByteArray(offset, array(), arrayOffset() + position(), remaining())
        position(limit())
        return
    }

    destination.buffer.sliceSafe(offset, remaining()).put(this)
}

private inline fun ByteBuffer.myDuplicate(): ByteBuffer {
    duplicate().apply { return suppressNullCheck() }
}

private inline fun ByteBuffer.mySlice(): ByteBuffer {
    slice().apply { return suppressNullCheck() }
}

private inline fun ByteBuffer.suppressNullCheck(): ByteBuffer {
    return this
}

internal fun ByteBuffer.sliceSafe(offset: Int, length: Int): ByteBuffer {
    return myDuplicate().apply { position(offset); limit(offset + length) }.mySlice()
}

/**
 * Fill memory range starting at the specified [offset] with [value] repeated [count] times.
 */
public actual fun Memory.fill(offset: Long, count: Long, value: Byte) {
    fill(offset.toIntOrFail("offset"), count.toIntOrFail("count"), value)
}

/**
 * Fill memory range starting at the specified [offset] with [value] repeated [count] times.
 */
public actual fun Memory.fill(offset: Int, count: Int, value: Byte) {
    for (index in offset until offset + count) {
        put(index, value)
    }
}
