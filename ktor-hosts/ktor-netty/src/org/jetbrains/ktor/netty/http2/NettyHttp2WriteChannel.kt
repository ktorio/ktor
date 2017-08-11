package org.jetbrains.ktor.netty.http2

import io.netty.channel.*
import io.netty.handler.codec.http2.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.netty.*
import java.io.*
import java.nio.*
import java.util.concurrent.atomic.*

internal class NettyHttp2WriteChannel(val context: ChannelHandlerContext) : WriteChannel {
    private val closed = AtomicBoolean()
    private val alloc = context.alloc()!!

    suspend override fun write(src: ByteBuffer) {
        try {
            val buf = alloc.ioBuffer(src.remaining())
            buf.writeBytes(src)
            val frame = DefaultHttp2DataFrame(buf, false)

            context.writeAndFlush(frame).suspendAwait()
        } catch (exception: IOException) {
            throw ChannelWriteException(exception = exception)
        }

    }

    suspend override fun flush() {
        try {
            context.flush()
        } catch (exception: IOException) {
            throw ChannelWriteException(exception = exception)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                context.writeAndFlush(DefaultHttp2DataFrame(true)).await()
            } catch (exception: IOException) {
                throw ChannelWriteException(exception = exception)
            }
        }
    }
}