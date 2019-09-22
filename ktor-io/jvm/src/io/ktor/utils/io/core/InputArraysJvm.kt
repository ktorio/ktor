package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.errors.*
import java.nio.*

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readFully(dst: ByteBuffer, length: Int = dst.remaining()) {
    if (readAvailable(dst, length) < length) {
        prematureEndOfStream(length)
    }
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readAvailable(dst: ByteBuffer, length: Int = dst.remaining()): Int {
    var bytesCopied = 0

    takeWhile { buffer ->
        val originalLimit = dst.limit()
        dst.limit(minOf(originalLimit, dst.position() + buffer.readRemaining))
        val size = dst.remaining()
        buffer.memory.copyTo(dst, buffer.readPosition)
        dst.limit(originalLimit)
        bytesCopied += size

        dst.hasRemaining() && bytesCopied < length
    }

    return bytesCopied
}
