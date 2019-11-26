/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.http2

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.netty.cio.*
import io.netty.channel.*
import io.netty.handler.codec.http2.*
import io.netty.util.*
import io.netty.util.concurrent.*
import kotlinx.coroutines.*
import java.lang.reflect.*
import java.nio.channels.*
import kotlin.coroutines.*

@ChannelHandler.Sharable
internal class NettyHttp2Handler(
    private val enginePipeline: EnginePipeline,
    private val application: Application,
    private val callEventGroup: EventExecutorGroup,
    private val userCoroutineContext: CoroutineContext
) : ChannelInboundHandlerAdapter(), CoroutineScope {
    private val handlerJob = CompletableDeferred<Nothing>()

    override val coroutineContext: CoroutineContext
        get() = handlerJob

    override fun channelRead(context: ChannelHandlerContext, message: Any?) {
        when (message) {
            is Http2HeadersFrame -> {
                startHttp2(context, message.headers())
            }
            is Http2DataFrame -> {
                context.applicationCall?.request?.apply {
                    val eof = message.isEndStream
                    contentActor.offer(message)
                    if (eof) {
                        contentActor.close()
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

    override fun channelRegistered(ctx: ChannelHandlerContext?) {
        super.channelRegistered(ctx)

        ctx?.pipeline()?.apply {
            addLast(callEventGroup, NettyApplicationCallHandler(userCoroutineContext, enginePipeline, application.log))
        }
    }

    @Suppress("OverridingDeprecatedMember")
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        handlerJob.completeExceptionally(cause)
        ctx.close()
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext?) {
        super.handlerRemoved(ctx)
        handlerJob.cancel()
    }

    private fun startHttp2(context: ChannelHandlerContext, headers: Http2Headers) {
        val requestQueue = NettyRequestQueue(1, 1)
        val responseWriter = NettyResponsePipeline(context, WriterEncapsulation.Http2, requestQueue, handlerJob)

        val call = NettyHttp2ApplicationCall(
            application,
            context,
            headers,
            this,
            handlerJob + Dispatchers.Unconfined,
            userCoroutineContext
        )
        context.applicationCall = call

        requestQueue.schedule(call)
        requestQueue.close()

        responseWriter.ensureRunning()
    }

    internal fun startHttp2PushPromise(context: ChannelHandlerContext, builder: ResponsePushBuilder) {
        val channel = context.channel() as Http2StreamChannel
        val streamId = channel.stream().id()
        val codec = channel.parent().pipeline().get(Http2MultiplexCodec::class.java)!!
        val connection = codec.connection()


        val rootContext = channel.parent().pipeline().lastContext()

        val promisedStreamId = connection.local().incrementAndGetNextStreamId()
        val headers = DefaultHttp2Headers().apply {
            val url = builder.url
            val parameters = url.parameters

            val pathAndQuery = if (parameters.isEmpty()) url.encodedPath else buildString {
                append(url.encodedPath)
                append('?')
                parameters.build().formUrlEncodeTo(this)
            }

            method(builder.method.value)
            authority(builder.url.host + ":" + builder.url.port)
            scheme(builder.url.protocol.name)
            path(pathAndQuery)
        }

        val bs = Http2StreamChannelBootstrap(channel.parent()).handler(this)
        val child = bs.open().get()

        child.setId(promisedStreamId)

        val promise = rootContext.newPromise()
        codec.encoder().writePushPromise(rootContext, streamId, promisedStreamId, headers, 0, promise)
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

    private val Http2FrameStream.idField: Field
        get() = javaClass.findIdField()

    private tailrec fun Class<*>.findIdField(): Field {
        val f = try { getDeclaredField("id") } catch (t: NoSuchFieldException) { null }
        if (f != null) {
            f.isAccessible = true
            return f
        }

        val superclass = superclass ?: throw NoSuchFieldException("id field not found")
        return superclass.findIdField()
    }

    private class Http2ClosedChannelException(
        val errorCode: Long
    ) : ClosedChannelException(), CopyableThrowable<Http2ClosedChannelException> {
        override val message: String
            get() = "Got close frame with code $errorCode"

        override fun createCopy(): Http2ClosedChannelException? = Http2ClosedChannelException(errorCode).also {
            it.initCause(this)
        }
    }

    companion object {
        private val ApplicationCallKey = AttributeKey.newInstance<NettyHttp2ApplicationCall>("ktor.ApplicationCall")

        private var ChannelHandlerContext.applicationCall: NettyHttp2ApplicationCall?
            get() = channel().attr(ApplicationCallKey).get()
            set(newValue) {
                channel().attr(ApplicationCallKey).set(newValue)
            }
    }
}
