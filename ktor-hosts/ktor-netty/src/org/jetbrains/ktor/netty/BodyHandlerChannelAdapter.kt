package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.util.*
import org.jetbrains.ktor.nio.*
import java.nio.*
import java.util.*
import java.util.concurrent.atomic.*

internal class BodyHandlerChannelAdapter(val context: ChannelHandlerContext) : AsyncReadChannel, SimpleChannelInboundHandler<DefaultHttpContent>(false) {
    private val currentHandler = AtomicReference<AsyncHandler>()
    private var currentBuffer: ByteBuffer? = null

    private var lastContent = Collections.synchronizedList(ArrayList<DefaultHttpContent>())
    private val requested = AtomicBoolean()

    override fun channelRead0(ctx: ChannelHandlerContext, msg: DefaultHttpContent) {
        lastContent.add(msg)
        requested.set(false)

        tryToMeet()
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        requested.set(false)
    }

    override fun read(dst: ByteBuffer, handler: AsyncHandler) {
        if (!dst.hasRemaining()) {
            handler.success(0)
            return
        }
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
                if (lastContent.isNotEmpty()) {
                    if (currentHandler.compareAndSet(handler, null)) {
                        currentBuffer = null

                        meet(handler, buffer)
                    }
                }
            }
        }
    }

    tailrec
    private fun meet(handler: AsyncHandler, buffer: ByteBuffer, copiedBefore: Int = 0) {
        val content = lastContent[lastContent.lastIndex]
        val copied = content.putTo(buffer)

        if (!content.content().isReadable && content !is LastHttpContent) {
            lastContent.removeAt(lastContent.lastIndex)
            ReferenceCountUtil.release(content)
        }

        if (lastContent.isEmpty()) {
            requestNext()
        }

        val totalCopied = copiedBefore + copied
        if (!buffer.hasRemaining()) {
            handler.success(totalCopied)
        } else if (content is LastHttpContent && totalCopied == 0) {
            close()
            handler.successEnd()
        } else if (content is LastHttpContent) {
            handler.success(totalCopied)
        } else if (lastContent.isEmpty()) {
            handler.success(totalCopied)
        } else {
            meet(handler, buffer, totalCopied)
        }
    }

    fun requestNext() {
        if (requested.compareAndSet(false, true)) {
            if (lastContent.isEmpty()) {
                context.read()
            } else {
                requested.set(false)
            }
        }
    }

    override fun close() {
        for (content in lastContent) {
            ReferenceCountUtil.release(content)
        }
        lastContent.clear()

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