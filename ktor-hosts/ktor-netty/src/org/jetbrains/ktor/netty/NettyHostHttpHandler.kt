package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.netty.handler.codec.http.HttpVersion.*
import io.netty.util.*

@ChannelHandler.Sharable
internal class NettyHostHttp1Handler(private val host: NettyApplicationHost) : SimpleChannelInboundHandler<HttpObject>(false) {
    override fun channelRead0(context: ChannelHandlerContext, message: HttpObject) {
        when (message) {
            is HttpRequest -> {
                if (HttpUtil.is100ContinueExpected(message)) {
                    context.write(DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
                }

                context.channel().config().isAutoRead = false
                val httpContentQueue = HttpContentQueue(context)
                context.pipeline().addLast(httpContentQueue)
                context.pipeline().addLast(host.callEventGroup, NettyApplicationCallHandler(host))

                if (message is HttpContent) {
                    httpContentQueue.queue.push(message, message is LastHttpContent)
                }

                ReferenceCountUtil.retain(message)
                val call = NettyApplicationCall(host.application, context, message, httpContentQueue.queue)
                context.fireChannelRead(call)
            }
            else -> context.fireChannelRead(message)
        }
    }
}

