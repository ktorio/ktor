package io.ktor.cio

import kotlinx.coroutines.experimental.io.*
import kotlinx.io.pool.*
import java.io.*


suspend fun ByteReadChannel.toByteArray(sizeHint: Int = 0, pool: ObjectPool<ByteBuffer> = EmptyByteBufferPool): ByteArray {
    val result = ByteArrayOutputStream(sizeHint)
    val buffer = pool.borrow()

    while (!isClosedForRead) {
        buffer.clear()
        val count = readAvailable(buffer)
        if (count == -1) break
        buffer.flip()

        result.write(buffer.array(), buffer.arrayOffset() + buffer.position(), count)
    }

    pool.recycle(buffer)
    return result.toByteArray()
}

suspend fun ByteReadChannel.pass(buffer: ByteBuffer, block: suspend (ByteBuffer) -> Unit) {
    while (!isClosedForRead) {
        buffer.clear()
        readAvailable(buffer)

        buffer.flip()
        block(buffer)
    }
}