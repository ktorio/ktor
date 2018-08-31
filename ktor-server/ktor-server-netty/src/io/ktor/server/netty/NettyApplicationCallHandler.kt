package io.ktor.server.netty

import io.ktor.application.*
import io.ktor.util.pipeline.*
import io.ktor.server.engine.*
import io.netty.channel.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal class NettyApplicationCallHandler(userCoroutineContext: CoroutineContext,
                                           private val enginePipeline: EnginePipeline) : ChannelInboundHandlerAdapter() {
    private val handleCoroutineContext = userCoroutineContext + CallHandlerCoroutineName

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        when (msg) {
            is ApplicationCall -> handleRequest(ctx, msg)
            else -> ctx.fireChannelRead(msg)
        }
    }

    private fun handleRequest(context: ChannelHandlerContext, call: ApplicationCall) {
        launch(handleCoroutineContext + NettyDispatcher.CurrentContext(context), start = CoroutineStart.UNDISPATCHED) {
            enginePipeline.execute(call)
        }
    }

    companion object {
        private val CallHandlerCoroutineName = CoroutineName("call-handler")
    }
}