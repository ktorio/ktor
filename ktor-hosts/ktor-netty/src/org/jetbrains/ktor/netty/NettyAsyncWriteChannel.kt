package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.util.concurrent.*
import org.jetbrains.ktor.nio.*
import java.nio.*
import java.util.concurrent.atomic.*

internal class NettyAsyncWriteChannel(val request: HttpRequest, val appResponse: NettyApplicationResponse, val context: ChannelHandlerContext) : AsyncWriteChannel {
    private val buffer = context.alloc().buffer(8192)
    private val currentHandler = AtomicReference<AsyncHandler?>()

    private val listener = GenericFutureListener<Future<Void>> { f ->
        val handler = currentHandler.get()

        try {
            f.get()
            val written = buffer.writerIndex()

            currentHandler.compareAndSet(handler, null)

            handler?.success(written)
        } catch (e: Throwable) {
            currentHandler.compareAndSet(handler, null)
            handler?.failed(e)
        }
    }

    override fun write(src: ByteBuffer, handler: AsyncHandler) {
        if (!currentHandler.compareAndSet(null, handler)) {
            throw IllegalStateException("Write operation is already in progress: wait for completion before ask new operation")
        }

        buffer.clear()
        buffer.writeBytes(src)
        val content = DefaultHttpContent(buffer.copy())

        context.executeInLoop {
            context.writeAndFlush(content).addListener(listener)
        }
    }

    override fun close() {
        appResponse.finalize()
    }

}