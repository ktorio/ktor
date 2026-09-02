/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.http1

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.*
import io.ktor.server.netty.*
import io.ktor.server.netty.NettyApplicationCallHandler.CallHandlerCoroutineName
import io.ktor.server.netty.NettyDispatcher.CurrentContext
import io.ktor.server.netty.cio.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http.*
import io.netty.handler.timeout.ReadTimeoutException
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.EventExecutorGroup
import kotlinx.coroutines.*
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
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
    private lateinit var handlerJob: CompletableJob

    private var skipEmpty = false

    private lateinit var responseWriter: NettyHttpResponsePipeline

    private val state = NettyHttpHandlerState(runningLimit, onCapacityAvailable = ::drainPending)

    // Decoded messages that arrived while activeRequests >= runningLimit. Netty's HTTP codec can decode
    // and deliver many complete pipelined HttpRequest (and follow-up HttpContent) messages from a single
    // physical socket read, all before callReadIfNeeded ever gets a chance to gate anything - so the only
    // way to make runningLimit an actual hard cap is to buffer messages here instead of dispatching them,
    // and replay them in order once a slot frees up. Access is confined to this channel's event loop.
    private val pendingMessages = ArrayDeque<Any>()

    private val activeCalls = ConcurrentLinkedQueue<NettyHttp1ApplicationCall>()

    private var activated = false

    // Per-channel cache of the connection-stable portion of the per-call coroutine context.
    // The dispatcher, user context, application context, and coroutine name are reused across
    // all requests on this connection, so we build them once and combine only with the per-call
    // [Job] on each request.
    private var channelApplication: Application? = null
    private var channelCoroutineContext: CoroutineContext = EmptyCoroutineContext

    override fun channelActive(context: ChannelHandlerContext) {
        // channelActive may be fired more than once on this handler (for example, when the pipeline is
        // reconfigured during an HTTP/2 cleartext upgrade or via an explicit fireChannelActive call
        // after adding the handler). Guard against re-adding the body handler and the tail sink, which
        // must be present exactly once per pipeline.
        if (activated) {
            context.fireChannelActive()
            return
        }
        activated = true

        handlerJob = SupervisorJob(applicationProvider().coroutineContext[Job])

        responseWriter = NettyHttpResponsePipeline(
            context = context,
            httpHandlerState = state,
            coroutineContext = handlerJob
        )

        context.channel().config().isAutoRead = false
        context.channel().read()
        context.pipeline().apply {
            addLast(RequestBodyHandler(context))
            // Append a tail sink that consumes NettyHttp1ApplicationCall messages forwarded by this handler
            // via fireChannelRead(call). This prevents Netty's tail handler from logging
            // "Discarded inbound message" warnings for calls that pass through any user-added
            // channelPipelineConfig handlers. The call lifecycle is driven by handleRequest, so the sink
            // only needs to drop the call without further action.
            addLast(NettyHttp1ApplicationCallSink)
        }
        context.fireChannelActive()
    }

    override fun channelRead(context: ChannelHandlerContext, message: Any) {
        // These flags track the state of the *live* physical read cycle currently in progress, so they must
        // only be touched here, for messages as they are actually decoded - never from drainPending, which
        // replays messages decoded during a past, already-completed read cycle.
        if (message is LastHttpContent) {
            state.isCurrentRequestFullyRead.compareAndSet(expect = false, update = true)
        }
        if (message is HttpRequest) {
            if (message !is LastHttpContent) {
                state.isCurrentRequestFullyRead.compareAndSet(expect = true, update = false)
            }
            state.isChannelReadCompleted.compareAndSet(expect = true, update = false)
        }

        // A pipelined connection can have several complete requests (and their body content) decoded from
        // one physical read. HTTP/1.1 requests on a connection never interleave on the wire, so once one
        // message has been deferred, every message after it - whether it's the rest of that request's body
        // or the start of the next pipelined request - must stay queued in order until we catch up.
        if (pendingMessages.isNotEmpty()) {
            pendingMessages.addLast(message)
            return
        }
        dispatchOrDefer(context, message)
    }

    private fun dispatchOrDefer(context: ChannelHandlerContext, message: Any) {
        if (message is HttpRequest && state.activeRequests.value >= runningLimit) {
            pendingMessages.addLast(message)
            return
        }
        dispatchMessage(context, message)
    }

    private fun dispatchMessage(context: ChannelHandlerContext, message: Any) {
        when (message) {
            is HttpRequest -> {
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

    /**
     * Replays messages that were buffered by [dispatchOrDefer] while over [runningLimit], now that a slot
     * has freed up. Stops as soon as the next queued message is a new request that would exceed the limit
     * again, leaving it (and everything after it) queued for the next call.
     */
    private fun drainPending(context: ChannelHandlerContext) {
        while (true) {
            val next = pendingMessages.firstOrNull() ?: return
            if (next is HttpRequest && state.activeRequests.value >= runningLimit) return
            pendingMessages.removeFirst()
            dispatchMessage(context, next)
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
        while (pendingMessages.isNotEmpty()) {
            ReferenceCountUtil.release(pendingMessages.removeFirst())
        }
        while (true) {
            val call = activeCalls.poll() ?: break
            @OptIn(InternalAPI::class)
            call.attributes.getOrNull(HttpRequestCloseHandlerKey)?.invoke()
        }
        // Marks handlerJob as completing rather than cancelling it: calls that didn't opt into
        // HttpRequestLifecycle's cancelCallOnClose (invoked above) are allowed to keep running to
        // completion — only once every in-flight callJob finishes does handlerJob actually complete
        // and detach from applicationJob's children list.
        handlerJob.complete()
    }

    @Suppress("OverridingDeprecatedMember")
    override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
        when (cause) {
            is IOException -> {
                // Cancellation of in-flight calls and completion of handlerJob is left to
                // onConnectionClose(), triggered by the channelInactive() that follows this close():
                // that path invokes each call's HttpRequestCloseHandlerKey callback (giving opted-in
                // calls their specific ConnectionClosedException cause) before touching handlerJob,
                // so doing it here too would race a generic cause against that specific one.
                environment.log.trace("I/O operation failed", cause)
                context.close()
            }

            is ReadTimeoutException -> {
                if (activeCalls.isEmpty()) {
                    context.fireExceptionCaught(cause)
                    return
                }
                context.respond408RequestTimeoutHttp1()
                activeCalls.forEach { call ->
                    call.coroutineContext.cancel(CancellationException(cause))
                }
            }

            else -> {
                // See the IOException branch above: cleanup is left to the channelInactive()/
                // onConnectionClose() that follows this close().
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
        val callExecutor = pinnedCallExecutor(context, callEventGroup)
        val application = applicationProvider()
        // Building the coroutine context is quite expensive, so we cache most of the elements.
        val baseContext = when {
            application === channelApplication && channelCoroutineContext !== EmptyCoroutineContext ->
                channelCoroutineContext

            else -> {
                val newContext = application.coroutineContext +
                    userContext +
                    CurrentContext(context, callExecutor) +
                    CallHandlerCoroutineName
                channelApplication = application
                channelCoroutineContext = newContext
                newContext
            }
        }
        val callJob = Job(parent = handlerJob)

        // Only the per-call [Job] is combined per request; the rest of the context is cached on the
        // handler instance and reused across all calls on this connection.
        val callContext = baseContext + callJob
        val call = prepareCallFromRequest(context, message, callContext = callContext)
        activeCalls.add(call)

        // Fire channel read for custom handlers added to the pipeline
        context.fireChannelRead(call)

        // Reserve response slot synchronously on the I/O thread for proper ordering
        responseWriter.processResponse(call)

        // Defer coroutine start to the next event loop tick so that channelReadComplete() fires first.
        // This allows the response pipeline to detect that the request body is still being received and flush headers
        // early instead of buffering them, which is required when the client waits for response headers
        // before sending the request body.
        // Dispatching to the call event group also ensures user handler code does not run on the I/O worker
        // event loop.
        callExecutor.execute {
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
                    activeCalls.remove(call)
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

/**
 * A no-op tail handler that swallows [NettyHttp1ApplicationCall] messages forwarded by
 * [NettyHttp1Handler.handleRequest] via [ChannelHandlerContext.fireChannelRead]. Its sole purpose is to
 * prevent Netty's default tail handler from logging a "Discarded inbound message ... at the tail of the
 * pipeline" warning. Non-call messages are propagated unchanged so they can reach Netty's tail and be
 * released/handled normally.
 */
@ChannelHandler.Sharable
internal object NettyHttp1ApplicationCallSink : ChannelInboundHandlerAdapter() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is NettyHttp1ApplicationCall) return
        ctx.fireChannelRead(msg)
    }
}
