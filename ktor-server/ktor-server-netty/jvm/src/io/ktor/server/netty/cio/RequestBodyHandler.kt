/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.netty.cio

import io.ktor.utils.io.*
import io.netty.buffer.*
import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.*

internal class RequestBodyHandler(
    val context: ChannelHandlerContext,
    private val requestQueue: NettyRequestQueue
) : ChannelInboundHandlerAdapter(), CoroutineScope {
    private val handlerJob = CompletableDeferred<Nothing>()

    private val queue = Channel<Any>(Channel.UNLIMITED)

    private object Upgrade

    override val coroutineContext: CoroutineContext get() = handlerJob

    private val job = launch(context.executor().asCoroutineDispatcher(), start = CoroutineStart.LAZY) {
        var current: ByteWriteChannel? = null
        var upgraded = false

        try {
            while (true) {
                @OptIn(ExperimentalCoroutinesApi::class)
                val event = queue.poll()
                    ?: run { current?.flush(); queue.receiveOrNull() }
                    ?: break

                if (event is ByteBufHolder) {
                    val channel = current ?: throw IllegalStateException("No current channel but received a byte buf")
                    processContent(channel, event)

                    if (!upgraded && event is LastHttpContent) {
                        current.close()
                        current = null
                    }
                } else if (event is ByteBuf) {
                    val channel = current ?: throw IllegalStateException("No current channel but received a byte buf")
                    processContent(channel, event)
                } else if (event is ByteWriteChannel) {
                    current?.close()
                    current = event
                } else if (event is Upgrade) {
                    upgraded = true
                }
            }
        } catch (t: Throwable) {
            queue.close(t)
            current?.close(t)
        } finally {
            current?.close()
            queue.close()
            consumeAndReleaseQueue()
            requestQueue.cancel()
        }
    }

    public fun upgrade(): ByteReadChannel {
        tryOfferChannelOrToken(Upgrade)
        return newChannel()
    }

    public fun newChannel(): ByteReadChannel {
        val bc = ByteChannel()
        tryOfferChannelOrToken(bc)
        return bc
    }

    private fun tryOfferChannelOrToken(token: Any) {
        try {
            if (!queue.offer(token)) {
                throw IllegalStateException(
                    "Unable to start request processing: failed to offer $token to the HTTP pipeline queue"
                )
            }
        } catch (closedCause: ClosedSendChannelException) {
            throw CancellationException("HTTP pipeline has been terminated.", closedCause)
        }
    }

    public fun close() {
        queue.close()
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any?) {
        if (msg is ByteBufHolder) {
            handleBytesRead(msg)
        } else if (msg is ByteBuf) {
            handleBytesRead(msg)
        } else {
            ctx.fireChannelRead(msg)
        }
    }

    private suspend fun processContent(current: ByteWriteChannel, event: ByteBufHolder) {
        try {
            requestMoreEvents()
            val buf = event.content()
            copy(buf, current)
        } finally {
            event.release()
        }
    }

    private suspend fun processContent(current: ByteWriteChannel, buf: ByteBuf) {
        try {
            requestMoreEvents()
            copy(buf, current)
        } finally {
            buf.release()
        }
    }

    private fun requestMoreEvents() {
        if (requestQueue.canRequestMoreEvents()) {
            context.read()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun consumeAndReleaseQueue() {
        while (!queue.isEmpty) {
            val e = try {
                queue.poll()
            } catch (t: Throwable) {
                null
            } ?: break

            when (e) {
                is ByteChannel -> e.close()
                is ReferenceCounted -> e.release()
                else -> {
                }
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

    @Suppress("OverridingDeprecatedMember")
    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable) {
        handlerJob.completeExceptionally(cause)
        queue.close(cause)
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext?) {
        if (queue.close() && job.isCompleted) {
            consumeAndReleaseQueue()
            handlerJob.cancel()
        }
    }

    override fun handlerAdded(ctx: ChannelHandlerContext?) {
        job.start()
    }
}
