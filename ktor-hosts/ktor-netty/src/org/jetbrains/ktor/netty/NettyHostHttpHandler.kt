package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.future.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*

@ChannelHandler.Sharable
class NettyHostHttp1Handler(private val host: NettyApplicationHost, private val hostPipeline: HostPipeline) : SimpleChannelInboundHandler<Any>(false) {

    override fun channelRead0(context: ChannelHandlerContext, message: Any) {
        when (message) {
            is HttpRequest -> {
                context.channel().config().isAutoRead = false
                val httpContentQueue = HttpContentQueue(context)
                context.pipeline().addLast(httpContentQueue)

                if (message is HttpContent) {
                    httpContentQueue.queue.push(message, message is LastHttpContent)
                }

                ReferenceCountUtil.retain(message)
                val call = NettyApplicationCall(host.application, context, message, httpContentQueue.queue)
                context.executeCall(call)
            }
            else -> context.fireChannelRead(message)
        }
    }

    private fun ChannelHandlerContext.executeCall(call: ApplicationCall) {
        val eventLoop = host.callEventGroup
        val dispatcher = eventLoop.toCoroutineDispatcher()
        future(dispatcher) {
            hostPipeline.execute(call)
        }
    }
}

