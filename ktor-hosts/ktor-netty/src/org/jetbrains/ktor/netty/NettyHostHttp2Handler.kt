package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http2.*
import io.netty.util.*
import io.netty.util.collection.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.netty.http2.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.response.*

@ChannelHandler.Sharable
class NettyHostHttp2Handler(private val host: NettyApplicationHost, private val http2: Http2Connection?, private val hostPipeline: HostPipeline) : ChannelInboundHandlerAdapter() {
    override fun channelRead(context: ChannelHandlerContext, message: Any?) {
        when (message) {
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
        host.environment.log.error("Application ${host.application::class.java} cannot fulfill the request", cause)
        ctx.close()
    }

    private fun startHttp2(context: ChannelHandlerContext, streamId: Int, headers: Http2Headers, http2: Http2Connection) {
        val call = NettyHttp2ApplicationCall(host.application, context, streamId, headers, this, http2)
        context.callByStreamId[streamId] = call
        context.executeCall(call)
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

    private fun ChannelHandlerContext.executeCall(call: ApplicationCall) {
        launchAsync(channel().eventLoop().parent()) {
            hostPipeline.execute(call)
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