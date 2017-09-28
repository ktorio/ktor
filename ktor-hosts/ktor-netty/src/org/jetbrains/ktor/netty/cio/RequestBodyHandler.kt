package org.jetbrains.ktor.netty.cio

import io.netty.buffer.*
import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.io.*

internal class RequestBodyHandler(val context: ChannelHandlerContext) : ChannelInboundHandlerAdapter() {
    private val queue = Channel<Any>(Channel.UNLIMITED)

    private val job = launch(Unconfined, start = CoroutineStart.LAZY) {
        var current: ByteWriteChannel? = null
        try {
            while (true) {
                val event = queue.receiveOrNull() ?: break
                requestMoreEvents()

                if (event is ByteBufHolder) {
                    val channel = current ?: throw IllegalStateException("No current channel but received a byte buf")
                    processContent(channel, event)

                    if (event is LastHttpContent) {
                        current.close()
                        current = null
                    }
                } else if (event is ByteBuf) {
                    val channel = current ?: throw IllegalStateException("No current channel but received a byte buf")
                    processContent(channel, event)
                } else if (event is ByteWriteChannel) {
                    current?.close()
                    current = event
                }
            }
        } catch (t: Throwable) {
            queue.close(t)
            current?.close(t)
        } finally {
            current?.close()
            queue.close()
            consumeAndReleaseQueue()
        }
    }

    fun newChannel(): ByteReadChannel {
        val bc = ByteChannel(autoFlush = true)
        if (!queue.offer(bc)) throw IllegalStateException("Unable to start request processing: failed to offer byte channel to the queue")
        return bc
    }

    fun close() {
        queue.close()
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any?) {
        if (msg is HttpContent) {
            handleBytesRead(msg)
        } else if (msg is ByteBuf) {
            handleBytesRead(msg)
        } else {
            ctx.fireChannelRead(msg)
        }
    }

    private suspend fun processContent(current: ByteWriteChannel, event: ByteBufHolder) {
        try {
            val buf = event.content()
            copy(buf, current)
        } finally {
            event.release()
        }
    }

    private suspend fun processContent(current: ByteWriteChannel, buf: ByteBuf) {
        try {
            copy(buf, current)
        } finally {
            buf.release()
        }
    }

    private fun requestMoreEvents() {
        context.read()
    }

    private fun consumeAndReleaseQueue() {
        while (!queue.isEmpty) {
            val e = try { queue.poll() } catch (t: Throwable) { null } ?: break

            when (e) {
                is ByteChannel -> e.close()
                is ReferenceCounted -> e.release()
                else -> {}
            }
        }
    }

    private suspend fun copy(buf: ByteBuf, dst: ByteWriteChannel) {
        val length = buf.readableBytes()
        if (length > 0) {
            val buffer = buf.internalNioBuffer(buf.readerIndex(), length)
            dst.writeFully(buffer)
        }
    }

    private fun handleBytesRead(content: ReferenceCounted) {
        if (!queue.offer(content)) {
            content.release()
            throw IllegalStateException("Unable to process received buffer: queue offer failed")
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable) {
        queue.close(cause)
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext?) {
        if (queue.close() && job.isCompleted) {
            consumeAndReleaseQueue()
        }
    }

    override fun handlerAdded(ctx: ChannelHandlerContext?) {
        job.start()
    }
}