package io.ktor.cio

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.pool.*
import java.io.*
import java.nio.ByteBuffer

fun InputStream.toByteReadChannel(
        pool: ObjectPool<ByteBuffer> = KtorDefaultPool,
        parent: Job = Job()
): ByteReadChannel = writer(Unconfined, parent = parent, autoFlush = true) {
    val buffer = pool.borrow()
    try {
        while (true) {
            buffer.clear()
            val readCount = read(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())
            if (readCount < 0) break
            if (readCount == 0) continue

            buffer.position(buffer.position() + readCount)
            buffer.flip()
            channel.writeFully(buffer)
        }
    } catch (cause: Throwable) {
        channel.close(cause)
    } finally {
        pool.recycle(buffer)
        close()
    }
}.channel
