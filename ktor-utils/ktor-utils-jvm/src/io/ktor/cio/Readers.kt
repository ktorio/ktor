package io.ktor.cio

import kotlinx.coroutines.experimental.io.*
import kotlinx.io.core.*
import java.nio.*

/**
 * Convert [ByteReadChannel] to [ByteArray]
 */
suspend fun ByteReadChannel.toByteArray(limit: Int = Int.MAX_VALUE): ByteArray =
    readRemaining(limit.toLong()).readBytes()

/**
 * Read data chunks from [ByteReadChannel] using buffer
 */
suspend fun ByteReadChannel.pass(buffer: ByteBuffer, block: suspend (ByteBuffer) -> Unit) {
    while (!isClosedForRead) {
        buffer.clear()
        readAvailable(buffer)

        buffer.flip()
        block(buffer)
    }
}

/**
 * Executes [block] on [ByteWriteChannel] and close it down correctly whether an exception
 */
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