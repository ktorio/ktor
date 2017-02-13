package org.jetbrains.ktor.netty

import io.netty.channel.*
import kotlinx.coroutines.experimental.*

internal class NettyApplicationCallHandler(private val host: NettyApplicationHost) : SimpleChannelInboundHandler<NettyApplicationCall>() {
    override fun channelRead0(context: ChannelHandlerContext, msg: NettyApplicationCall) {
        launch(host.callEventGroup.toCoroutineDispatcher()) {
            host.pipeline.execute(msg)
        }
    }
}