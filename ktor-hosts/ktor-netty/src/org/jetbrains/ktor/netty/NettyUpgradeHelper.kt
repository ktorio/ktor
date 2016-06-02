package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.stream.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*

internal fun setupUpgradeHelper(call: NettyApplicationCall, context: ChannelHandlerContext, drops: LastDropsCollectorHandler?) {
    call.interceptRespond(RespondPipeline.Before) { obj ->
        if (obj is FinalContent.ProtocolUpgrade) {
            context.executeInLoop {
                context.channel().pipeline().remove(ChunkedWriteHandler::class.java)
                context.channel().pipeline().remove(NettyApplicationHost.HostHttpHandler::class.java)

                call.response.status(obj.status ?: HttpStatusCode.SwitchingProtocols)
                obj.headers.flattenEntries().forEach { e ->
                    call.response.headers.append(e.first, e.second)
                }

                context.channel().pipeline().addFirst(NettyDirectDecoder())
                drops?.forgetCompleted()

                context.writeAndFlush(call.httpResponse).addListener {
                    context.channel().pipeline().remove(HttpServerCodec::class.java)
                    drops?.forgetCompleted()
                    context.channel().pipeline().addFirst(NettyDirectEncoder())

                    obj.upgrade(call, this, call.request.content.get(), call.response.channel())
                }
            }

            onSuccess {
                context.close()
            }
            onFail {
                context.close()
            }

            pause()
        }
    }
}
