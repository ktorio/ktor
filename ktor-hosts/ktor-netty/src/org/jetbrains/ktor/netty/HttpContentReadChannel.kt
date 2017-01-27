package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import org.jetbrains.ktor.cio.*
import java.nio.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

internal class HttpContentReadChannel(val context: ChannelHandlerContext) : ReadChannel, SimpleChannelInboundHandler<DefaultHttpContent>(false) {
    private var currentContinuation: Continuation<Int>? = null
    private var currentBuffer: ByteBuffer? = null
    private var currentMessage: DefaultHttpContent? = null

    override fun channelRead0(ctx: ChannelHandlerContext, msg: DefaultHttpContent) {
        check(currentMessage == null)
        currentMessage = msg
        val continuation = currentContinuation
        currentContinuation = null
        if (continuation == null)
            return // nobody reads yet, so just remember content

        when (msg) {
            is DefaultLastHttpContent -> { // end of stream
                continuation.resume(-1)
            }
            is DefaultHttpContent -> {
                val count = msg.putTo(currentBuffer!!)
                if (!msg.content().isReadable) { // End of input message, request another one
                    msg.release()
                    currentMessage = null
                    context.read() // request more content, can come synchronously reentrantly
                } else {
                    currentMessage = msg
                }
                continuation.resume(count) // resume ReadChannel.read
            }
        }
    }

    override suspend fun read(dst: ByteBuffer): Int {
        if (!dst.hasRemaining())
            return 0

        do {
            val msg = currentMessage
            if (msg != null) {
                if (msg is DefaultLastHttpContent) {
                    msg.release()
                    return -1
                }
                val count = msg.putTo(dst)
                if (msg.content().isReadable) {
                    // msg has some more data
                    return count
                }
                msg.release()
                currentMessage = null
            }
            // no message, or no data in last message, request some more
            context.read() // can complete sync
        } while (currentMessage != null)

        return suspendCoroutineOrReturn {
            check(currentContinuation == null)
            currentContinuation = it
            currentBuffer = dst
            SUSPENDED_MARKER
        }
    }

    override fun close() {
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