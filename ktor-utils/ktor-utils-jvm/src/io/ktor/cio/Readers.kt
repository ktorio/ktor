package io.ktor.cio

import kotlinx.coroutines.experimental.io.*
import kotlinx.io.core.*
import java.nio.*

suspend fun ByteReadChannel.toByteArray(limit: Int = Int.MAX_VALUE): ByteArray = readRemaining(limit.toLong()).readBytes()

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
