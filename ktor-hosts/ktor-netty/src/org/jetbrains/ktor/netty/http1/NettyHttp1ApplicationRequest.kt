package org.jetbrains.ktor.netty.http1

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.multipart.*
import kotlinx.coroutines.experimental.io.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.netty.*
import org.jetbrains.ktor.util.*

internal class NettyHttp1ApplicationRequest(call: ApplicationCall,
                                            context: ChannelHandlerContext,
                                            val httpRequest: HttpRequest,
                                            requestBodyChannel: ByteReadChannel)
    : NettyApplicationRequest(call, context, requestBodyChannel, httpRequest.uri(), HttpUtil.isKeepAlive(httpRequest)) {
    override val local = NettyConnectionPoint(httpRequest, context)
    override val headers: ValuesMap = NettyApplicationRequestHeaders(httpRequest)
    override fun newDecoder(): HttpPostMultipartRequestDecoder {
        return HttpPostMultipartRequestDecoder(httpRequest)
    }
}