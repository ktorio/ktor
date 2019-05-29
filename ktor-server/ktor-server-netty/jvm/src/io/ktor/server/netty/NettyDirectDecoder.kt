/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

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
