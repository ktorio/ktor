package io.ktor.util

import kotlinx.io.pool.*
import java.nio.*
import java.nio.charset.*

/**
 * Moves bytes from `this` buffer to the [destination] buffer
 *
 * @param destination is the buffer to copy bytes to
 * @param limit is an optional parameter specifying maximum number of bytes to be moved
 * @return number of bytes moved
 */
@InternalAPI
fun ByteBuffer.moveTo(destination: ByteBuffer, limit: Int = Int.MAX_VALUE): Int {
    val size = minOf(limit, remaining(), destination.remaining())
    if (size == remaining()) {
        destination.put(this)
    } else {
        val l = limit()
        limit(position() + size)
        destination.put(this)
        limit(l)
    }
    return size
}

/**
 * Moves bytes from `this` buffer into newly created [ByteArray] and returns it
 */
@InternalAPI
fun ByteBuffer.moveToByteArray(): ByteArray {
    val array = ByteArray(remaining())
    get(array)
    return array
}

/**
 * Decodes a string from `this` buffer with the specified [charset]
 */
@InternalAPI
fun ByteBuffer.decodeString(charset: Charset = Charsets.UTF_8): String {
    return charset.decode(this).toString()
}

/**
 * Moves all bytes in `this` buffer to a newly created buffer with the optionally specified [size]
 */
@InternalAPI
fun ByteBuffer.copy(size: Int = remaining()): ByteBuffer {
    return ByteBuffer.allocate(size).apply {
        this@copy.slice().moveTo(this@apply)
        clear()
    }
}

/**
 * Moves all bytes in `this` buffer to a newly created buffer with the optionally specified [size] by allocating it from the given [pool]
 */
@InternalAPI
fun ByteBuffer.copy(pool: ObjectPool<ByteBuffer>, size: Int = remaining()): ByteBuffer = pool.borrow().apply {
    limit(size)
    this@copy.slice().moveTo(this)
    flip()
}

/**
 * Helper class for building [ByteBuffer] with the specific content
 */
@Deprecated("Use BytePacketBuilder instead", level = DeprecationLevel.ERROR)
@Suppress("UNUSED_PARAMETER")
class ByteBufferBuilder(order: ByteOrder = ByteOrder.BIG_ENDIAN) {
    companion object {
        @Deprecated("Use buildPacket instead", level = DeprecationLevel.ERROR,
                replaceWith = ReplaceWith("buildPacket(block).readByteBuffer()",
                        "kotlinx.io.core.buildPacket", "kotlinx.io.core.readByteBuffer"))
        @Suppress("DEPRECATION_ERROR")
        inline fun build(order: ByteOrder = ByteOrder.BIG_ENDIAN, block: ByteBufferBuilder.() -> Unit): ByteBuffer {
            return ByteBufferBuilder(order).apply(block).build()
        }
    }

    /**
     * Puts bytes from [other] [ByteBuffer] into this builder
     */
    fun put(other: ByteBuffer): Unit = TODO()

    /**
     * Puts [byte] value into this builder
     */
    fun put(byte: Byte): Unit = TODO()

    /**
     * Puts [short] value into this builder
     */
    fun putShort(short: Short): Unit = TODO()

    /**
     * Puts [integer] value into this builder
     */
    fun putInt(integer: Int): Unit = TODO()

    /**
     * Puts [String] value into this builder using specified [charset]
     */
    fun putString(string: String, charset: Charset): Unit = TODO()

    /**
     * Builds a [ByteBuffer] from the accumulated data
     */
    fun build(): ByteBuffer = TODO()
}

