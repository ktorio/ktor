/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty

import io.ktor.application.*
import io.ktor.util.pipeline.*
import io.ktor.server.engine.*
import io.ktor.util.logging.*
import io.netty.channel.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal class NettyApplicationCallHandler(userCoroutineContext: CoroutineContext,
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

    private fun handleRequest(context: ChannelHandlerContext, call: ApplicationCall) {
        launch(CallHandlerCoroutineName + NettyDispatcher.CurrentContext(context), start = CoroutineStart.UNDISPATCHED) {
            enginePipeline.execute(call)
        }
    }

    companion object {
        private val CallHandlerCoroutineName = CoroutineName("call-handler")
    }
}
