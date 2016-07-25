package org.jetbrains.ktor.netty.http2

import io.netty.channel.*
import io.netty.handler.codec.http2.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.netty.*
import org.jetbrains.ktor.util.*

internal class NettyHttp2ApplicationCall(override val application: Application,
                                         val context: ChannelHandlerContext,
                                         streamId: Int,
                                         val headers: Http2Headers,
                                         handler: HostHttpHandler,
                                         host: NettyApplicationHost,
                                         connection: Http2Connection) : BaseApplicationCall(application) {
    override val request = NettyHttp2ApplicationRequest(this, context, streamId, headers)

    override val response = NettyHttp2ApplicationResponse(this, host, handler, context, connection)

    override val parameters: ValuesMap
        get() = request.parameters

    override fun close() {
        // TODO send reset, remove headers
        response.ensureChannelClosed()
    }

}