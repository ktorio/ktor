package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.nio.charset.*

internal class FormUrlEncodedHandler(val encoding: Charset) : SimpleChannelInboundHandler<DefaultHttpContent>(true) {
    private val buffer = ByteArrayOutputStream(4096)

    val values: ValuesMap
        get() = buffer.toByteArray().toString(encoding).parseUrlEncodedParameters(encoding)

    override fun channelRead0(ctx: ChannelHandlerContext, msg: DefaultHttpContent) {
        msg.content().let { content ->
            if (content.isReadable) {
                content.readBytes(buffer, content.readableBytes())
            }
        }
    }
}