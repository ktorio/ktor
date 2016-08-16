package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.stream.*
import io.netty.util.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*

internal class NettyApplicationCall(application: Application,
                                    val context: ChannelHandlerContext,
                                    val httpRequest: HttpRequest,
                                    val drops: LastDropsCollectorHandler?,
                                    override val pool: ByteBufferPool
) : BaseApplicationCall(application) {

    var completed: Boolean = false

    val httpResponse = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

    override val request = NettyApplicationRequest(this, httpRequest, context, drops)
    override val response = NettyApplicationResponse(this, respondPipeline, httpRequest, httpResponse, context)

    override fun PipelineContext<*>.handleUpgrade(upgrade: ProtocolUpgrade) {
        context.executeInLoop {
            context.channel().pipeline().remove(ChunkedWriteHandler::class.java)
            context.channel().pipeline().remove(HostHttpHandler::class.java)

            response.status(upgrade.status ?: HttpStatusCode.SwitchingProtocols)
            upgrade.headers.flattenEntries().forEach { e ->
                response.headers.append(e.first, e.second)
            }

            context.channel().pipeline().addFirst(NettyDirectDecoder())
            drops?.forgetCompleted()

            context.writeAndFlush(httpResponse).addListener {
                context.channel().pipeline().remove(HttpServerCodec::class.java)
                drops?.forgetCompleted()
                context.channel().pipeline().addFirst(NettyDirectEncoder())

                upgrade.upgrade(this@NettyApplicationCall, this, request.content.get(), response.channel())
            }
        }

        onFinish {
            context.close()
        }

        pause()
    }

    override fun responseChannel(): WriteChannel = response.channel()

    override fun close() {
        completed = true
        ReferenceCountUtil.release(httpRequest)
        drops?.close(context)

        response.finalize()
        request.close()
    }
}