package io.ktor.utils.io.jvm.nio

import io.ktor.utils.io.*
import java.nio.*
import java.nio.channels.*

/**
 * Copy up to [limit] bytes to blocking NIO [channel]. Copying to non-blocking channel requires selection and
 * not supported. It does suspend if no data available in byte channel but may block if destination NIO channel blocks.
 *
 * @return number of bytes copied
 */
suspend fun ByteReadChannel.copyTo(channel: WritableByteChannel, limit: Long = Long.MAX_VALUE): Long {
    require(limit >= 0L) { "Limit shouldn't be negative: $limit" }
    if (channel is SelectableChannel && !channel.isBlocking) {
        throw IllegalArgumentException("Non-blocking channels are not supported")
    }

    if (isClosedForRead) return 0

    var copied = 0L
    val copy = { bb: ByteBuffer ->
        val rem = limit - copied

        if (rem < bb.remaining()) {
            val l = bb.limit()
            bb.limit(bb.position() + rem.toInt())

            while (bb.hasRemaining()) {
                channel.write(bb)
            }

            bb.limit(l)
            copied += rem
        } else {
            var written = 0L
            while (bb.hasRemaining()) {
                written += channel.write(bb)
            }

            copied += written
        }
    }

    while (copied < limit) {
        read(min = 0, consumer = copy)
        if (isClosedForRead) break
    }

    return copied
}

/**
 * Copy up to [limit] bytes to blocking [pipe]. A shortcut to copyTo function with NIO channel destination
 *
 * @return number of bytes were copied
 */
suspend fun ByteReadChannel.copyTo(pipe: Pipe, limit: Long = Long.MAX_VALUE): Long = copyTo(pipe.sink(), limit)
