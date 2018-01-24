package io.ktor.util

import io.ktor.cio.*
import java.nio.*
import java.nio.charset.*

/**
 * Moves bytes from `this` buffer to the [destination] buffer
 *
 * @param destination is the buffer to copy bytes to
 * @param limit is an optional parameter specifying maximum number of bytes to be moved
 * @return number of bytes moved
 */
fun ByteBuffer.moveTo(destination: ByteBuffer, limit: Int = Int.MAX_VALUE): Int {
    val size = minOf(limit, remaining(), destination.remaining())
    for (i in 1..size) {
        destination.put(get())
    }
    return size
}

/**
 * Moves bytes from `this` buffer into newly created [ByteArray] and returns it
 */
fun ByteBuffer.moveToByteArray(): ByteArray {
    val array = ByteArray(remaining())
    get(array)
    return array
}

/**
 * Decodes a string from `this` buffer with the specified [charset]
 */
fun ByteBuffer.decodeString(charset: Charset = Charsets.UTF_8): String {
    return charset.decode(this).toString()
}

/**
 * Moves all bytes in `this` buffer to a newly created buffer with the optionally specified [size]
 */
fun ByteBuffer.copy(size: Int = remaining()): ByteBuffer {
    return ByteBuffer.allocate(size).apply {
        this@copy.slice().moveTo(this@apply)
        clear()
    }
}

/**
 * Moves all bytes in `this` buffer to a newly created buffer with the optionally specified [size] by allocating it from the given [pool]
 */
fun ByteBuffer.copy(pool: ByteBufferPool, size: Int = remaining()): PoolTicket {
    return pool.allocate(size).apply {
        buffer.clear()
        this@copy.slice().moveTo(buffer)
        flip()
    }
}

/**
 * Helper class for building [ByteBuffer] with the specific content
 */
@Deprecated("Use BytePacketBuilder instead")
class ByteBufferBuilder(order: ByteOrder = ByteOrder.BIG_ENDIAN) {
    companion object {
        @Deprecated("Use buildPacket instead",
                ReplaceWith("buildPacket(block).readByteBuffer()",
                        "kotlinx.io.core.buildPacket", "kotlinx.io.core.readByteBuffer"))
        inline fun build(order: ByteOrder = ByteOrder.BIG_ENDIAN, block: ByteBufferBuilder.() -> Unit): ByteBuffer {
            return ByteBufferBuilder(order).apply(block).build()
        }
    }

    private var buffer: ByteBuffer = ByteBuffer.allocate(16).also { it.order(order) }

    /**
     * Puts bytes from [other] [ByteBuffer] into this builder
     */
    fun put(other: ByteBuffer) {
        ensureBufferSize(other.remaining())

        buffer.put(other)
    }

    /**
     * Puts [byte] value into this builder
     */
    fun put(byte: Byte) {
        ensureBufferSize(1)
        buffer.put(byte)
    }

    /**
     * Puts [short] value into this builder
     */
    fun putShort(short: Short) {
        ensureBufferSize(2)
        buffer.putShort(short)
    }

    /**
     * Puts [integer] value into this builder
     */
    fun putInt(integer: Int) {
        ensureBufferSize(4)
        buffer.putInt(integer)
    }

    /**
     * Puts [String] value into this builder using specified [charset]
     */
    fun putString(string: String, charset: Charset) {
        val cb = CharBuffer.wrap(string)
        val encoder = charset.newEncoder()

        while (cb.hasRemaining()) {
            ensureBufferSize(cb.remaining())
            val result = encoder.encode(cb, buffer, false)

            when {
                result.isError -> result.throwException()
                result.isOverflow -> ensureBufferSize((cb.remaining() * encoder.maxBytesPerChar()).toInt())
            }
        }

        finish@ do {
            val result = encoder.encode(cb, buffer, true)

            when {
                result.isError -> result.throwException()
                result.isOverflow -> ensureBufferSize(encoder.maxBytesPerChar().toInt())
                else -> break@finish
            }
        } while (true)
    }

    /**
     * Builds a [ByteBuffer] from the accumulated data
     */
    fun build(): ByteBuffer {
        val src = buffer.duplicate()
        src.flip()
        return src.copy(buffer.position())
    }

    private fun ensureBufferSize(size: Int) {
        if (buffer.remaining() < size) {
            val used = buffer.position()
            var newSize = buffer.capacity()

            while (newSize != Int.MAX_VALUE && newSize - used < size) {
                newSize = newSize shl 1
            }

            grow(newSize)
        }
    }

    private fun grow(newSize: Int) {
        if (newSize != buffer.capacity()) {
            val oldPosition = buffer.position()
            buffer.flip()
            buffer = buffer.copy(newSize)
            buffer.position(oldPosition)
        }
    }
}

