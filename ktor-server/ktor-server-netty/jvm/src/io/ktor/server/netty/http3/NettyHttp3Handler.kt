/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.http3

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.netty.cio.*
import io.netty.channel.*
import io.netty.handler.codec.http3.*
import io.netty.util.*
import io.netty.util.concurrent.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal class NettyHttp3Handler(
    private val enginePipeline: EnginePipeline,
    private val application: Application,
    private val callEventGroup: EventExecutorGroup,
    private val userCoroutineContext: CoroutineContext,
    runningLimit: Int
) : Http3RequestStreamInboundHandler(), CoroutineScope {
    private val handlerJob = SupervisorJob(userCoroutineContext[Job])

    private val state = NettyHttpHandlerState(runningLimit)
    private lateinit var responseWriter: NettyHttpResponsePipeline

    override val coroutineContext: CoroutineContext
        get() = handlerJob

    override fun channelActive(context: ChannelHandlerContext) {
        responseWriter = NettyHttpResponsePipeline(
            context,
            state,
            coroutineContext
        )

        context.pipeline()?.apply {
            addLast(callEventGroup, NettyApplicationCallHandler(userCoroutineContext, enginePipeline))
        }
        context.fireChannelActive()
    }

    override fun channelRead(context: ChannelHandlerContext, frame: Http3HeadersFrame) {
        state.isChannelReadCompleted.compareAndSet(expect = true, update = false)

        val existingCall = context.applicationCall
        if (existingCall == null) {
            state.activeRequests.incrementAndGet()
            startHttp3(context, frame.headers())
        } else {
            existingCall.request.receiveTrailers(frame.headers())
        }
    }

    override fun channelRead(context: ChannelHandlerContext, frame: Http3DataFrame) {
        val request = context.applicationCall?.request
        if (request == null || !request.contentActor.trySend(frame).isSuccess) {
            frame.release()
        }
    }

    override fun channelInputClosed(context: ChannelHandlerContext) {
        context.applicationCall?.request?.apply {
            contentActor.close()
            state.isCurrentRequestFullyRead.compareAndSet(expect = false, update = true)
        }
    }

    override fun channelReadComplete(context: ChannelHandlerContext) {
        state.isChannelReadCompleted.compareAndSet(expect = false, update = true)
        responseWriter.flushIfNeeded()
        context.fireChannelReadComplete()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        application.log.error("HTTP/3 stream exception", cause)
        ctx.close()
    }

    private fun startHttp3(context: ChannelHandlerContext, headers: Http3Headers) {
        val call = NettyHttp3ApplicationCall(
            application,
            context,
            headers,
            handlerJob + Dispatchers.Unconfined,
            userCoroutineContext
        )
        context.applicationCall = call

        context.fireChannelRead(call)
        responseWriter.processResponse(call)
    }

    companion object {
        private val ApplicationCallKey = AttributeKey.valueOf<NettyHttp3ApplicationCall>("ktor.Http3ApplicationCall")

        private var ChannelHandlerContext.applicationCall: NettyHttp3ApplicationCall?
            get() = channel().attr(ApplicationCallKey).get()
            set(newValue) {
                channel().attr(ApplicationCallKey).set(newValue)
            }
    }
}
