package io.ktor.cio

import io.ktor.util.*
import java.nio.*
import java.nio.charset.*

class ByteBufferReadChannel(val source: ByteBuffer, val maxReadSize: Int = Int.MAX_VALUE) : RandomAccessReadChannel {
    private val initialPosition = source.position()

    constructor(source: ByteArray, maxReadSize: Int = Int.MAX_VALUE) : this(ByteBuffer.wrap(source), maxReadSize)

    init {
        require(maxReadSize > 0) { "maxReadSize should be positive: $maxReadSize" }
    }

    override val size: Long
        get() = source.limit().toLong()

    override val position: Long
        get() = (source.position() - initialPosition).toLong()

    override suspend fun seek(position: Long) {
        val newPosition = initialPosition + Math.min(maxReadSize.toLong(), position).toInt()
        if (newPosition > source.limit()) {
            throw IllegalArgumentException("Seek to $position failed for buffer size ${source.limit() - initialPosition}")
        } else {
            source.position(initialPosition + position.toInt())
        }
    }

    override suspend fun read(dst: ByteBuffer): Int {
        if (!source.hasRemaining()) {
            return -1
        }

        return source.putTo(dst, maxReadSize)
    }

    override fun close() {}
}

class ByteBufferWriteChannel : WriteChannel {
    private var buf: ByteArray = EMPTY
    private var count = 0

    fun toByteArray() = if (buf === EMPTY) buf else buf.copyOf(count)
    fun toString(charset: Charset) = if (buf.isEmpty()) "" else String(buf, 0, count, charset)

    fun reset(): ByteArray {
        val result = buf
        count = 0
        buf = EMPTY

        return result
    }

    override fun close() {
    }

    suspend override fun flush() {
    }

    override suspend fun write(src: ByteBuffer) {
        val size = src.remaining()
        ensureCapacity(count + size)
        src.get(buf, count, size)
        count += size
    }

    fun ensureCapacity(size: Int) {
        if (buf.size < size) {
            var newSize = buf.size.coerceAtLeast(16)
            while (newSize < size && newSize > 0) {
                newSize = newSize shl 1
            }
            if (newSize < 0) newSize = size

            val newBuffer = ByteArray(newSize)
            if (count > 0) {
                System.arraycopy(buf, 0, newBuffer, 0, count)
            }
            buf = newBuffer
        }
    }

    companion object {
        private val EMPTY = ByteArray(0)
    }
}

fun ByteBuffer.toReadChannel() = ByteBufferReadChannel(this)
fun ByteArray.toReadChannel() = ByteBufferReadChannel(this)