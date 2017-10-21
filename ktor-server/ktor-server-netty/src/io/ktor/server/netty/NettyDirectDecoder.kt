package io.ktor.server.netty

import io.netty.buffer.*
import io.netty.channel.*
import io.netty.handler.codec.*
import io.netty.handler.codec.http.*

internal class NettyDirectDecoder : ByteToMessageDecoder() {
    override fun decode(ctx: ChannelHandlerContext, buf: ByteBuf, out: MutableList<Any>) {
        val content = ctx.alloc().buffer(buf.readableBytes())
        buf.readBytes(content)
        out.add(DefaultHttpContent(content))
    }
}
