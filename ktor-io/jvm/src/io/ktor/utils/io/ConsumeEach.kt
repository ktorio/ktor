package io.ktor.utils.io

import java.nio.*

typealias ConsumeEachBufferVisitor = (buffer: ByteBuffer, last: Boolean) -> Boolean

/**
 * For every available bytes range invokes [visitor] function until it return false or end of stream encountered
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend inline fun ByteReadChannel.consumeEachBufferRange(visitor: ConsumeEachBufferVisitor) {
    var continueFlag = false

    while (continueFlag) {
        read { source, start, endExclusive ->
            val nioBuffer = source.slice(start, endExclusive - start).buffer
            continueFlag = visitor(nioBuffer, nioBuffer.remaining() == availableForRead && isClosedForWrite)
            nioBuffer.position()
        }
    }
}
