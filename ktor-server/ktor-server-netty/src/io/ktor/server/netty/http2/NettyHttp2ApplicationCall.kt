package io.ktor.server.netty.http2

import io.ktor.application.*
import io.ktor.server.netty.*
import io.netty.channel.*
import io.netty.handler.codec.http2.*
import kotlin.coroutines.experimental.*

internal class NettyHttp2ApplicationCall(application: Application,
                                         context: ChannelHandlerContext,
                                         val headers: Http2Headers,
                                         handler: NettyHostHttp2Handler,
                                         connection: Http2Connection,
                                         hostCoroutineContext: CoroutineContext,
                                         userCoroutineContext: CoroutineContext
) : NettyApplicationCall(application, context, headers) {

    override val request = NettyHttp2ApplicationRequest(this, context, headers)
    override val response = NettyHttp2ApplicationResponse(this, handler, context, connection, hostCoroutineContext, userCoroutineContext)
}