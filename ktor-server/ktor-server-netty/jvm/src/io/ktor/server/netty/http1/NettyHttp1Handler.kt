/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.http1

import io.ktor.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.netty.cio.*
import io.ktor.util.cio.*
import io.ktor.util.logging.*
import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.util.concurrent.*
import kotlinx.coroutines.*
import io.ktor.utils.io.*
import java.io.*
import kotlin.coroutines.*

@ChannelHandler.Sharable
internal class NettyHttp1Handler(
    private val enginePipeline: EnginePipeline,
    private val environment: ApplicationEngineEnvironment,
    private val callEventGroup: EventExecutorGroup,
    private val engineContext: CoroutineContext,
    private val userContext: CoroutineContext,
    private val requestQueue: NettyRequestQueue
) : ChannelInboundHandlerAdapter(), CoroutineScope {
    private val handlerJob = CompletableDeferred<Nothing>()

    private var configured = false
    private var skipEmpty = false

    override val coroutineContext: CoroutineContext get() = handlerJob

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is HttpRequest) {
            handleRequest(ctx, msg)
        } else if (msg is LastHttpContent && !msg.content().isReadable && skipEmpty) {
            skipEmpty = false
            msg.release()
        } else {
            ctx.fireChannelRead(msg)
        }
    }

    private fun handleRequest(context: ChannelHandlerContext, message: HttpRequest) {
        context.channel().config().isAutoRead = false

        val requestBodyChannel = when {
            message is LastHttpContent && !message.content().isReadable -> ByteReadChannel.Empty
            message.method() === HttpMethod.GET &&
                !HttpUtil.isContentLengthSet(message) && !HttpUtil.isTransferEncodingChunked(message) -> {
                skipEmpty = true
                ByteReadChannel.Empty
            }
            else -> content(context, message)
        }

        val call = NettyHttp1ApplicationCall(
            environment.application,
            context,
            message,
            requestBodyChannel,
            engineContext,
            userContext
        )

        requestQueue.schedule(call)
    }

    private fun content(context: ChannelHandlerContext, message: HttpRequest): ByteReadChannel {
        return when (message) {
            is HttpContent -> {
                val bodyHandler = context.pipeline().get(RequestBodyHandler::class.java)
                bodyHandler.newChannel().also { bodyHandler.channelRead(context, message) }
            }
            else -> {
                val bodyHandler = context.pipeline().get(RequestBodyHandler::class.java)
                bodyHandler.newChannel()
            }
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        if (!configured) {
            configured = true
            val requestBodyHandler = RequestBodyHandler(ctx, requestQueue)
            val responseWriter = NettyResponsePipeline(ctx, WriterEncapsulation.Http1, requestQueue, coroutineContext)

            ctx.pipeline().apply {
                addLast(requestBodyHandler)
                addLast(callEventGroup, NettyApplicationCallHandler(userContext, enginePipeline, environment.log))
            }

            responseWriter.ensureRunning()
        }

        super.channelActive(ctx)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        if (configured) {
            configured = false
            ctx.pipeline().apply {
                remove(NettyApplicationCallHandler::class.java)
            }

            requestQueue.cancel()
        }
        super.channelInactive(ctx)
    }

    @Suppress("OverridingDeprecatedMember")
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        if (cause is IOException || cause is ChannelIOException) {
            environment.application.log.debug("I/O operation failed", cause)
            handlerJob.cancel()
        } else {
            handlerJob.completeExceptionally(cause)
        }
        requestQueue.cancel()
        ctx.close()
    }
}

