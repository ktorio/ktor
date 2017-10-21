package io.ktor.server.netty.http2

import io.ktor.content.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.server.netty.*
import io.netty.channel.*
import io.netty.handler.codec.http2.*
import kotlin.coroutines.experimental.*

internal class NettyHttp2ApplicationResponse(call: NettyApplicationCall,
                                             val handler: NettyHostHttp2Handler,
                                             context: ChannelHandlerContext,
                                             val connection: Http2Connection,
                                             hostCoroutineContext: CoroutineContext,
                                             userCoroutineContext: CoroutineContext
                                             )
    : NettyApplicationResponse(call, context, hostCoroutineContext, userCoroutineContext) {

    private val responseHeaders = DefaultHttp2Headers().apply {
        status(HttpStatusCode.OK.value.toString())
    }

    override fun setStatus(statusCode: HttpStatusCode) {
        responseHeaders.status(statusCode.value.toString())
    }

    override fun responseMessage(chunked: Boolean, last: Boolean): Any {
        return DefaultHttp2HeadersFrame(responseHeaders, false)
    }

    suspend override fun respondUpgrade(upgrade: FinalContent.ProtocolUpgrade) {
        throw UnsupportedOperationException("HTTP/2 doesn't support upgrade")
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