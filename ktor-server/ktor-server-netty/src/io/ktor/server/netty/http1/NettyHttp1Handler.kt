package io.ktor.server.netty.http1

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.netty.cio.*
import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.netty.handler.codec.http.HttpVersion.*
import io.netty.util.concurrent.*
import kotlinx.coroutines.experimental.io.*
import kotlin.coroutines.experimental.*

@ChannelHandler.Sharable
internal class NettyHttp1Handler(private val enginePipeline: EnginePipeline,
                                 private val environment: ApplicationEngineEnvironment,
                                 private val callEventGroup: EventExecutorGroup,
                                 private val engineContext: CoroutineContext,
                                 private val userContext: CoroutineContext,
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

        val call = NettyHttp1ApplicationCall(environment.application, context, message, requestBodyChannel, engineContext, userContext)
        requestQueue.schedule(call)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        if (!configured) {
            configured = true
            val requestBodyHandler = RequestBodyHandler(ctx, requestQueue)
            val responseWriter = NettyResponsePipeline(ctx, WriterEncapsulation.Http1, requestQueue)

            ctx.pipeline().apply {
                addLast(requestBodyHandler)
                addLast(callEventGroup, NettyApplicationCallHandler(userContext, enginePipeline))
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

