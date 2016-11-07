package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.util.*
import io.netty.util.concurrent.*
import org.jetbrains.ktor.nio.*
import java.io.*
import java.nio.*
import java.util.concurrent.atomic.*

internal class NettyWriteChannel(val request: HttpRequest, val appResponse: NettyApplicationResponse, val context: ChannelHandlerContext) : WriteChannel {
    private val buffer = context.alloc().ioBuffer(8192)
    private val currentHandler = AtomicReference<AsyncHandler?>()
    private val released = AtomicBoolean()

    private val listener = GenericFutureListener<Future<Void>> { f ->
        val handler = currentHandler.get() ?: throw IllegalStateException("No write operation is in progress")

        try {
            f.get()
            val written = buffer.writerIndex()

            currentHandler.compareAndSet(handler, null)

            handler.success(written)
        } catch (e: Throwable) {
            currentHandler.compareAndSet(handler, null)
            handler.failed(e)
        }
    }

    override fun write(src: ByteBuffer, handler: AsyncHandler) {
        if (!currentHandler.compareAndSet(null, handler)) {
            throw IllegalStateException("Write operation is already in progress: wait for completion before ask new operation")
        }

        buffer.clear()
        buffer.writeBytes(src)
        val content = DefaultHttpContent(buffer)

        context.executeInLoop {
            ReferenceCountUtil.retain(buffer)
            context.writeAndFlush(content).addListener(listener)
        }
    }

    override fun flush(handler: AsyncHandler) {
        if (!currentHandler.compareAndSet(null, handler)) {
            throw IllegalStateException("Write operation is already in progress: wait for completion before ask new operation")
        }

        context.executeInLoop {
            if (!released.get()) {
                val flushFailure = try {
                    context.flush()
                    null
                } catch (e: Throwable) {
                    e
                }

                currentHandler.set(null)
                if (flushFailure == null) {
                    handler.successEnd()
                } else {
                    handler.failed(flushFailure)
                }
            } else {
                currentHandler.set(null)
                handler.failed(EOFException("Context already released"))
            }
        }
    }

    override fun requestFlush() {
        context.executeInLoop {
            if (!released.get()) {
                context.flush()
            }
        }
    }

    override fun close() {
        try {
            currentHandler.getAndSet(null)?.failed(EOFException("Channel closed"))
        } finally {
            if (released.compareAndSet(false, true)) {
                ReferenceCountUtil.release(buffer)
                appResponse.finalize()
            }
        }
    }

}