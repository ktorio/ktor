package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.stream.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*

internal fun setupUpgradeHelper(call: NettyApplicationCall, context: ChannelHandlerContext, drops: LastDropsCollectorHandler?) {
    call.respond.intercept(RespondPipeline.After) {
        val message = subject.message
        if (message is FinalContent.ProtocolUpgrade) {
            context.executeInLoop {
                context.channel().pipeline().remove(ChunkedWriteHandler::class.java)
                context.channel().pipeline().remove(HostHttpHandler::class.java)

                call.response.status(message.status ?: HttpStatusCode.SwitchingProtocols)
                message.headers.flattenEntries().forEach { e ->
                    call.response.headers.append(e.first, e.second)
                }

                context.channel().pipeline().addFirst(NettyDirectDecoder())
                drops?.forgetCompleted()

                context.writeAndFlush(call.httpResponse).addListener {
                    context.channel().pipeline().remove(HttpServerCodec::class.java)
                    drops?.forgetCompleted()
                    context.channel().pipeline().addFirst(NettyDirectEncoder())

                    message.upgrade(call, this, call.request.content.get(), call.response.channel())
                }
            }

            onFinish {
                context.close()
            }

            pause()
        }
    }
}
