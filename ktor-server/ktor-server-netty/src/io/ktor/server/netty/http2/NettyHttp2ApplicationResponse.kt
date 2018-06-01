package io.ktor.server.netty.http2

import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.server.netty.*
import io.netty.channel.*
import io.netty.handler.codec.http2.*
import kotlin.coroutines.experimental.*

internal class NettyHttp2ApplicationResponse(call: NettyApplicationCall,
                                             val handler: NettyHttp2Handler,
                                             context: ChannelHandlerContext,
                                             engineContext: CoroutineContext,
                                             userContext: CoroutineContext
                                             )
    : NettyApplicationResponse(call, context, engineContext, userContext) {

    private val responseHeaders = DefaultHttp2Headers().apply {
        status(HttpStatusCode.OK.value.toString())
    }

    override fun setStatus(statusCode: HttpStatusCode) {
        responseHeaders.status(statusCode.value.toString())
    }

    override fun responseMessage(chunked: Boolean, last: Boolean): Any {
        return DefaultHttp2HeadersFrame(responseHeaders, false)
        // endStream should be false
        // as response pipeline is always sending at least one data frame
    }

    override suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade) {
        throw UnsupportedOperationException("HTTP/2 doesn't support upgrade")
    }

    override val headers = object : ResponseHeaders() {
        override fun engineAppendHeader(name: String, value: String) {
            responseHeaders.add(name.toLowerCase(), value)
        }

        override fun getEngineHeaderNames(): List<String> = responseHeaders.names().map { it.toString() }

        override fun getEngineHeaderValues(name: String): List<String> = responseHeaders.getAll(name).map { it.toString() }
    }

    override fun push(builder: ResponsePushBuilder) {
        context.executor().execute {
            handler.startHttp2PushPromise(this@NettyHttp2ApplicationResponse.context, builder)
        }
    }
}