package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.nio.charset.*
import java.util.concurrent.atomic.*

internal class FormUrlEncodedHandler(val encoding: Charset, val completionHandler: (ValuesMap) -> Unit) : SimpleChannelInboundHandler<DefaultHttpContent>(true) {
    private val buffer = ByteArrayOutputStream(4096)
    private val completed = AtomicBoolean()

    val values: ValuesMap
        get() = buffer.toByteArray().toString(encoding).parseUrlEncodedParameters(encoding)

    override fun channelRead0(ctx: ChannelHandlerContext, msg: DefaultHttpContent) {
        msg.content().let { content ->
            if (content.isReadable) {
                content.readBytes(buffer, content.readableBytes())
            }
        }

        if (msg is LastHttpContent) {
            doComplete(ctx)
        }
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        doComplete(ctx)
        super.channelReadComplete(ctx)
    }

    private fun doComplete(ctx: ChannelHandlerContext) {
        if (completed.compareAndSet(false, true)) {
            ctx.pipeline().remove(this)
            completionHandler(values)
        }
    }
}