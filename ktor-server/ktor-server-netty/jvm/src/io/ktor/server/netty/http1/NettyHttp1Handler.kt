/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.http1

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.netty.cio.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.util.concurrent.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import java.io.*
import kotlin.coroutines.*

internal class NettyHttp1Handler(
    private val enginePipeline: EnginePipeline,
    private val environment: ApplicationEngineEnvironment,
    private val callEventGroup: EventExecutorGroup,
    private val engineContext: CoroutineContext,
    private val userContext: CoroutineContext,
    private val runningLimit: Int
) : ChannelInboundHandlerAdapter(), CoroutineScope {
    private val handlerJob = CompletableDeferred<Nothing>()

    override val coroutineContext: CoroutineContext get() = handlerJob

    private var skipEmpty = false
    private val skippedRead: AtomicBoolean = atomic(false)

    private lateinit var responseWriter: NettyHttpResponsePipeline

    /**
     *  Represents current number of processing requests
     */
    internal val activeRequests: AtomicLong = atomic(0L)

    /**
     * True if current request's last http content is read, false otherwise.
     */
    internal val isCurrentRequestFullyRead: AtomicBoolean = atomic(false)

    /**
     * True if [channelReadComplete] was invoked for the current request, false otherwise
     */
    internal val isChannelReadCompleted: AtomicBoolean = atomic(false)

    override fun channelActive(context: ChannelHandlerContext) {
        responseWriter = NettyHttpResponsePipeline(
            context,
            this,
            coroutineContext
        )

        context.channel().config().isAutoRead = false
        context.channel().read()
        context.pipeline().apply {
            addLast(RequestBodyHandler(context))
            addLast(callEventGroup, NettyApplicationCallHandler(userContext, enginePipeline))
        }
        context.fireChannelActive()
    }

    override fun channelRead(context: ChannelHandlerContext, message: Any) {
        if (message is LastHttpContent) {
            isCurrentRequestFullyRead.compareAndSet(expect = false, update = true)
        }

        when {
            message is HttpRequest -> {
                if (message !is LastHttpContent) {
                    isCurrentRequestFullyRead.compareAndSet(expect = true, update = false)
                }
                isChannelReadCompleted.compareAndSet(expect = true, update = false)
                activeRequests.incrementAndGet()

                handleRequest(context, message)
                callReadIfNeeded(context)
            }
            message is LastHttpContent && !message.content().isReadable && skipEmpty -> {
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
        context.pipeline().remove(NettyApplicationCallHandler::class.java)
        context.fireChannelInactive()
    }

    @Suppress("OverridingDeprecatedMember")
    override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
        if (cause is IOException || cause is ChannelIOException) {
            environment.application.log.debug("I/O operation failed", cause)
            handlerJob.cancel()
        } else {
            handlerJob.completeExceptionally(cause)
        }
        context.close()
    }

    override fun channelReadComplete(context: ChannelHandlerContext?) {
        isChannelReadCompleted.compareAndSet(expect = false, update = true)
        responseWriter.flushIfNeeded()
        super.channelReadComplete(context)
    }

    private fun handleRequest(context: ChannelHandlerContext, message: HttpRequest) {
        val call = prepareCallFromRequest(context, message)

        context.fireChannelRead(call)
        responseWriter.processResponse(call)
    }

    /**
     * Returns netty application call with [message] as a request
     * and channel for request body
     */
    private fun prepareCallFromRequest(
        context: ChannelHandlerContext,
        message: HttpRequest
    ): NettyHttp1ApplicationCall {
        val requestBodyChannel = when {
            message is LastHttpContent && !message.content().isReadable -> null
            message.method() === HttpMethod.GET &&
                !HttpUtil.isContentLengthSet(message) && !HttpUtil.isTransferEncodingChunked(message) -> {
                skipEmpty = true
                null
            }
            else -> prepareRequestContentChannel(context, message)
        }

        return NettyHttp1ApplicationCall(
            environment.application,
            context,
            message,
            requestBodyChannel,
            engineContext,
            userContext
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
        if (activeRequests.value < runningLimit) {
            context.read()
            skippedRead.value = false
        } else {
            skippedRead.value = true
        }
    }

    internal fun onLastResponseMessage(context: ChannelHandlerContext) {
        activeRequests.decrementAndGet()

        if (skippedRead.compareAndSet(expect = false, update = true) && activeRequests.value < runningLimit) {
            context.read()
        }
    }
}
