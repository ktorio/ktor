package org.jetbrains.ktor.util

import org.jetbrains.ktor.cio.*
import java.nio.*
import java.nio.charset.*

fun ByteBuffer.putTo(other: ByteBuffer, limit: Int = Int.MAX_VALUE): Int {
    val size = minOf(limit, remaining(), other.remaining())
    for (i in 1..size) {
        other.put(get())
    }
    return size
}

fun ByteBuffer.getString(charset: Charset = Charsets.UTF_8) = charset.decode(this).toString()

fun ByteBuffer.copy(newSize: Int = remaining()): ByteBuffer = ByteBuffer.allocate(newSize).apply { this@copy.slice().putTo(this@apply); clear() }

fun ByteBuffer.copy(pool: ByteBufferPool, newSize: Int = remaining()): PoolTicket = pool.allocate(newSize).apply { buffer.clear(); this@copy.slice().putTo(buffer); flip() }

fun buildByteBuffer(order: ByteOrder = ByteOrder.BIG_ENDIAN, block: ByteBufferBuilder.() -> Unit) = ByteBufferBuilder(order).apply { block() }.build()

class ByteBufferBuilder(order: ByteOrder = ByteOrder.BIG_ENDIAN) {
    private var bb: ByteBuffer = ByteBuffer.allocate(16)

    init {
        bb.order(order)
    }

    fun put(b: Byte) {
        ensureBufferSize(1)

        bb.put(b)
    }

    fun putShort(s: Short) {
        ensureBufferSize(2)
        bb.putShort(s)
    }

    fun putInt(i: Int) {
        ensureBufferSize(4)
        bb.putInt(i)
    }

    fun putString(s: String, charset: Charset) {
        val cb = CharBuffer.wrap(s)
        val encoder = charset.newEncoder()

        while (cb.hasRemaining()) {
            ensureBufferSize(cb.remaining())
            val result = encoder.encode(cb, bb, false)

            when {
                result.isError -> result.throwException()
                result.isOverflow -> ensureBufferSize((cb.remaining() * encoder.maxBytesPerChar()).toInt())
            }
        }

        finish@do {
            val result = encoder.encode(cb, bb, true)

            when {
                result.isError -> result.throwException()
                result.isOverflow -> ensureBufferSize(encoder.maxBytesPerChar().toInt())
                else -> break@finish
            }
        } while (true)
    }

    fun build() = bb.duplicate().let { src -> src.flip(); src.copy(bb.position()) }

    private fun ensureBufferSize(size: Int) {
        if (bb.remaining() < size) {
            val used = bb.position()
            var newSize = bb.capacity()

            while (newSize != Int.MAX_VALUE && newSize - used < size) {
                newSize = newSize shl 1
            }

            grow(newSize)
        }
    }

    private fun grow(newSize: Int) {
        if (newSize != bb.capacity()) {
            val oldPosition = bb.position()
            bb.flip()
            bb = bb.copy(newSize)
            bb.position(oldPosition)
        }
    }
}

fun ByteBuffer.getAll(): ByteArray {
    val array = ByteArray(remaining())
    get(array)
    return array
}
