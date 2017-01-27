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
    private var endOfContent = false

    override fun channelRead0(ctx: ChannelHandlerContext, msg: DefaultHttpContent) {
        check(currentMessage == null)
        currentMessage = msg
        val continuation = currentContinuation
        currentContinuation = null
        if (continuation == null)
            return // nobody reads yet, so just remember content

        val count = msg.putTo(currentBuffer!!)
        if (!msg.content().isReadable) { // End of input message, request another one
            msg.release()
            if (msg is DefaultLastHttpContent) {
                // end of content, no more content expected
                endOfContent = true
            }
            currentMessage = null
            if (!endOfContent)
                context.read() // request more content, can come synchronously reentrantly
        } else {
            currentMessage = msg
        }
        if (count > 0)
            continuation.resume(count) // resume ReadChannel.read
        else
            continuation.resume(-1) // resume ReadChannel.read and signal EOF

    }

    override suspend fun read(dst: ByteBuffer): Int {
        var totalCount = 0
        do {
            if (!dst.hasRemaining())
                return totalCount // some message may be pending, but no place to in dst buffer

            val msg = currentMessage
            currentMessage = null

            if (msg != null) {
                // there is message available and, there is place in output buffer
                val count = msg.putTo(dst)
                totalCount += count

                if (msg.content().isReadable) {
                    // msg has some more data, but no place in dst
                    return totalCount
                }

                // okay, message is done, clean it
                if (msg is DefaultLastHttpContent) {
                    // stream finished, no more messages expected
                    endOfContent = true
                }
                msg.release()
            }
            if (endOfContent) {
                if (totalCount > 0)
                    return totalCount
                else
                    return -1 // EOF
            }

            // request more data, it can complete synchronously so continue loop
            context.read()
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