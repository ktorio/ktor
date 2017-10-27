package io.ktor.server.netty

import io.ktor.application.*
import io.ktor.pipeline.*
import io.ktor.server.engine.*
import io.netty.channel.*
import kotlinx.coroutines.experimental.*
import kotlin.coroutines.experimental.*

internal class NettyApplicationCallHandler(private val userCoroutineContext: CoroutineContext,
                                           private val enginePipeline: EnginePipeline) : ChannelInboundHandlerAdapter() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        when (msg) {
            is ApplicationCall -> handleRequest(ctx, msg)
            else -> ctx.fireChannelRead(msg)
        }
    }

    private fun handleRequest(context: ChannelHandlerContext, call: ApplicationCall) {
        launch(userCoroutineContext + NettyDispatcher.CurrentContext(context), start = CoroutineStart.UNDISPATCHED) {
            enginePipeline.execute(call)
        }
    }
}