package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.util.*
import kotlinx.coroutines.experimental.future.*
import org.jetbrains.ktor.application.*

@ChannelHandler.Sharable
class NettyHostHttp1Handler(private val host: NettyApplicationHost) : SimpleChannelInboundHandler<Any>(false) {

    override fun channelRead0(context: ChannelHandlerContext, message: Any) {
        when (message) {
            is HttpRequest -> {
                /*
                    if (HttpUtil.is100ContinueExpected(req)) {
                        ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
                    }
                */
                context.channel().config().isAutoRead = false
                val httpContentQueue = HttpContentQueue(context)
                context.pipeline().addLast(httpContentQueue)

                if (message is HttpContent) {
                    httpContentQueue.queue.push(message, message is LastHttpContent)
                }

                ReferenceCountUtil.retain(message)
                val call = NettyApplicationCall(host.application, context, message, httpContentQueue.queue)
                executeCall(call)
            }
            else -> context.fireChannelRead(message)
        }
    }

    private fun executeCall(call: ApplicationCall) {
        future(host.callDispatcher) {
            host.pipeline.execute(call)
        }
    }
}

