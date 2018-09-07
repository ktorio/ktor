package io.ktor.server.netty

import io.netty.buffer.*
import io.netty.channel.*
import io.netty.handler.codec.*

internal class NettyDirectDecoder : ByteToMessageDecoder() {
    override fun decode(ctx: ChannelHandlerContext, buf: ByteBuf, out: MutableList<Any>) {
        out.add(buf.copy())
        buf.readerIndex(buf.writerIndex())
    }
}
