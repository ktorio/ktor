package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.util.*
import java.util.*

internal class LastDropsCollectorHandler : SimpleChannelInboundHandler<DefaultHttpContent>(false) {
    val collected = ArrayList<DefaultHttpContent>()
    var completed = false
    var transferred = false

    override fun channelRead0(ctx: ChannelHandlerContext, msg: DefaultHttpContent) {
        collected.add(msg)
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext?) {
        completed = true
    }

    fun <T: DefaultHttpContent> transferTo(context: ChannelHandlerContext, handler: SimpleChannelInboundHandler<T>) {
        transferred = true
        context.pipeline().remove(this)

        for (content in collected) {
            handler.channelRead(context, content)
        }

        if (completed) {
            handler.channelReadComplete(context)
        }
    }

    fun close() {
        if (!transferred) {
            for (content in collected) {
                ReferenceCountUtil.release(content)
            }
            collected.clear()
        }
    }
}