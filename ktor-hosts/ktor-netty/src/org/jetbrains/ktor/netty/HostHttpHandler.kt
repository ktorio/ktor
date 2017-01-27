package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http2.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.future.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*

@ChannelHandler.Sharable
class HostHttpHandler(private val nettyApplicationHost: NettyApplicationHost, private val http2: Http2Connection?, val pool: ByteBufferPool, private val hostPipeline: HostPipeline) : SimpleChannelInboundHandler<Any>(false) {
    override fun channelRead0(context: ChannelHandlerContext, message: Any) {
        when (message) {
            is HttpRequest -> {
                context.channel().config().isAutoRead = false
                startHttp1HandleRequest(context, message)
            }
            is Http2HeadersFrame -> {
                if (http2 == null) {
                    context.close()
                } else {
                    startHttp2(context, message.streamId(), message.headers(), http2)
                }
            }
            is Http2StreamFrame -> {
                //context.callByStreamId[message.streamId()]?.request?.handler?.listener?.channelRead(context, message)
            }
            else -> context.fireChannelRead(message)
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        nettyApplicationHost.environment.log.error("Application ${nettyApplicationHost.application.javaClass} cannot fulfill the request", cause)
        ctx.close()
    }

    private fun startHttp1HandleRequest(context: ChannelHandlerContext, request: HttpRequest) {
        val call = NettyApplicationCall(nettyApplicationHost.application, context, request, pool)
        context.executeCall(call)
    }

    private fun startHttp2(context: ChannelHandlerContext, streamId: Int, headers: Http2Headers, http2: Http2Connection) {
/*
        val call = NettyHttp2ApplicationCall(nettyApplicationHost.application, context, streamId, headers, this, nettyApplicationHost, http2, pool)
        context.callByStreamId[streamId] = call
        context.executeCall(call)
*/
    }

    fun startHttp2PushPromise(call: ApplicationCall, block: ResponsePushBuilder.() -> Unit, connection: Http2Connection, context: ChannelHandlerContext) {
/*
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
*/
    }

    private fun ChannelHandlerContext.executeCall(call: ApplicationCall) {
        val dispatcher = channel().eventLoop().toCoroutineDispatcher()
        future(dispatcher) {
            try {
                hostPipeline.execute(call)
                if (call.response.status() == null) {
                    call.respond(HttpStatusContent(HttpStatusCode.NotFound, "Cannot find resource with the requested URI: ${call.request.uri}"))
                }
            } catch (exception: Throwable) {
                nettyApplicationHost.application.environment.log.error("Failed to process request", exception)
                call.respond(HttpStatusCode.InternalServerError)
            } finally {
                close()
            }
        }
    }

    companion object {
        //private val CallByStreamIdKey = AttributeKey.newInstance<IntObjectHashMap<NettyHttp2ApplicationCall>>("ktor.CallByStreamIdKey")

/*
        private val ChannelHandlerContext.callByStreamId: IntObjectHashMap<NettyHttp2ApplicationCall>
            get() = channel().attr(CallByStreamIdKey).let { attr ->
                attr.get() ?: IntObjectHashMap<NettyHttp2ApplicationCall>().apply { attr.set(this) }
            }
*/
    }
}