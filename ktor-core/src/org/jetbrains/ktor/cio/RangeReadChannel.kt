package org.jetbrains.ktor.cio

import java.nio.*

class RangeReadChannel(val source: ReadChannel, val skip: Long, val maxSize: Long, val bufferPool: ByteBufferPool = NoPool) : ReadChannel {
    var totalCount = 0
    var skipped = false

    suspend override fun read(dst: ByteBuffer): Int {
        if (!skipped) {
            if (source is RandomAccessReadChannel) {
                source.seek(skip)
            } else {
                var skipped = 0
                val skipTicket = bufferPool.allocate(8192)
                val skipBuffer = skipTicket.buffer
                while (skipped < skip) {
                    skipBuffer.clear()
                    val remaining = skip - skipped
                    if (remaining < skipBuffer.capacity())
                        skipBuffer.limit(remaining.toInt())

                    val read = source.read(skipBuffer)
                    if (read == -1)
                        return -1
                    skipped += read
                }
            }
            skipped = true
        }

        val remaining = maxSize - totalCount
        if (remaining == 0L)
            return -1
        val count = if (remaining < dst.remaining()) {
            val wrapped = dst.slice()
            wrapped.limit(remaining.toInt())
            source.read(wrapped)
        } else {
            source.read(dst)
        }
        totalCount += count
        return count
    }

    override fun close() {
        source.close()
    }
}
