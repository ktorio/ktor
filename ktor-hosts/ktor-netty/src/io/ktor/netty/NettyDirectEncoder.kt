package io.ktor.netty

import io.netty.buffer.*
import io.netty.channel.*
import io.netty.handler.codec.*
import io.netty.handler.codec.http.*

internal class NettyDirectEncoder : MessageToByteEncoder<HttpContent>() {
    override fun encode(ctx: ChannelHandlerContext, msg: HttpContent, out: ByteBuf) {
        out.writeBytes(msg.content())
    }
}
