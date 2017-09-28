package org.jetbrains.ktor.netty.http1

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.netty.handler.codec.http.HttpVersion.*
import io.netty.util.*
import io.netty.util.concurrent.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.netty.*
import org.jetbrains.ktor.netty.cio.*
import kotlin.coroutines.experimental.*

@ChannelHandler.Sharable
internal class NettyHostHttp1Handler(private val hostPipeline: HostPipeline,
                                     private val environment: ApplicationHostEnvironment,
                                     private val callEventGroup: EventExecutorGroup,
                                     private val hostCoroutineContext: CoroutineContext,
                                     private val userCoroutineContext: CoroutineContext) : ChannelInboundHandlerAdapter() {
    private var configured = false

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is HttpRequest) {
            handleRequest(ctx, msg)
        } else {
            ctx.fireChannelRead(msg)
        }
    }

    private fun handleRequest(context: ChannelHandlerContext, message: HttpRequest) {
        if (HttpUtil.is100ContinueExpected(message)) {
            context.write(DefaultFullHttpResponse(HTTP_1_1, CONTINUE))
        }

        context.channel().config().isAutoRead = false
        val bodyHandler = context.pipeline().get(RequestBodyHandler::class.java)
        val requestBodyChannel = bodyHandler.newChannel()

        if (message is HttpContent) {
            bodyHandler.channelRead(context, message)
        }

        val call = NettyHttp1ApplicationCall(environment.application, context, message, requestBodyChannel, hostCoroutineContext, userCoroutineContext)
        context.channel().attr(ResponseWriterKey).get().send(call)

        context.fireChannelRead(call)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        if (!configured) {
            configured = true
            val requestBodyHandler = RequestBodyHandler(ctx)
            val responseWriter = NettyResponsePipeline(ctx, WriterEncapsulation.Http1)

            ctx.channel().attr(ResponseWriterKey).set(responseWriter)

            ctx.pipeline().apply {
                addLast(requestBodyHandler)
                addLast(callEventGroup, NettyApplicationCallHandler(userCoroutineContext, hostPipeline))
            }
        }

        super.channelActive(ctx)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        if (configured) {
            configured = false
            ctx.pipeline().apply {
//                remove(RequestBodyHandler::class.java)
                remove(NettyApplicationCallHandler::class.java)
            }

            ctx.channel().attr(ResponseWriterKey).getAndSet(null)?.close()
        }
        super.channelInactive(ctx)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        ctx.close()
        ctx.channel().attr(ResponseWriterKey).getAndSet(null)?.job?.cancel(cause)
    }

    companion object {
        internal val ResponseWriterKey = AttributeKey.newInstance<NettyResponsePipeline>("NettyResponsePipeline")
    }
}

