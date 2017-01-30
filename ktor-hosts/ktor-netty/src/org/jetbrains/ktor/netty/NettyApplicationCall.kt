package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.stream.*
import io.netty.util.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.io.*

internal class NettyApplicationCall(application: Application,
                                    val context: ChannelHandlerContext,
                                    val httpRequest: HttpRequest,
                                    override val bufferPool: ByteBufferPool,
                                    val contentQueue: NettyContentQueue

) : BaseApplicationCall(application) {

    var completed: Boolean = false

    val httpResponse = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

    override val request = NettyApplicationRequest(httpRequest, context, contentQueue)
    override val response = NettyApplicationResponse(this, respondPipeline, httpRequest, httpResponse, context)

    suspend override fun respond(message: Any) {
        super.respond(message)

        completed = true
        try {
            response.finalize()
            request.close()
        } catch (t: Throwable) {
            context.close()
            throw IOException("response finalization or request close failed", t)
        }
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