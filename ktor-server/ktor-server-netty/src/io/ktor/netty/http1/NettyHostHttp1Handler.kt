package io.ktor.netty.http1

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.netty.handler.codec.http.HttpVersion.*
import io.netty.util.concurrent.*
import io.ktor.host.*
import io.ktor.netty.*
import io.ktor.netty.cio.*
import kotlinx.coroutines.experimental.io.*
import kotlin.coroutines.experimental.*

@ChannelHandler.Sharable
internal class NettyHostHttp1Handler(private val hostPipeline: HostPipeline,
                                     private val environment: ApplicationHostEnvironment,
                                     private val callEventGroup: EventExecutorGroup,
                                     private val hostCoroutineContext: CoroutineContext,
                                     private val userCoroutineContext: CoroutineContext,
                                     private val requestQueue: NettyRequestQueue) : ChannelInboundHandlerAdapter() {
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

        val requestBodyChannel = if (message is LastHttpContent && !message.content().isReadable) {
            EmptyByteReadChannel
        } else if (message is HttpContent) {
            bodyHandler.newChannel().also { bodyHandler.channelRead(context, message) }
        } else {
            bodyHandler.newChannel()
        }

        val call = NettyHttp1ApplicationCall(environment.application, context, message, requestBodyChannel, hostCoroutineContext, userCoroutineContext)
        requestQueue.schedule(call)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        if (!configured) {
            configured = true
            val requestBodyHandler = RequestBodyHandler(ctx, requestQueue)
            val responseWriter = NettyResponsePipeline(ctx, WriterEncapsulation.Http1, requestQueue)

            ctx.pipeline().apply {
                addLast(requestBodyHandler)
                addLast(callEventGroup, NettyApplicationCallHandler(userCoroutineContext, hostPipeline))
            }

            responseWriter.ensureRunning()
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

            requestQueue.cancel()
        }
        super.channelInactive(ctx)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        requestQueue.cancel()
        ctx.close()
    }
}

