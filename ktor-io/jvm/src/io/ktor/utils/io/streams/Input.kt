package io.ktor.utils.io.streams

import io.ktor.utils.io.bits.Memory
import io.ktor.utils.io.bits.storeByteArray
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import java.io.*

internal class InputStreamAsInput(
    private val stream: InputStream,
    pool: ObjectPool<ChunkBuffer>
) : AbstractInput(pool = pool) {

    override fun fill(destination: Memory, offset: Int, length: Int): Int {
        if (destination.buffer.hasArray() && !destination.buffer.isReadOnly) {
            return stream.read(destination.buffer.array(), destination.buffer.arrayOffset() + offset, length)
                .coerceAtLeast(0)
        }

        val buffer = ByteArrayPool.borrow()
        try {
            val rc = stream.read(buffer, 0, minOf(buffer.size, length))
            if (rc == -1) return 0
            destination.storeByteArray(offset, buffer, 0, rc)
            return rc
        } finally {
            ByteArrayPool.recycle(buffer)
        }
    }

    override fun closeSource() {
        stream.close()
    }
}

fun InputStream.asInput(pool: ObjectPool<ChunkBuffer> = ChunkBuffer.Pool): Input = InputStreamAsInput(this, pool)
