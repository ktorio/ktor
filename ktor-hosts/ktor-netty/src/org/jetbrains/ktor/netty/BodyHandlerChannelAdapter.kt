package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import org.jetbrains.ktor.nio.*
import java.nio.*
import java.util.concurrent.atomic.*

internal class BodyHandlerChannelAdapter(val context: ChannelHandlerContext) : AsyncReadChannel, SimpleChannelInboundHandler<DefaultHttpContent>() {
    private val currentHandler = AtomicReference<AsyncHandler>()
    private var currentBuffer: ByteBuffer? = null
    @Volatile
    private var lastContent: DefaultHttpContent? = null
    private val requested = AtomicBoolean()

    override fun channelRead0(ctx: ChannelHandlerContext, msg: DefaultHttpContent) {
        require(lastContent == null) { throw IllegalStateException("lastContent should be consumed before we receive the next event") }
        lastContent = msg
        requested.set(false)

        tryToMeet()
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        requested.set(false)
        close()
        ctx.flush()
    }

    override fun read(dst: ByteBuffer, handler: AsyncHandler) {
        if (!currentHandler.compareAndSet(null, handler)) { // this is not guaranteed check but most likely works almost all the time
            throw IllegalStateException("Read operation is already in progress")
        }
        currentBuffer = dst
        requestNext()

        tryToMeet()
    }

    private fun tryToMeet() {
        currentHandler.get()?.let { handler ->
            currentBuffer?.let { buffer ->
                lastContent?.let { content ->
                    if (currentHandler.compareAndSet(handler, null)) {
                        currentBuffer = null

                        meet(handler, buffer, content)
                    }
                }
            }
        }
    }

    private fun meet(handler: AsyncHandler, buffer: ByteBuffer, content: DefaultHttpContent) {
        val copied = if (content.content().isReadable) {
            content.putTo(buffer)
        } else 0

        val hasRemaining = content.content().isReadable && content.content().readableBytes() > 0
        val isLast = content is LastHttpContent

        if (!isLast && !hasRemaining) {
            lastContent = null
            requestNext()
        }

        if (isLast && !hasRemaining && copied == 0) {
            handler.successEnd()
        } else {
            handler.success(copied)
        }
    }

    private fun requestNext() {
        if (requested.compareAndSet(false, true)) {
            context.read()
        }
    }

    override fun close() {
        currentHandler.getAndSet(null)?.let { handler ->
            currentBuffer = null
            handler.successEnd()
        }
    }

    private fun DefaultHttpContent.putTo(buffer: ByteBuffer): Int {
        val size = Math.min(buffer.remaining(), content().readableBytes())
        val oldLimit = buffer.limit()
        buffer.limit(buffer.position() + size)

        content().readBytes(buffer)

        buffer.limit(oldLimit)

        return size
    }
}