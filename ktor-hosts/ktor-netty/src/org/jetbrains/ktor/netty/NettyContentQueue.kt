package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.util.*
import org.jetbrains.ktor.pipeline.*

class NettyContentQueue(val context: ChannelHandlerContext) : SuspendQueue<HttpContent>(2) {
    fun dispose() {
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
    private var _queue: NettyContentQueue? = null

    val queue get() = _queue ?: NettyContentQueue(context).also { _queue = it }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpContent) {
        queue.push(msg, msg is LastHttpContent)
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        close()
        super.channelInactive(ctx)
    }

    private fun close() {
        _queue?.apply {
            dispose()
            _queue = null
        }
    }
}