/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.http2

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.*
import io.ktor.server.netty.*
import io.ktor.server.netty.NettyApplicationCallHandler.CallHandlerCoroutineName
import io.ktor.server.netty.cio.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http2.*
import io.netty.util.AttributeKey
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.EventExecutorGroup
import kotlinx.coroutines.*
import java.lang.reflect.Field
import java.nio.channels.ClosedChannelException
import kotlin.coroutines.CoroutineContext

@ChannelHandler.Sharable
internal class NettyHttp2Handler(
    private val enginePipeline: EnginePipeline,
    private val application: Application,
    private val callEventGroup: EventExecutorGroup,
    private val userCoroutineContext: CoroutineContext,
    runningLimit: Int
) : ChannelInboundHandlerAdapter() {
    private val handlerJob = SupervisorJob(userCoroutineContext[Job])

    private val state = NettyHttpHandlerState(runningLimit)
    private lateinit var responseWriter: NettyHttpResponsePipeline

    override fun channelRead(context: ChannelHandlerContext, message: Any) {
        when (message) {
            is Http2HeadersFrame -> {
                state.isChannelReadCompleted.compareAndSet(expect = true, update = false)
                state.activeRequests.incrementAndGet()
                startHttp2(context, message.headers())
            }
            is Http2DataFrame -> {
                context.applicationCall?.request?.apply {
                    val eof = message.isEndStream
                    contentActor.trySend(message).isSuccess
                    if (eof) {
                        contentActor.close()
                        state.isCurrentRequestFullyRead.compareAndSet(expect = false, update = true)
                    } else {
                        state.isCurrentRequestFullyRead.compareAndSet(expect = true, update = false)
                    }
                } ?: message.release()
            }
            is Http2ResetFrame -> {
                context.applicationCall?.request?.let { r ->
                    val e = if (message.errorCode() == 0L) null else Http2ClosedChannelException(message.errorCode())
                    r.contentActor.close(e)
                }
            }
            else -> context.fireChannelRead(message)
        }
    }

    override fun channelActive(context: ChannelHandlerContext) {
        responseWriter = NettyHttpResponsePipeline(
            context = context,
            httpHandlerState = state,
            coroutineContext = handlerJob
        )

        // Append a tail sink that consumes NettyHttp2ApplicationCall messages forwarded by this handler
        // via fireChannelRead(call). This prevents Netty's tail handler from logging
        // "Discarded inbound message" warnings for calls that pass through any user-added
        // channelPipelineConfig handlers. The call lifecycle is driven by startHttp2, so the sink
        // only needs to drop the call without further action.
        if (context.pipeline().get(NettyHttp2ApplicationCallSink::class.java) == null) {
            context.pipeline().addLast(NettyHttp2ApplicationCallSink)
        }
        context.fireChannelActive()
    }

    override fun channelInactive(context: ChannelHandlerContext) {
        onStreamClose(context)
        context.fireChannelInactive()
    }

    private fun onStreamClose(context: ChannelHandlerContext) {
        context.applicationCall?.let { call ->
            context.applicationCall = null
            @OptIn(InternalAPI::class)
            call.attributes.getOrNull(HttpRequestCloseHandlerKey)?.invoke()
        }
    }

    override fun channelReadComplete(context: ChannelHandlerContext) {
        state.isChannelReadCompleted.compareAndSet(expect = false, update = true)
        responseWriter.flushIfNeeded()
        context.fireChannelReadComplete()
    }

    @Suppress("OverridingDeprecatedMember")
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        ctx.close()
    }

    private fun startHttp2(context: ChannelHandlerContext, headers: Http2Headers) {
        val callJob = Job(parent = userCoroutineContext[Job])
        val callExecutor = pinnedCallExecutor(context, callEventGroup)
        val callContext = userCoroutineContext +
            NettyDispatcher.CurrentContext(context, callExecutor) +
            callJob +
            CallHandlerCoroutineName
        val call = NettyHttp2ApplicationCall(
            application = application,
            context = context,
            headers = headers,
            handler = this@NettyHttp2Handler,
            engineContext = handlerJob + Dispatchers.Unconfined,
            coroutineContext = callContext
        )
        context.applicationCall = call

        // Fire channel read for custom handlers added to the pipeline
        context.fireChannelRead(call)

        // Reserve response slot synchronously on the I/O thread for proper ordering
        responseWriter.processResponse(call)

        // Defer coroutine start to the next event loop tick so that channelRead returns and Netty can
        // deliver subsequent Http2DataFrame messages. Without this, the coroutine runs on the event loop,
        // blocking data frame delivery and causing EOFException.
        // Dispatching to the call event group also ensures user handler code does not run on the I/O worker
        // event loop.
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

    @UseHttp2Push
    internal fun startHttp2PushPromise(context: ChannelHandlerContext, builder: ResponsePushBuilder) {
        val channel = context.channel() as Http2StreamChannel
        val streamId = channel.stream().id()
        val codec = channel.parent().pipeline().get(Http2MultiplexCodec::class.java)!!
        val connection = codec.connection()

        if (!connection.remote().allowPushTo()) {
            return
        }

        val rootContext = channel.parent().pipeline().lastContext()

        val promisedStreamId = connection.local().incrementAndGetNextStreamId()
        val headers = DefaultHttp2Headers().apply {
            val url = builder.url.build()

            method(builder.method.value)
            authority(url.hostWithPort)
            scheme(url.protocol.name)
            path(url.encodedPathAndQuery)
        }

        val bs = Http2StreamChannelBootstrap(channel.parent()).handler(this)
        val child = bs.open().get()

        child.setId(promisedStreamId)

        val promise = rootContext.newPromise()
        val childStream = connection.local().createStream(promisedStreamId, false)
        if (!child.stream().setStreamAndProperty(codec, childStream)) {
            childStream.close()
            child.close()
            return
        }

        codec.encoder().frameWriter().writePushPromise(rootContext, streamId, promisedStreamId, headers, 0, promise)
        if (promise.isSuccess) {
            startHttp2(child.pipeline().firstContext(), headers)
        } else {
            promise.addListener { future ->
                future.get()
                startHttp2(child.pipeline().firstContext(), headers)
            }
        }
    }

    // TODO: avoid reflection access once Netty provides API, see https://github.com/netty/netty/issues/7603
    private fun Http2StreamChannel.setId(streamId: Int) {
        val stream = stream()!!
        stream.idField.setInt(stream, streamId)
    }

    private val streamKeyField: Field? by lazy {
        try {
            Http2FrameCodec::class.javaObjectType.getDeclaredField("streamKey")
                .also { it.isAccessible = true }
        } catch (_: Throwable) {
            null
        }
    }

    private fun Http2FrameStream.setStreamAndProperty(codec: Http2FrameCodec, childStream: Http2Stream): Boolean {
        val streamKey = streamKeyField?.get(codec) as? Http2Connection.PropertyKey ?: return false

        val function = javaClass.declaredMethods
            .firstOrNull { it.name == "setStreamAndProperty" }
            ?.also { it.isAccessible = true } ?: return false

        try {
            function.invoke(this, streamKey, childStream)
        } catch (_: Throwable) {
            return false
        }

        return true
    }

    private val Http2FrameStream.idField: Field
        get() = javaClass.findIdField()

    private tailrec fun Class<*>.findIdField(): Field {
        val idField = try {
            getDeclaredField("id")
        } catch (_: NoSuchFieldException) {
            null
        }
        if (idField != null) {
            idField.isAccessible = true
            return idField
        }

        val superclass = superclass ?: throw NoSuchFieldException("id field not found")
        return superclass.findIdField()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private class Http2ClosedChannelException(
        val errorCode: Long
    ) : ClosedChannelException(), CopyableThrowable<Http2ClosedChannelException> {
        override val message: String
            get() = "Got close frame with code $errorCode"

        override fun createCopy(): Http2ClosedChannelException = Http2ClosedChannelException(errorCode).also {
            it.initCause(this)
        }
    }

    internal fun cancel() {
        handlerJob.cancel()
    }

    companion object {
        private val ApplicationCallKey = AttributeKey.valueOf<NettyHttp2ApplicationCall>("ktor.ApplicationCall")

        private var ChannelHandlerContext.applicationCall: NettyHttp2ApplicationCall?
            get() = channel().attr(ApplicationCallKey).get()
            set(newValue) {
                channel().attr(ApplicationCallKey).set(newValue)
            }
    }
}

/**
 * A no-op tail handler that swallows [NettyHttp2ApplicationCall] messages forwarded by
 * [NettyHttp2Handler.startHttp2] via [ChannelHandlerContext.fireChannelRead]. Its sole purpose is to
 * prevent Netty's default tail handler from logging a "Discarded inbound message ... at the tail of the
 * pipeline" warning. Non-call messages are propagated unchanged so they can reach Netty's tail and be
 * released/handled normally.
 */
@ChannelHandler.Sharable
internal object NettyHttp2ApplicationCallSink : ChannelInboundHandlerAdapter() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is NettyHttp2ApplicationCall) return
        ctx.fireChannelRead(msg)
    }
}

/**
 * A no-op tail handler installed on the HTTP/2 *connection* (parent) pipeline that swallows
 * connection-level [Http2Frame] messages (e.g. SETTINGS, PING, GOAWAY) which Netty's
 * [Http2MultiplexCodec] forwards down the parent pipeline after handling them.
 *
 * Without this sink, those frames reach Netty's default tail handler and trigger
 * "Discarded inbound message ... at the tail of the pipeline" warnings. Non-frame messages are
 * propagated unchanged so they can reach Netty's tail and be released/handled normally.
 */
@ChannelHandler.Sharable
internal object NettyHttp2ConnectionSink : ChannelInboundHandlerAdapter() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is Http2Frame) {
            ReferenceCountUtil.release(msg)
            return
        }
        ctx.fireChannelRead(msg)
    }
}
