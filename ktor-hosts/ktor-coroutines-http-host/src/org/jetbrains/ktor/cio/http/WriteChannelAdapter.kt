package org.jetbrains.ktor.cio.http

import kotlinx.coroutines.experimental.io.*
import org.jetbrains.ktor.cio.*
import java.nio.ByteBuffer

internal class WriteChannelAdapter(val output: ByteWriteChannel) : WriteChannel {
    suspend override fun write(src: ByteBuffer) {
        output.writeFully(src)
    }

    suspend override fun flush() {
        output.flush()
    }

    override fun close() {
        output.close()
    }
}