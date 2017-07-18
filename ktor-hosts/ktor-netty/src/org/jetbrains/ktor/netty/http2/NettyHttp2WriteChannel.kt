package org.jetbrains.ktor.netty.http2

import io.netty.buffer.*
import io.netty.channel.*
import io.netty.handler.codec.http2.*
import io.netty.util.concurrent.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.netty.*
import java.nio.*
import java.nio.channels.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.experimental.*

internal class NettyHttp2WriteChannel(val context: ChannelHandlerContext) : WriteChannel {
    private val closed = AtomicBoolean()
    private val alloc = context.alloc()!!

    suspend override fun write(src: ByteBuffer) {
        val buf = alloc.ioBuffer(src.remaining())
        buf.writeBytes(src)
        val frame = DefaultHttp2DataFrame(buf, false)

        context.writeAndFlush(frame).suspendAwait()
    }

    suspend override fun flush() {
        context.flush()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            context.writeAndFlush(DefaultHttp2DataFrame(true)).await()
        }
    }
}