package io.ktor.netty.http2

import io.netty.channel.*
import io.netty.handler.codec.http2.*
import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.host.*
import io.ktor.http.*
import io.ktor.netty.*
import io.ktor.response.*

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

    suspend override fun respondUpgrade(upgrade: FinalContent.ProtocolUpgrade) {
        throw UnsupportedOperationException("HTTP/2 doesn't support upgrade")
    }

    private val channelLazy: Lazy<WriteChannel> = lazy {
        ensureMessageSent(false)
        NettyHttp2WriteChannel(context)
    }

    override suspend fun responseChannel() = channelLazy.value

    suspend override fun respondFinalContent(content: FinalContent) {
        try {
            super.respondFinalContent(content)
        } finally {
            ensureMessageSent(true)
            if (channelLazy.isInitialized()) {
                channelLazy.value.close()
            }
        }
    }

    override val headers = object : ResponseHeaders() {
        override fun hostAppendHeader(name: String, value: String) {
            responseHeaders.add(name.toLowerCase(), value)
        }

        override fun getHostHeaderNames(): List<String> = responseHeaders.names().map { it.toString() }

        override fun getHostHeaderValues(name: String): List<String> = responseHeaders.getAll(name).map { it.toString() }
    }

    override fun push(builder: ResponsePushBuilder) {
        context.executor().execute {
            handler.startHttp2PushPromise(connection, this@NettyHttp2ApplicationResponse.context, builder)
        }
    }
}