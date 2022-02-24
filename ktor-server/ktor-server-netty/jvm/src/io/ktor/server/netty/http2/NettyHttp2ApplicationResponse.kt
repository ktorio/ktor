/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.netty.http2

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.server.netty.*
import io.ktor.util.*
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

    override fun setStatus(statusCode: HttpStatusCode) {
        responseHeaders.status(statusCode.value.toString())
    }

    override fun responseMessage(chunked: Boolean, data: ByteArray): Any {
        return responseMessage(false, data.isEmpty())
    }

    override fun responseMessage(chunked: Boolean, last: Boolean): Any {
        // transfer encoding should be never set for HTTP/2
        // so we simply remove header
        // it should be lower case
        responseHeaders.remove("transfer-encoding")
        return DefaultHttp2HeadersFrame(responseHeaders, last)
    }

    override suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade) {
        throw UnsupportedOperationException("HTTP/2 doesn't support upgrade")
    }

    override val headers = object : ResponseHeaders() {
        override fun engineAppendHeader(name: String, value: String) {
            responseHeaders.add(name.toLowerCasePreservingASCIIRules(), value)
        }

        override fun get(name: String): String? = responseHeaders[name]?.toString()
        override fun getEngineHeaderNames(): List<String> = responseHeaders.names().map { it.toString() }
        override fun getEngineHeaderValues(name: String): List<String> =
            responseHeaders.getAll(name).map { it.toString() }
    }

    @UseHttp2Push
    override fun push(builder: ResponsePushBuilder) {
        context.executor().execute {
            handler.startHttp2PushPromise(this@NettyHttp2ApplicationResponse.context, builder)
        }
    }
}
