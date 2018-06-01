package io.ktor.cio

import kotlinx.coroutines.experimental.io.*
import kotlinx.io.pool.*
import java.io.*
import java.nio.*

suspend fun ByteReadChannel.toByteArray(sizeHint: Int = 0, pool: ObjectPool<ByteBuffer> = KtorDefaultPool): ByteArray {
    val result = ByteArrayOutputStream(sizeHint)
    pool.use { buffer ->
        while (!isClosedForRead) {
            buffer.clear()
            val count = readAvailable(buffer)
            if (count == -1) break
            buffer.flip()

            result.write(buffer.array(), buffer.arrayOffset() + buffer.position(), count)
        }
    }

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

suspend fun ByteWriteChannel.use(block: suspend ByteWriteChannel.() -> Unit) {
    try {
        block()
    } catch (cause: Throwable) {
        close(cause)
        throw cause
    } finally {
        close()
    }
}
