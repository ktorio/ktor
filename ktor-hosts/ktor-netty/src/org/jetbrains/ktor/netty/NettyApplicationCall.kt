package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.stream.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*

internal class NettyApplicationCall(application: Application,
                                    val context: ChannelHandlerContext,
                                    httpRequest: HttpRequest,
                                    override val bufferPool: ByteBufferPool,
                                    contentQueue: NettyContentQueue

) : BaseApplicationCall(application) {

    var completed: Boolean = false

    val httpResponse = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

    override val request = NettyApplicationRequest(httpRequest, NettyConnectionPoint(httpRequest, context), contentQueue)
    override val response = NettyApplicationResponse(this, respondPipeline, httpRequest, httpResponse, context)

    suspend override fun respond(message: Any) {
        super.respond(message)

        completed = true
        response.finalize()
        request.close()
    }

    override fun PipelineContext<*>.handleUpgrade(upgrade: ProtocolUpgrade) {
        context.executeInLoop {
            context.channel().pipeline().remove(ChunkedWriteHandler::class.java)
            context.channel().pipeline().remove(HostHttpHandler::class.java)

            response.status(upgrade.status ?: HttpStatusCode.SwitchingProtocols)
            upgrade.headers.flattenEntries().forEach { e ->
                response.headers.append(e.first, e.second)
            }

            context.channel().pipeline().addFirst(NettyDirectDecoder())

            context.writeAndFlush(httpResponse).addListener {
                context.channel().pipeline().remove(HttpServerCodec::class.java)
                context.channel().pipeline().addFirst(NettyDirectEncoder())

                upgrade.upgrade(this@NettyApplicationCall, this, request.content.get(), responseChannel())
            }
        }
    }

    override fun responseChannel(): WriteChannel = response.writeChannel.value
}