package org.jetbrains.ktor.netty

import io.netty.channel.*
import org.jetbrains.ktor.cio.*
import java.io.*
import java.nio.*

internal class NettyHttp1WriteChannel(val context: ChannelHandlerContext) : WriteChannel {
    private val allocator = context.alloc()

    override suspend fun write(src: ByteBuffer) {
        try {
            while (src.hasRemaining()) {
                val buffer = allocator.ioBuffer(src.remaining())
                buffer.writeBytes(src)
                context.writeAndFlush(buffer).suspendAwait()
            }
        } catch (exception: IOException) {
            throw ChannelWriteException(exception = exception)
        }
    }

    suspend override fun flush() {
        try {
            context.flush() // TODO: does it really completes flush once it returns? seems to be it doesn't
        } catch (exception: IOException) {
            throw ChannelWriteException(exception = exception)
        }
    }

    override fun close() {
        try {
            context.flush()
        } catch (exception: IOException) {
            throw ChannelWriteException(exception = exception)
        }
    }
}

