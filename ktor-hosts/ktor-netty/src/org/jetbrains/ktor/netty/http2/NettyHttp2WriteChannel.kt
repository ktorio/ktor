package org.jetbrains.ktor.netty.http2

import io.netty.buffer.*
import io.netty.channel.*
import io.netty.handler.codec.http2.*
import io.netty.util.concurrent.*
import org.jetbrains.ktor.netty.*
import org.jetbrains.ktor.nio.*
import java.io.*
import java.nio.*
import java.util.concurrent.atomic.*

internal class NettyHttp2WriteChannel(val context: ChannelHandlerContext) : WriteChannel {
    @Volatile
    private var lastPromise: ChannelPromise? = null

    private val currentHandler = AtomicReference<AsyncHandler?>()
    private var currentBuffer: ByteBuffer? = null

    private val closed = AtomicBoolean()

    private val writeFutureListener = GenericFutureListener<Future<Void>> { f ->
        try {
            f.get()

            val buffer = currentBuffer
            currentBuffer = null
            val handler = currentHandler.getAndSet(null)

            if (handler != null && buffer != null) {
                val remaining = buffer.remaining()

                buffer.position(buffer.limit())
                handler.success(remaining)
            }
        } catch (t: Throwable) {
            val buffer = currentBuffer
            currentBuffer = null
            val handler = currentHandler.getAndSet(null)

            if (handler != null && buffer != null) {
                handler.failed(t)
            }
        }
    }

    override fun requestFlush() {
        // we don't need to do anything
    }

    override fun flush(handler: AsyncHandler) {
        lastPromise?.addListener { f ->
            try {
                f.get()
                handler.successEnd()
            } catch (t: Throwable) {
                handler.failed(t)
            }
        } ?: run { handler.successEnd() }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            fun sendEnd() {
                context.executeInLoop {
                    context.writeAndFlush(DefaultHttp2DataFrame(true))
                }
            }

            lastPromise?.addListener { sendEnd() } ?: run { sendEnd() }
        }
    }

    override fun write(src: ByteBuffer, handler: AsyncHandler) {
        if (!currentHandler.compareAndSet(null, handler)) {
            throw IllegalStateException("write operation is already in progress")
        }
        if (closed.get()) {
            currentHandler.set(null)
            handler.failed(IOException("Channel closed"))
            return
        }
        currentBuffer = src

        context.executeInLoop(writeImpl)
    }

    private val writeImpl = Runnable {
        val data = Unpooled.wrappedBuffer(currentBuffer!!)

        val promise = context.channel().newPromise()
        lastPromise = promise

        context.writeAndFlush(DefaultHttp2DataFrame(data, false), promise)

        promise.addListener(writeFutureListener)
    }
}