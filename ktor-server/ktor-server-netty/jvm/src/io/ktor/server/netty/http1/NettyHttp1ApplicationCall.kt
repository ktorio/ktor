/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.http1

import io.ktor.application.*
import io.ktor.server.netty.*
import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.ktor.utils.io.*
import kotlin.coroutines.*

internal class NettyHttp1ApplicationCall(
    application: Application,
    context: ChannelHandlerContext,
    httpRequest: HttpRequest,
    requestBodyChannel: ByteReadChannel,
    engineContext: CoroutineContext,
    userContext: CoroutineContext
) : NettyApplicationCall(application, context, httpRequest) {

    override val request = NettyHttp1ApplicationRequest(this, engineContext, context, httpRequest, requestBodyChannel)
    override val response = NettyHttp1ApplicationResponse(this, context, engineContext, userContext, httpRequest.protocolVersion())

    init {
        putResponseAttribute()
    }
}
