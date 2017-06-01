package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.util.*
import org.jetbrains.ktor.pipeline.*

class NettyContentQueue(val context: ChannelHandlerContext) : SuspendQueue<HttpContent>(2) {
    fun dispose(t: Throwable? = null) {
        cancel(t)
        clear { it.release() }
    }

    override fun onPull(element: HttpContent?) {
        context.read()
    }
}

internal class RawContentQueue(val context: ChannelHandlerContext) : ChannelInboundHandlerAdapter() {
    val queue = NettyContentQueue(context)
    private var closed = false

    fun close() {
        closed = true
        queue.clear { it.release() }
    }

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        if (!closed && msg is HttpContent)
            queue.push(msg, false)
        else if (msg is ReferenceCounted)
            msg.release()
    }
}

internal open class HttpContentQueue(val context: ChannelHandlerContext) : SimpleChannelInboundHandler<HttpContent>(false) {
    private val _queuesStack = ArrayList<NettyContentQueue>(2)

    fun createNew(): NettyContentQueue {
        val q = NettyContentQueue(context)
        _queuesStack.add(q)
        return q
    }

    fun pop(): NettyContentQueue {
        return _queuesStack.removeAt(_queuesStack.lastIndex)
    }

    fun popAndForEach(block: (NettyContentQueue) -> Unit) {
        val qq = _queuesStack.toList()
        _queuesStack.clear()
        for (q in qq) {
            block(q)
            q.dispose()
        }
    }

    override fun channelRead0(context: ChannelHandlerContext, msg: HttpContent) {
        val last = msg is LastHttpContent
        val q = _queuesStack.firstOrNull() ?: throw IllegalStateException("No stacked queue")

        q.push(msg, last)
        if (last) {
            _queuesStack.remove(q)
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        close(null)
        super.channelInactive(ctx)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        close(cause)
        super.exceptionCaught(ctx, cause)
    }

    private fun close(cause: Throwable?) {
        while (_queuesStack.isNotEmpty()) {
            pop().apply {
                dispose(cause)
            }
        }
    }
}