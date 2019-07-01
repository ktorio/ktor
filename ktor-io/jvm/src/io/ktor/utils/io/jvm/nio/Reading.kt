package io.ktor.utils.io.jvm.nio

import io.ktor.utils.io.*
import java.nio.*
import java.nio.channels.*

/**
 * Copies up to [limit] bytes from blocking NIO channel to CIO [ch]. It does suspend if no space available for writing
 * in the destination channel but may block if source NIO channel blocks.
 *
 * @return number of bytes were copied
 */
suspend fun ReadableByteChannel.copyTo(ch: ByteWriteChannel, limit: Long = Long.MAX_VALUE): Long {
    require(limit >= 0L) { "Limit shouldn't be negative: $limit" }
    if (this is SelectableChannel && !isBlocking) {
        throw IllegalArgumentException("Non-blocking channels are not supported")
    }

    var copied = 0L
    var eof = false

    val copy: (ByteBuffer) -> Unit = { bb ->
        val rem = limit - copied
        if (rem < bb.remaining()) {
            val l = bb.limit()
            bb.limit(bb.position() + rem.toInt())
            val rc = read(bb)
            if (rc == -1) eof = true
            else copied += rc
            bb.limit(l)
        } else {
            val rc = read(bb)
            if (rc == -1) eof = true
            else copied += rc
        }
    }

    val needFlush = !ch.autoFlush
    while (copied < limit && !eof) {
        ch.write(1, copy)
        if (needFlush) ch.flush()
    }

    return copied
}

/**
 * Copies up to [limit] bytes from a blocking NIO pipe to CIO [ch]. A shortcut to copyTo with
 * NIO readable channel receiver
 *
 * @return number of bytes copied
 */
suspend fun Pipe.copyTo(ch: ByteWriteChannel, limit: Long = Long.MAX_VALUE): Long = source().copyTo(ch, limit)
