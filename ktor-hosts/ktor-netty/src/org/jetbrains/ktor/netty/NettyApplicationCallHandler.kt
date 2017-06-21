package org.jetbrains.ktor.netty

import io.netty.channel.*
import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.pipeline.*
import kotlin.coroutines.experimental.*

internal class NettyApplicationCallHandler(private val host: NettyApplicationHost) : ChannelInboundHandlerAdapter() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is NettyApplicationCall) {
            handleRequest(ctx, msg)
        } else {
            ctx.fireChannelRead(msg)
        }
    }

    fun handleRequest(context: ChannelHandlerContext, call: NettyApplicationCall) {
        launch(Dispatcher + CurrentContext(context)) {
            host.pipeline.execute(call)
        }
    }

    object Dispatcher : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext): Boolean {
            return !context[CurrentContextKey]!!.context.executor().inEventLoop()
        }

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            val nettyContext = context[CurrentContextKey]!!.context
            nettyContext.executor().execute(block)
        }
    }

    class CurrentContext(val context: ChannelHandlerContext) : AbstractCoroutineContextElement(CurrentContextKey)
    object CurrentContextKey : CoroutineContext.Key<CurrentContext>
}