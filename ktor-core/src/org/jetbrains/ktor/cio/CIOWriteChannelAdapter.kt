package org.jetbrains.ktor.cio

import kotlinx.coroutines.experimental.io.*
import org.jetbrains.ktor.cio.*
import java.nio.ByteBuffer

class CIOWriteChannelAdapter(val output: ByteWriteChannel, val suppressClose: Boolean = false) : WriteChannel {
    suspend override fun write(src: ByteBuffer) {
        output.writeFully(src)
    }

    suspend override fun flush() {
        output.flush()
    }

    override fun close() {
        if (suppressClose) output.flush()
        else output.close()
    }
}