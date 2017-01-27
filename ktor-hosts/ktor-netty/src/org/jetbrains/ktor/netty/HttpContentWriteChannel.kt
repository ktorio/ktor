package org.jetbrains.ktor.netty

import io.netty.channel.*
import org.jetbrains.ktor.cio.*
import java.nio.*

internal class HttpContentWriteChannel(val context: ChannelHandlerContext) : WriteChannel {
    private val allocator = context.alloc()
    override suspend fun write(src: ByteBuffer) {
        while (src.hasRemaining()) {
            val buffer = allocator.buffer()
            buffer.writeBytes(src)
            context.write(buffer, context.voidPromise())
        }
    }

    override fun close() {
    }
}