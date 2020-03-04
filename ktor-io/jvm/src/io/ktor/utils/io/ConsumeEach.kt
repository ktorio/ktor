package io.ktor.utils.io

import io.ktor.utils.io.bits.*
import java.nio.*

/**
 * Visitor function that is invoked for every available buffer (or chunk) of a channel.
 * The last parameter shows that the buffer is known to be the last.
 */
typealias ConsumeEachBufferVisitor = (buffer: ByteBuffer, last: Boolean) -> Boolean

/**
 * For every available bytes range invokes [visitor] function until it return false or end of stream encountered.
 * The provided buffer should be never captured outside of the visitor block otherwise resource leaks, crashes and
 * data corruptions may occur. The visitor block may be invoked multiple times, once or never.
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend inline fun ByteReadChannel.consumeEachBufferRange(visitor: ConsumeEachBufferVisitor) {
    var continueFlag: Boolean
    var lastChunkReported = false

    do {
        continueFlag = false
        read { source, start, endExclusive ->
            val nioBuffer = when {
                endExclusive > start -> source.slice(start, endExclusive - start).buffer
                else -> Memory.Empty.buffer
            }

            lastChunkReported = nioBuffer.remaining() == availableForRead && isClosedForWrite
            continueFlag = visitor(nioBuffer, lastChunkReported)

            nioBuffer.position()
        }

        if (lastChunkReported && isClosedForRead) {
            break
        }

    } while (continueFlag)
}
