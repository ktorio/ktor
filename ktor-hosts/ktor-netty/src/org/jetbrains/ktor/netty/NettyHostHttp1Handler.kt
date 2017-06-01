package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.netty.handler.codec.http.HttpVersion.*
import io.netty.util.*

@ChannelHandler.Sharable
internal class NettyHostHttp1Handler(private val host: NettyApplicationHost) : SimpleChannelInboundHandler<HttpRequest>(false) {
    override fun channelRead0(context: ChannelHandlerContext, message: HttpRequest) {
        if (HttpUtil.is100ContinueExpected(message)) {
            context.write(DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
        }

        context.channel().config().isAutoRead = false
        val httpContentQueue = context.pipeline().get(HttpContentQueue::class.java)
        val queue = httpContentQueue.createNew()

        if (message is HttpContent) {
            queue.push(message, message is LastHttpContent)
        }

        val call = NettyApplicationCall(host.application, context, message, queue)
        context.channel().attr(ResponseQueueKey).get().started(call)

        context.fireChannelRead(call)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        val httpContentQueue = HttpContentQueue(ctx)
        ctx.pipeline().apply {
            addLast(httpContentQueue)
            addLast(host.callEventGroup, NettyApplicationCallHandler(host))
        }

        ctx.channel().attr(ResponseQueueKey).set(NettyResponseQueue(ctx))

        super.channelActive(ctx)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        ctx.pipeline().apply {
            remove(HttpContentQueue::class.java)
            remove(NettyApplicationCallHandler::class.java)
        }

        ctx.channel().attr(ResponseQueueKey).getAndSet(null)?.cancel()

        super.channelInactive(ctx)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        ctx.close()
    }

    companion object {
        internal val ResponseQueueKey = AttributeKey.newInstance<NettyResponseQueue>("NettyResponseQueue")
    }
}

