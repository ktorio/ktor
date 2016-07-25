package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http2.*
import io.netty.util.AttributeKey
import io.netty.util.collection.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.http.HttpHeaders
import org.jetbrains.ktor.netty.http2.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*

@ChannelHandler.Sharable
class HostHttpHandler(private val nettyApplicationHost: NettyApplicationHost, private val connection: Http2Connection?) : SimpleChannelInboundHandler<Any>(false) {
    override fun channelRead0(context: ChannelHandlerContext, message: Any) {
        if (message is HttpRequest) {
            val requestContentType = message.headers().get(HttpHeaders.ContentType)?.let { ContentType.parse(it) }

            if (requestContentType != null && requestContentType.match(ContentType.Application.FormUrlEncoded)) {
                context.channel().config().isAutoRead = true
                val urlEncodedHandler = FormUrlEncodedHandler(Charsets.UTF_8, { parameters ->
                    startHttp1HandleRequest(context, message, true, { parameters }, null)
                })
                context.pipeline().addLast(urlEncodedHandler)
            } else {
                context.channel().config().isAutoRead = false
                val dropsHandler = LastDropsCollectorHandler() // in spite of that we have cleared auto-read mode we still need to collect remaining events
                context.pipeline().addLast(dropsHandler)

                startHttp1HandleRequest(context, message, false, { ValuesMap.Empty }, dropsHandler)
            }
        } else if (message is Http2HeadersFrame) {
            startHttp2(context, message.streamId(), message.headers(), connection!!) // TODO ???
        } else if (message is Http2StreamFrame) {
            context.callByStreamId[message.streamId()]?.request?.handler?.listener?.channelRead(context, message)
        } else {
            context.fireChannelRead(message)
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        nettyApplicationHost.environment.log.error("Application ${nettyApplicationHost.application.javaClass} cannot fulfill the request", cause)
        ctx.close()
    }

    override fun channelReadComplete(context: ChannelHandlerContext) {
        context.flush()
    }

    private fun startHttp1HandleRequest(context: ChannelHandlerContext, request: HttpRequest, bodyConsumed: Boolean, urlEncodedParameters: () -> ValuesMap, drops: LastDropsCollectorHandler?) {
        val call = NettyApplicationCall(nettyApplicationHost.application, context, request, bodyConsumed, urlEncodedParameters, drops)
        setupUpgradeHelper(call, context, drops)
        executeCall(call, request.uri())
    }

    private fun startHttp2(ctx: ChannelHandlerContext, streamId: Int, headers: Http2Headers, connection: Http2Connection) {
        val call = NettyHttp2ApplicationCall(nettyApplicationHost.application, ctx, streamId, headers, this, nettyApplicationHost, connection)
        ctx.callByStreamId[streamId] = call

        call.execute().whenComplete { pipelineState, throwable ->
            val response: Any? = if (throwable != null) {
                nettyApplicationHost.application.environment.log.error("Failed to process request", throwable)
                HttpStatusCode.InternalServerError
            } else if (pipelineState != PipelineState.Executing) {
                HttpStatusContent(HttpStatusCode.NotFound, "Cannot find resource with the requested URI: ${headers.path()}")
            } else {
                null
            }

            if (response != null) {
                call.execution.runBlockWithResult {
                    call.respond(response)
                }
            }
        }
    }

    fun startHttp2PushPromise(call: ApplicationCall, block: ResponsePushBuilder.() -> Unit, connection: Http2Connection, context: ChannelHandlerContext) {
        val builder = DefaultResponsePushBuilder(call)
        block(builder)

        val streamId = connection.local().incrementAndGetNextStreamId()
        val pushPromiseFrame = Http2PushPromiseFrame()
        pushPromiseFrame.promisedStreamId = streamId
        pushPromiseFrame.headers.apply {
            val pathAndQuery = builder.url.build().substringAfter("?", "").let { q ->
                if (q.isEmpty()) {
                    builder.url.encodedPath
                } else {
                    builder.url.encodedPath + "?" + q
                }
            }

            scheme(builder.url.protocol.name)
            method(builder.method.value)
            authority(builder.url.host + ":" + builder.url.port)
            path(pathAndQuery)
        }

        connection.local().createStream(streamId, false)

        context.writeAndFlush(pushPromiseFrame)

        startHttp2(context, streamId, pushPromiseFrame.headers, connection)
    }

    private fun executeCall(call: NettyApplicationCall, uri: String) {
        call.execute().whenComplete { pipelineState, throwable ->
            val response: Any? = if (throwable != null && !call.completed) {
                nettyApplicationHost.application.environment.log.error("Failed to process request", throwable)
                HttpStatusCode.InternalServerError
            } else if (pipelineState != PipelineState.Executing && !call.completed) {
                HttpStatusContent(HttpStatusCode.NotFound, "Cannot find resource with the requested URI: ${uri}")
            } else {
                null
            }

            if (response != null) {
                call.execution.runBlockWithResult {
                    call.respond(response)
                }
            }
        }
    }

    companion object {
        private val CallByStreamIdKey = AttributeKey.newInstance<IntObjectHashMap<NettyHttp2ApplicationCall>>("ktor.CallByStreamIdKey")

        private val ChannelHandlerContext.callByStreamId: IntObjectHashMap<NettyHttp2ApplicationCall>
            get() = channel().attr(CallByStreamIdKey).let { attr ->
                attr.get() ?: IntObjectHashMap<NettyHttp2ApplicationCall>().apply { attr.set(this) }
            }
    }
}