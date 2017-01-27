package org.jetbrains.ktor.netty

import io.netty.channel.*
import org.jetbrains.ktor.cio.*
import java.nio.*

internal class HttpContentWriteChannel(val context: ChannelHandlerContext) : WriteChannel {
    private val byteBuf = context.alloc().buffer()
    override suspend fun write(src: ByteBuffer) {
        while (src.hasRemaining()) {
            byteBuf.clear()
            byteBuf.writeBytes(src)
            context.write(byteBuf, context.voidPromise())
        }
    }

    override fun close() {
        context.flush()
    }
}