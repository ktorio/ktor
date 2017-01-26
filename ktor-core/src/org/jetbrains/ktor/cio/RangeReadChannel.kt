package org.jetbrains.ktor.cio

import java.nio.*

class RangeReadChannel(val source: ReadChannel, val skip: Long, val maxSize: Long, val bufferPool: ByteBufferPool = NoPool) : ReadChannel {
    var totalCount = 0
    var skipped = false

    suspend override fun read(dst: ByteBuffer): Int {
        if (!skipped) {
            when (source) {
                is RandomAccessReadChannel -> {
                    if (source.size <= skip) {
                        skipped = true
                        return -1
                    }
                    source.seek(skip)
                }
                else -> if (!seekBySkip()) {
                    skipped = true
                    return -1
                }
            }
            skipped = true
        }

        val remaining = maxSize - totalCount
        if (remaining == 0L)
            return -1
        val count = if (remaining < dst.remaining()) {
            dst.limit(dst.position() + remaining.toInt())
            source.read(dst)
        } else {
            source.read(dst)
        }
        totalCount += count
        return count
    }

    private suspend fun seekBySkip(): Boolean {
        var skipped = 0
        val skipTicket = bufferPool.allocate(8192)
        val skipBuffer = skipTicket.buffer
        try {
            while (skipped < skip) {
                skipBuffer.clear()
                val remaining = skip - skipped
                if (remaining < skipBuffer.capacity())
                    skipBuffer.limit(remaining.toInt())

                val read = source.read(skipBuffer)
                if (read == -1)
                    return false
                skipped += read
            }
        } finally {
            bufferPool.release(skipTicket)
        }
        return true
    }

    override fun close() {
        source.close()
    }
}
