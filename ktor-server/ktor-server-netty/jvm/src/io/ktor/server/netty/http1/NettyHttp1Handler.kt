/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.http1

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.*
import io.ktor.server.netty.*
import io.ktor.server.netty.NettyApplicationCallHandler.CallHandlerCoroutineName
import io.ktor.server.netty.cio.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http.*
import io.netty.handler.timeout.ReadTimeoutException
import io.netty.util.concurrent.EventExecutorGroup
import kotlinx.coroutines.*
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

internal class NettyHttp1Handler(
    private val applicationProvider: () -> Application,
    private val enginePipeline: EnginePipeline,
    private val environment: ApplicationEnvironment,
    private val callEventGroup: EventExecutorGroup,
    private val engineContext: CoroutineContext,
    private val userContext: CoroutineContext,
    private val runningLimit: Int
) : ChannelInboundHandlerAdapter() {
    private val handlerJob = CompletableDeferred<Nothing>()

    private var skipEmpty = false

    private lateinit var responseWriter: NettyHttpResponsePipeline

    private val state = NettyHttpHandlerState(runningLimit)

    @Volatile
    private var currentCall: NettyHttp1ApplicationCall? = null

    override fun channelActive(context: ChannelHandlerContext) {
        responseWriter = NettyHttpResponsePipeline(
            context = context,
            httpHandlerState = state,
            coroutineContext = handlerJob
        )

        context.channel().config().isAutoRead = false
        context.channel().read()
        context.pipeline().apply {
            addLast(RequestBodyHandler(context))
        }
        context.fireChannelActive()
    }

    override fun channelRead(context: ChannelHandlerContext, message: Any) {
        if (message is LastHttpContent) {
            state.isCurrentRequestFullyRead.compareAndSet(expect = false, update = true)
        }

        when (message) {
            is HttpRequest -> {
                if (message !is LastHttpContent) {
                    state.isCurrentRequestFullyRead.compareAndSet(expect = true, update = false)
                }
                state.isChannelReadCompleted.compareAndSet(expect = true, update = false)
                state.activeRequests.incrementAndGet()

                handleRequest(context, message)
                callReadIfNeeded(context)
            }

            is LastHttpContent if !message.content().isReadable && skipEmpty -> {
                skipEmpty = false
                message.release()
                callReadIfNeeded(context)
            }

            else -> {
                context.fireChannelRead(message)
            }
        }
    }

    override fun channelInactive(context: ChannelHandlerContext) {
        onConnectionClose(context)
        context.fireChannelInactive()
    }

    private fun onConnectionClose(context: ChannelHandlerContext) {
        if (context.channel().isActive) {
            return
        }
        currentCall?.let { call ->
            currentCall = null
            @OptIn(InternalAPI::class)
            call.attributes.getOrNull(HttpRequestCloseHandlerKey)?.invoke()
        }
    }

    @Suppress("OverridingDeprecatedMember")
    override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
        when (cause) {
            is IOException -> {
                environment.log.debug("I/O operation failed", cause)
                handlerJob.cancel()
                context.close()
            }

            is ReadTimeoutException -> {
                val callContext = currentCall?.coroutineContext
                if (callContext == null) {
                    context.fireExceptionCaught(cause)
                    return
                }
                context.respond408RequestTimeoutHttp1()
                callContext.cancel(CancellationException(cause))
            }

            else -> {
                handlerJob.completeExceptionally(cause)
                context.close()
            }
        }
    }

    override fun channelReadComplete(context: ChannelHandlerContext?) {
        state.isChannelReadCompleted.compareAndSet(expect = false, update = true)
        responseWriter.flushIfNeeded()
        super.channelReadComplete(context)
    }

    private fun handleRequest(context: ChannelHandlerContext, message: HttpRequest) {
        val userAppContext = applicationProvider().coroutineContext + userContext
        val callJob = Job(parent = userAppContext[Job])

        val callContext = userAppContext + CallHandlerCoroutineName + callJob
        val call = prepareCallFromRequest(context, message, callContext = callContext)
        currentCall = call

        // Fire channel read for custom handlers added to the pipeline
        context.fireChannelRead(call)

        // Reserve response slot synchronously on the I/O thread for proper ordering
        responseWriter.processResponse(call)

        callEventGroup.execute {
            val callScope = CoroutineScope(context = callContext)
            callScope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    if (!call.request.isValid()) {
                        call.respondError400BadRequest()
                        return@launch
                    }
                    enginePipeline.execute(call)
                } catch (error: Throwable) {
                    handleFailure(call, error)
                } finally {
                    callJob.complete()
                }
            }
        }
    }

    /**
     * Returns netty application call with [message] as a request
     * and channel for request body
     */
    private fun prepareCallFromRequest(
        context: ChannelHandlerContext,
        message: HttpRequest,
        callContext: CoroutineContext
    ): NettyHttp1ApplicationCall {
        val requestBodyChannel = when {
            message is LastHttpContent && !message.content().isReadable -> null
            message.method() === HttpMethod.GET &&
                !HttpUtil.isContentLengthSet(message) &&
                !HttpUtil.isTransferEncodingChunked(message) -> {
                skipEmpty = true
                null
            }

            else -> prepareRequestContentChannel(context, message)
        }
        return NettyHttp1ApplicationCall(
            application = applicationProvider(),
            context = context,
            httpRequest = message,
            requestBodyChannel = requestBodyChannel,
            engineContext = engineContext,
            coroutineContext = callContext
        )
    }

    private fun prepareRequestContentChannel(
        context: ChannelHandlerContext,
        message: HttpRequest
    ): ByteReadChannel {
        val bodyHandler = context.pipeline().get(RequestBodyHandler::class.java)
        val result = bodyHandler.newChannel()

        if (message is HttpContent) {
            bodyHandler.channelRead(context, message)
        }

        return result
    }

    private fun callReadIfNeeded(context: ChannelHandlerContext) {
        if (state.activeRequests.value < runningLimit) {
            context.read()
            state.skippedRead.value = false
        } else {
            state.skippedRead.value = true
        }
    }
}
