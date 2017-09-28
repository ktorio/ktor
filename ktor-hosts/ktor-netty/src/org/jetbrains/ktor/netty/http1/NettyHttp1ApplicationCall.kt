package org.jetbrains.ktor.netty.http1

import io.netty.channel.*
import io.netty.handler.codec.http.*
import kotlinx.coroutines.experimental.io.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.netty.*
import kotlin.coroutines.experimental.*

internal class NettyHttp1ApplicationCall(
        application: Application,
        context: ChannelHandlerContext,
        httpRequest: HttpRequest,
        requestBodyChannel: ByteReadChannel,
        hostCoroutineContext: CoroutineContext,
        userCoroutineContext: CoroutineContext
) : NettyApplicationCall(application, context, httpRequest) {

    override val request = NettyHttp1ApplicationRequest(this, context, httpRequest, requestBodyChannel)
    override val response = NettyHttp1ApplicationResponse(this, context, hostCoroutineContext, userCoroutineContext, httpRequest.protocolVersion())
}