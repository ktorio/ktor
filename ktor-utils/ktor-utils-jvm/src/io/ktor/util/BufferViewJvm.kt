package io.ktor.util

import kotlinx.io.core.*
import java.nio.channels.*

/**
 * Read from a NIO channel into the specified [buffer]
 * Could return `0` if the channel is non-blocking or [buffer] has no free space
 * @return number of bytes read (possibly 0) or -1 if EOF
 */
@InternalAPI
fun ReadableByteChannel.read(buffer: IoBuffer): Int {
    if (buffer.writeRemaining == 0) return 0
    var count = 0

    buffer.writeDirect(1) { bb ->
        count = read(bb)
    }

    return count
}

/**
 * Write bytes to a NIO channel from the specified [buffer]
 * Could return `0` if the channel is non-blocking or [buffer] has no free space
 * @return number of bytes written (possibly 0)
 */
@InternalAPI
fun WritableByteChannel.write(buffer: IoBuffer): Int {
    var count = 0
    buffer.readDirect { bb ->
        count = write(bb)
    }

    return count
}
