package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.util.concurrent.*
import org.jetbrains.ktor.nio.*
import java.nio.*
import java.util.concurrent.atomic.*

class NettyAsyncWriteChannel(val request: HttpRequest, val context: ChannelHandlerContext) : AsyncWriteChannel {
    private val buffer = context.alloc().buffer(8192)
    private val closed = AtomicBoolean(false)
    private val currentHandler = AtomicReference<AsyncHandler?>()
    private var currentBuffer: ByteBuffer? = null

    private val listener = GenericFutureListener<Future<Void>> { f ->
        try {
            f.get()
            val written = buffer.readerIndex()

            currentBuffer?.positionForward(written)
            currentHandler.get()?.success(written)
        } catch (e: Throwable) {
            currentHandler.get()?.failed(e)
        } finally {
            currentHandler.set(null)
            currentBuffer = null
        }
    }

    override fun write(src: ByteBuffer, handler: AsyncHandler) {
        if (!currentHandler.compareAndSet(null, handler)) {
            throw IllegalStateException("Write operation is already in progress: wait for completion before ask new operation")
        }

        currentBuffer = src
        buffer.clear()
        buffer.writeBytes(src)
        context.writeAndFlush(DefaultHttpContent(buffer.copy())).addListener(listener)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            context.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).scheduleClose()
        }
    }

    private fun ChannelFuture.scheduleClose() {
        if (noKeepAlive()) {
            addListener(ChannelFutureListener.CLOSE)
        }
    }

    private fun noKeepAlive() = !HttpHeaders.isKeepAlive(request)

    private fun ByteBuffer.positionForward(delta: Int) {
        require(delta >= 0) { "delta should be positive or zero" }
        require(delta <= remaining()) { "You couldn't just over the limit: delta = $delta, remaining: ${remaining()}" }

        position(position() + delta)
    }
}