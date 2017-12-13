package io.ktor.server.netty.http2

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.netty.cio.*
import io.netty.channel.*
import io.netty.handler.codec.http2.*
import io.netty.util.*
import io.netty.util.collection.*
import io.netty.util.concurrent.*
import kotlinx.coroutines.experimental.*
import java.nio.channels.*
import kotlin.coroutines.experimental.*

@ChannelHandler.Sharable
internal class NettyHttp2Handler(private val enginePipeline: EnginePipeline,
                                 private val application: Application,
                                 private val callEventGroup: EventExecutorGroup,
                                 private val userCoroutineContext: CoroutineContext,
                                 private val http2: Http2Connection,
                                 private val requestQueue: NettyRequestQueue) : ChannelInboundHandlerAdapter() {
    override fun channelRead(context: ChannelHandlerContext, message: Any?) {
        when (message) {
            is Http2HeadersFrame -> {
                startHttp2(context, message.streamId(), message.headers(), http2)
            }
            is Http2DataFrame -> {
                context.callByStreamId[message.streamId()]?.request?.apply {
                    val eof = message.isEndStream
                    contentActor.offer(message)
                    if (eof) {
                        contentActor.close()
                    }
                } ?: message.release()
            }
            is Http2ResetFrame -> {
                context.callByStreamId[message.streamId()]?.request?.let { r ->
                    val e = if (message.errorCode() == 0L) null else Http2ClosedChannelException(message.errorCode())
                    r.contentActor.close(e)
                }
            }
            else -> context.fireChannelRead(message)
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        ctx.pipeline().apply {
            addLast(callEventGroup, NettyApplicationCallHandler(userCoroutineContext, enginePipeline))
        }

        val responseWriter = NettyResponsePipeline(ctx, WriterEncapsulation.Http2, requestQueue)
        responseWriter.ensureRunning()

        super.channelActive(ctx)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        ctx.pipeline().apply {
            remove(NettyApplicationCallHandler::class.java)
        }

        requestQueue.cancel()
        super.channelInactive(ctx)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        requestQueue.cancel()
        ctx.close()
    }

    private fun startHttp2(context: ChannelHandlerContext, streamId: Int, headers: Http2Headers, http2: Http2Connection) {
        val call = NettyHttp2ApplicationCall(application, context, headers, this, http2, Unconfined, userCoroutineContext)
        context.callByStreamId[streamId] = call
        requestQueue.schedule(call)
    }

    internal fun startHttp2PushPromise(connection: Http2Connection, context: ChannelHandlerContext, builder: ResponsePushBuilder) {
        val streamId = connection.local().incrementAndGetNextStreamId()
        val pushPromiseFrame = Http2PushPromiseFrame()
        pushPromiseFrame.promisedStreamId = streamId
        pushPromiseFrame.headers.apply {
            val pathAndQuery = builder.url.buildString().substringAfter("?", "").let { q ->
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

    private class Http2ClosedChannelException(val errorCode: Long) : ClosedChannelException() {
        override val message: String
            get() = "Got close frame with code $errorCode"
    }

    companion object {
        private val CallByStreamIdKey = AttributeKey.newInstance<IntObjectHashMap<NettyHttp2ApplicationCall>>("ktor.CallByStreamIdKey")

        private val ChannelHandlerContext.callByStreamId: IntObjectHashMap<NettyHttp2ApplicationCall>
            get() = channel().attr(CallByStreamIdKey).let { attr ->
                attr.get() ?: IntObjectHashMap<NettyHttp2ApplicationCall>().apply { attr.set(this) }
            }
    }
}