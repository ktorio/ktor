package org.jetbrains.ktor.netty

import io.netty.buffer.*
import io.netty.channel.*
import io.netty.handler.codec.*
import io.netty.handler.codec.http.*

internal class NettyDirectDecoder : ByteToMessageDecoder() {
    override fun decode(ctx: ChannelHandlerContext, buf: ByteBuf, out: MutableList<Any>) {
        val message = DefaultHttpContent(buf.readBytes(buf.readableBytes()))
        out.add(message)
    }
}
