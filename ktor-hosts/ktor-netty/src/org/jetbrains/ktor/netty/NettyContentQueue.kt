package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import org.jetbrains.ktor.pipeline.*

class NettyContentQueue(val context: ChannelHandlerContext) : SuspendQueue<HttpContent>(2) {
    fun dispose() {
        clear { it.release() }
    }

    override fun onPull(element: HttpContent) {
        context.read()
    }

    /*
    companion object {
        private val queueRecycler = object : Recycler<NettyContentQueue>() {
            override fun newObject(handle: Handle<NettyContentQueue>) = NettyContentQueue(handle)
        }

        operator fun invoke(): NettyContentQueue {
            return queueRecycler.get()
        }
    }
*/
}

internal class HttpContentQueue(val context: ChannelHandlerContext) : SimpleChannelInboundHandler<HttpContent>(false) {
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