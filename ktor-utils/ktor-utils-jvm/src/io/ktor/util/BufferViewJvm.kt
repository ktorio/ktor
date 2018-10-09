package io.ktor.util

import kotlinx.io.core.*
import java.nio.channels.*

@InternalAPI
fun ReadableByteChannel.read(buffer: IoBuffer): Int {
    if (buffer.writeRemaining == 0) return 0
    var count = 0

    buffer.writeDirect(1) { bb ->
        count = read(bb)
    }

    return count
}

@InternalAPI
fun WritableByteChannel.write(buffer: IoBuffer): Int {
    var count = 0
    buffer.readDirect { bb ->
        count = write(bb)
    }

    return count
}
