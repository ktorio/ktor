package io.ktor.util.cio

import io.ktor.util.*
import kotlinx.coroutines.io.*
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
@InternalAPI
suspend inline fun ByteReadChannel.pass(buffer: ByteBuffer, block: (ByteBuffer) -> Unit) {
    while (!isClosedForRead) {
        buffer.clear()
        readAvailable(buffer)

        buffer.flip()
        block(buffer)
    }
}

/**
 * Read data chunks from [ByteReadChannel] using buffer
 */
@Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
@InternalAPI
suspend fun ByteReadChannel.pass(buffer: ByteBuffer, block: suspend (ByteBuffer) -> Unit) {
    pass(buffer) {
        block(it)
    }
}

/**
 * Executes [block] on [ByteWriteChannel] and close it down correctly whether an exception
 */
@Deprecated("", level = DeprecationLevel.HIDDEN)
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

/**
 * Executes [block] on [ByteWriteChannel] and close it down correctly whether an exception
 */
inline fun ByteWriteChannel.use(block: ByteWriteChannel.() -> Unit) {
    try {
        block()
    } catch (cause: Throwable) {
        close(cause)
        throw cause
    } finally {
        close()
    }
}
