/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.http1

import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.utils.io.*
import io.netty.buffer.*
import io.netty.channel.*
import io.netty.handler.codec.http.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal class NettyHttp1ApplicationCall(
    application: Application,
    context: ChannelHandlerContext,
    val httpRequest: HttpRequest,
    requestBodyChannel: ByteReadChannel?,
    engineContext: CoroutineContext,
    override val coroutineContext: CoroutineContext,
) : NettyApplicationCall(application, context, httpRequest), CoroutineScope {

    override val request = NettyHttp1ApplicationRequest(
        this,
        engineContext,
        context,
        httpRequest,
        requestBodyChannel ?: ByteReadChannel.Empty
    )

    override val response = NettyHttp1ApplicationResponse(
        this,
        context,
        engineContext,
        coroutineContext,
        httpRequest.protocolVersion()
    )

    init {
        putResponseAttribute()
    }

    override fun prepareMessage(buf: ByteBuf, isLastContent: Boolean): Any {
        if (isByteBufferContent) {
            return super.prepareMessage(buf, isLastContent)
        }
        return DefaultHttpContent(buf)
    }

    override fun prepareEndOfStreamMessage(lastTransformed: Boolean): Any? {
        if (isByteBufferContent) {
            return super.prepareEndOfStreamMessage(lastTransformed)
        }
        return LastHttpContent.EMPTY_LAST_CONTENT
    }

    override fun upgrade(dst: ChannelHandlerContext) {
        if (isByteBufferContent) {
            return super.upgrade(dst)
        }
        dst.pipeline().apply {
            replace(HttpServerCodec::class.java, "direct-encoder", NettyDirectEncoder())
        }
    }

    override fun isContextCloseRequired(): Boolean = !isByteBufferContent
}
