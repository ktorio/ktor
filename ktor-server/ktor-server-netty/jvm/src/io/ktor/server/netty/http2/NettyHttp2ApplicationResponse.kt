/*
* Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.netty.http2

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.netty.http.*
import io.ktor.server.response.*
import io.netty.channel.*
import io.netty.handler.codec.http2.*
import kotlin.coroutines.*

internal class NettyHttp2ApplicationResponse(
    call: NettyApplicationCall,
    val handler: NettyHttp2Handler,
    context: ChannelHandlerContext,
    engineContext: CoroutineContext,
    userContext: CoroutineContext
) : NettyApplicationResponse(call, context, engineContext, userContext) {

    private val responseHeaders = DefaultHttp2Headers().apply {
        status(HttpStatusCode.OK.value.toString())
    }

    private val responseTrailers = DefaultHttp2Headers()

    override fun setStatus(statusCode: HttpStatusCode) {
        responseHeaders.status(statusCode.value.toString())
    }

    override fun responseMessage(chunked: Boolean, data: ByteArray): Any {
        return responseMessage(false, data.isEmpty())
    }

    override fun responseMessage(chunked: Boolean, last: Boolean): Any {
        // transfer encoding should never be set for HTTP/2,
        // so we simply remove header
        // it should be a lower case
        responseHeaders.remove("transfer-encoding")
        return DefaultHttp2HeadersFrame(responseHeaders, last)
    }

    override fun prepareTrailerMessage(): Any? {
        return if (responseTrailers.isEmpty) null else DefaultHttp2HeadersFrame(responseTrailers, true)
    }

    override suspend fun respondOutgoingContent(content: OutgoingContent) {
        super.respondOutgoingContent(content)

        // write all trailers
        content.trailers()?.let { it ->
            it.forEach { name, values ->
                for (value in values) {
                    trailers.append(name, value)
                }
            }
        }
    }

    override suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade) {
        throw UnsupportedOperationException("HTTP/2 doesn't support upgrade")
    }

    override val headers: ResponseHeaders = HttpMultiplexedResponseHeaders(responseHeaders)

    private val trailers: ResponseHeaders = HttpMultiplexedResponseHeaders(responseTrailers)

    @UseHttp2Push
    override fun push(builder: ResponsePushBuilder) {
        context.executor().execute {
            handler.startHttp2PushPromise(this@NettyHttp2ApplicationResponse.context, builder)
        }
    }
}
