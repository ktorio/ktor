package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.util.*
import org.jetbrains.ktor.pipeline.*
import java.nio.channels.*

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

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        queue.push(LastHttpContent.EMPTY_LAST_CONTENT, true)
        closed = true
        super.channelInactive(ctx)
    }
}

internal open class HttpContentQueue(val context: ChannelHandlerContext) : ChannelInboundHandlerAdapter() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is HttpContent) {
            handleRequest(ctx, msg)
        } else {
            ctx.fireChannelRead(msg)
        }
    }

    private val _queuesStack = ArrayList<NettyContentQueue>(2)
    @Volatile
    private var closeCause: Throwable? = null

    fun createNew(): NettyContentQueue {
        closeCause?.let { throw it }

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

    fun handleRequest(context: ChannelHandlerContext, call: HttpContent) {
        val last = call is LastHttpContent
        closeCause?.let { t -> call.release(); throw t }
        val q = _queuesStack.firstOrNull() ?: run { call.release(); throw IllegalStateException("No stacked queue") }

        q.push(call, last)
        if (last) {
            _queuesStack.remove(q)
        }
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext?) {
        close(null)
        super.handlerRemoved(ctx)
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
        closeCause = closeCause ?: cause ?: ClosedChannelException()

        while (_queuesStack.isNotEmpty()) {
            pop().dispose(cause)
        }
    }
}