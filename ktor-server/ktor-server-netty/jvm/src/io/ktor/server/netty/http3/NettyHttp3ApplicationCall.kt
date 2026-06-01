/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.http3

import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.netty.buffer.*
import io.netty.channel.*
import io.netty.handler.codec.http3.*
import kotlin.coroutines.*

internal class NettyHttp3ApplicationCall(
    application: Application,
    context: ChannelHandlerContext,
    val headers: Http3Headers,
    engineContext: CoroutineContext,
    userContext: CoroutineContext
) : NettyApplicationCall(application, context, headers) {

    override val coroutineContext: CoroutineContext = userContext

    override val request = NettyHttp3ApplicationRequest(this, engineContext, context, headers)
    override val response = NettyHttp3ApplicationResponse(this, context, engineContext, userContext)

    init {
        putResponseAttribute()
    }

    override fun prepareMessage(buf: ByteBuf, isLastContent: Boolean): Any {
        if (isByteBufferContent) {
            return super.prepareMessage(buf, isLastContent)
        }
        return DefaultHttp3DataFrame(buf)
    }

    override fun prepareEndOfStreamMessage(lastTransformed: Boolean): Any? {
        if (isByteBufferContent) {
            return super.prepareEndOfStreamMessage(lastTransformed)
        }
        // HTTP/3 signals end of stream by closing the QUIC stream, not via a frame flag
        return null
    }

    override fun upgrade(dst: ChannelHandlerContext) {
        if (isByteBufferContent) {
            return super.upgrade(dst)
        }
        throw IllegalStateException("HTTP/3 doesn't support upgrade")
    }

    override fun isContextCloseRequired(): Boolean = true
}
