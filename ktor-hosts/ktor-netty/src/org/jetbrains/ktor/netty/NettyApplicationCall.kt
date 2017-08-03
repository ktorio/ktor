package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import kotlin.coroutines.experimental.*

internal class NettyApplicationCall(application: Application,
                                    context: ChannelHandlerContext,
                                    httpRequest: HttpRequest,
                                    contentQueue: NettyContentQueue,
                                    hostCoroutineContext: CoroutineContext,
                                    userCoroutineContext: CoroutineContext) : BaseApplicationCall(application) {

    override val bufferPool = NettyByteBufferPool(context)

    override val request = NettyApplicationRequest(this, NettyConnectionPoint(httpRequest, context), httpRequest, contentQueue)
    override val response = NettyApplicationResponse(this, httpRequest, context, hostCoroutineContext, userCoroutineContext)
}