package org.jetbrains.ktor.netty

import io.netty.channel.*
import org.jetbrains.ktor.cio.*
import java.nio.*

internal class HttpContentWriteChannel(val context: ChannelHandlerContext) : WriteChannel {
    private val allocator = context.alloc()

    override suspend fun write(src: ByteBuffer) {
        while (src.hasRemaining()) {
            val buffer = allocator.buffer(src.remaining())
            buffer.writeBytes(src)
            context.writeAndFlush(buffer).suspendAwait()
        }
    }

    suspend override fun flush() {
        context.flush() // TODO: does it really completes flush once it returns? seems to be it doesn't
    }

    override fun close() {
        context.flush()
    }
}