/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.http1.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.netty.channel.*
import io.netty.handler.codec.http.*
import kotlinx.coroutines.*
import org.slf4j.*
import kotlin.coroutines.*

private const val CHUNKED_VALUE  = "chunked"

internal class NettyApplicationCallHandler(
    userCoroutineContext: CoroutineContext,
    private val enginePipeline: EnginePipeline,
    private val logger: Logger
) : ChannelInboundHandlerAdapter(), CoroutineScope {
    override val coroutineContext: CoroutineContext = userCoroutineContext +
        CallHandlerCoroutineName +
        DefaultUncaughtExceptionHandler(logger)

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        when (msg) {
            is ApplicationCall -> handleRequest(ctx, msg)
            else -> ctx.fireChannelRead(msg)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun handleRequest(context: ChannelHandlerContext, call: ApplicationCall) {
        val callContext = CallHandlerCoroutineName + NettyDispatcher.CurrentContext(context)

        launch(callContext, start = CoroutineStart.UNDISPATCHED) {
            when {
                call is NettyHttp1ApplicationCall && !call.request.httpRequest.isValid() -> {
                    call.response.status(HttpStatusCode.BadRequest)
                    call.response.sendResponse(chunked = false, ByteReadChannel.Empty)
                    call.finish()
                }
                else -> enginePipeline.execute(call)
            }
        }
    }

    companion object {
        private val CallHandlerCoroutineName = CoroutineName("call-handler")
    }
}

internal fun HttpRequest.isValid(): Boolean {
    if (decoderResult().isFailure) {
        return false
    }

    val encodings = headers().getAll(io.ktor.http.HttpHeaders.TransferEncoding) ?: return true

    encodings.forEachIndexed { index, header ->
        val chunkedStart = header.indexOf(CHUNKED_VALUE)

        if (chunkedStart == -1) return@forEachIndexed

        if (index + 1 != encodings.size) return false

        val chunkedIsNotLast = chunkedStart + CHUNKED_VALUE.length < header.length
        if (chunkedIsNotLast) {
            return false
        }
    }

    return true
}
