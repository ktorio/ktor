package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.util.*
import java.util.*

internal class LastDropsCollectorHandler : SimpleChannelInboundHandler<DefaultHttpContent>(false) {
    private val collected = ArrayList<DefaultHttpContent>()
    private var completed = false
    private var transferred = false
    private var removed = false

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

    fun forgetCompleted() {
        collected.removeAll { it is LastHttpContent }
        completed = false
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext?) {
        removed = true
        super.handlerRemoved(ctx)
    }

    fun close(context: ChannelHandlerContext) {
        if (!transferred) {
            context.executeInLoop {
                try {
                    if (!removed) {
                        context.pipeline().remove(this)
                    }
                } catch (ignore: NoSuchElementException) {
                }

                for (content in collected) {
                    ReferenceCountUtil.release(content)
                }

                collected.clear()
            }
        }
    }
}