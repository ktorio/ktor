package io.ktor.compat

import kotlinx.io.core.*
import java.nio.channels.*


fun ReadableByteChannel.read(buffer: BufferView): Int {
    if (buffer.writeRemaining == 0) return 0
    var count = 0

    buffer.writeDirect(1) { bb ->
        count = read(bb)
    }

    return count
}

fun WritableByteChannel.write(buffer: BufferView): Int {
    var count = 0
    buffer.readDirect { bb ->
        count = write(bb)
    }

    return count
}