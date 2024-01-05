/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.netty.http1

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.utils.io.*
import io.netty.channel.*
import io.netty.handler.codec.http.*
import kotlin.coroutines.*

internal class NettyHttp1ApplicationRequest(
    call: PipelineCall,
    coroutineContext: CoroutineContext,
    context: ChannelHandlerContext,
    val httpRequest: HttpRequest,
    requestBodyChannel: ByteReadChannel
) : NettyApplicationRequest(
    call,
    coroutineContext,
    context,
    requestBodyChannel,
    httpRequest.uri(),
    HttpUtil.isKeepAlive(httpRequest)
) {
    override val local = NettyConnectionPoint(httpRequest, context)

    override val engineHeaders: Headers = NettyApplicationRequestHeaders(httpRequest)
}
