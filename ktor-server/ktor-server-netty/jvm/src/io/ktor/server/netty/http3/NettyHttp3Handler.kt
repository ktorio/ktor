/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.http3

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.netty.NettyApplicationCallHandler.CallHandlerCoroutineName
import io.ktor.server.netty.cio.*
import io.ktor.util.pipeline.*
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http3.Http3DataFrame
import io.netty.handler.codec.http3.Http3Headers
import io.netty.handler.codec.http3.Http3HeadersFrame
import io.netty.handler.codec.http3.Http3RequestStreamInboundHandler
import io.netty.util.AttributeKey
import io.netty.util.concurrent.EventExecutorGroup
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

internal class NettyHttp3Handler(
    private val enginePipeline: EnginePipeline,
    private val application: Application,
    private val userCoroutineContext: CoroutineContext,
    private val callEventGroup: EventExecutorGroup,
    runningLimit: Int
) : Http3RequestStreamInboundHandler(), CoroutineScope {
    // Parent [Job] for per-call [Job]s. Cached to avoid re-running `userCoroutineContext[Job]` per request.
    private val parentJob: Job? = userCoroutineContext[Job]

    private val handlerJob = SupervisorJob(parentJob)

    // Connection-stable portion of the per-call coroutine context. Cached so each request only needs
    // to combine it with the per-stream dispatcher and the per-call [Job].
    private val staticCallContext: CoroutineContext = userCoroutineContext + CallHandlerCoroutineName

    // Engine context exposed on the [NettyHttp3ApplicationCall]. Constant per handler instance.
    private val callEngineContext: CoroutineContext = handlerJob + Dispatchers.Unconfined

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

    override fun channelInactive(context: ChannelHandlerContext) {
        handlerJob.cancel()
        context.fireChannelInactive()
    }

    override fun channelReadComplete(context: ChannelHandlerContext) {
        state.isChannelReadCompleted.compareAndSet(expect = false, update = true)
        responseWriter.flushIfNeeded()
        context.fireChannelReadComplete()
    }

    @Suppress("OverridingDeprecatedMember")
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        application.log.error("HTTP/3 stream exception", cause)
        ctx.close()
    }

    private fun startHttp3(context: ChannelHandlerContext, headers: Http3Headers) {
        val callJob = Job(parent = parentJob)
        val callExecutor = pinnedCallExecutor(context, callEventGroup)
        // Combine the cached static context with the per-stream dispatcher and per-call [Job] only.
        val callContext = staticCallContext + NettyDispatcher.CurrentContext(context, callExecutor) + callJob
        val call = NettyHttp3ApplicationCall(
            application,
            context,
            headers,
            callEngineContext,
            callContext
        )
        context.applicationCall = call

        responseWriter.processResponse(call)

        // Dispatching to the call event group keeps user handler code off the QUIC event loop,
        // which drives every connection and stream of this connector (same model as HTTP/1/2).
        callExecutor.execute {
            val callScope = CoroutineScope(context = callContext)
            callScope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    enginePipeline.execute(call)
                } catch (error: Throwable) {
                    handleFailure(call, error)
                } finally {
                    callJob.complete()
                }
            }
        }
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
