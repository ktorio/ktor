package org.jetbrains.ktor.nio

import java.nio.*
import java.nio.charset.*

class ByteArrayWriteChannel : WriteChannel {
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

    override fun write(src: ByteBuffer, handler: AsyncHandler) {
        val size = src.remaining()
        ensureCapacity(count + size)
        src.get(buf, count, size)
        count += size

        handler.success(size)
    }

    fun ensureCapacity(size: Int) {
        if (buf.size < size) {
            val newBuffer = ByteArray(size)
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