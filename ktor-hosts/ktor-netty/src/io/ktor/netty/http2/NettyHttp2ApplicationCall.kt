package io.ktor.netty.http2

import io.netty.channel.*
import io.netty.handler.codec.http2.*
import io.ktor.application.*
import io.ktor.content.*
import io.ktor.host.*
import io.ktor.netty.*

internal class NettyHttp2ApplicationCall(application: Application,
                                         val context: ChannelHandlerContext,
                                         val headers: Http2Headers,
                                         handler: NettyHostHttp2Handler,
                                         connection: Http2Connection
) : BaseApplicationCall(application) {
    override val bufferPool = NettyByteBufferPool(context)
    override val request = NettyHttp2ApplicationRequest(this, context, headers)
    override val response = NettyHttp2ApplicationResponse(this, handler, context, connection)
}