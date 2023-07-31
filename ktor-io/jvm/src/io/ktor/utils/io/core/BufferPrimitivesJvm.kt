package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import java.nio.*

/**
 * Read buffer's content to the [destination] buffer moving its position.
 */
@Suppress("DEPRECATION")
public fun Buffer.readFully(destination: ByteBuffer) {
    val size = destination.remaining()
    readExact(size, "buffer content") { memory, offset ->
        memory.copyTo(destination, offset)
    }
}

/**
 * Write [source] buffer content moving its position.
 */
@Suppress("DEPRECATION")
public fun Buffer.writeFully(source: ByteBuffer) {
    val size = source.remaining()
    writeExact(size, "buffer content") { memory, offset ->
        source.copyTo(memory, offset)
    }
}
