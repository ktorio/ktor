package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.util.*

public class NettyApplicationCall(override val application: Application,
                                  val context: ChannelHandlerContext,
                                  val httpRequest: FullHttpRequest) : ApplicationCall {

    override val attributes = Attributes()
    val httpResponse = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

    init {
        HttpHeaders.setTransferEncodingChunked(httpResponse)
    }

    override val close = Interceptable0 {
        (response as NettyApplicationResponse).finalize()
    }

    override val request: ApplicationRequest = NettyApplicationRequest(httpRequest)
    override val response: ApplicationResponse = NettyApplicationResponse(this, httpRequest, httpResponse, context)
}