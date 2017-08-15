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
        val buf = alloc.ioBuffer(src.remaining())
        buf.writeBytes(src)
        val frame = DefaultHttp2DataFrame(buf, false)

        context.writeAndFlush(frame).suspendWriteAwait()
    }

    suspend override fun flush() {
        try {
            context.flush()
        } catch (exception: IOException) {
            throw ChannelWriteException("Failed to flush netty outbound context", exception)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                context.writeAndFlush(DefaultHttp2DataFrame(true)).await()
            } catch (exception: IOException) {
                throw ChannelWriteException("Failed to send End-Of-Stream data frame to netty outbound context",exception)
            }
        }
    }
}