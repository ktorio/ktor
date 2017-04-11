package org.jetbrains.ktor.netty

import io.netty.channel.*
import org.jetbrains.ktor.pipeline.*

internal class NettyApplicationCallHandler(private val host: NettyApplicationHost) : SimpleChannelInboundHandler<NettyApplicationCall>() {
    override fun channelRead0(context: ChannelHandlerContext, msg: NettyApplicationCall) {
        launchAsync(context.executor()) {
            host.pipeline.execute(msg)
        }
    }
}