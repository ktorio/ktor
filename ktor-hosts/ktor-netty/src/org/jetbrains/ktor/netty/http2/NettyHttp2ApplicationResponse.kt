package org.jetbrains.ktor.netty.http2

import io.netty.channel.*
import io.netty.handler.codec.http2.*
import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.netty.*
import org.jetbrains.ktor.response.*

internal class NettyHttp2ApplicationResponse(call: ApplicationCall,
                                             val handler: NettyHostHttp2Handler,
                                             val context: ChannelHandlerContext,
                                             val connection: Http2Connection) : BaseApplicationResponse(call) {

    private var sent = false

    private val responseHeaders = DefaultHttp2Headers().apply {
        status(HttpStatusCode.OK.value.toString())
    }

    override fun setStatus(statusCode: HttpStatusCode) {
        responseHeaders.status(statusCode.value.toString())
    }

    private fun ensureMessageSent(forceLast: Boolean) {
        if (!sent) {
            sent = true
            context.writeAndFlush(DefaultHttp2HeadersFrame(responseHeaders, forceLast))
        }
    }

    internal val channelLazy: Lazy<WriteChannel> = lazy {
        ensureMessageSent(false)
        NettyHttp2WriteChannel(context)
    }

    fun ensureChannelClosed() {
        ensureMessageSent(true)
        if (channelLazy.isInitialized()) {
            channelLazy.value.close()
        }
    }

    override val headers = object : ResponseHeaders() {
        override fun hostAppendHeader(name: String, value: String) {
            responseHeaders.add(name.toLowerCase(), value)
        }

        override fun getHostHeaderNames(): List<String> = responseHeaders.names().map { it.toString() }

        override fun getHostHeaderValues(name: String): List<String> = responseHeaders.getAll(name).map { it.toString() }
    }

    override fun push(block: ResponsePushBuilder.() -> Unit) {
        launch(context.executor().asCoroutineDispatcher()) {
            val builder = DefaultResponsePushBuilder(call)
            block(builder)
            handler.startHttp2PushPromise(connection, this@NettyHttp2ApplicationResponse.context, builder)
        }
    }
}