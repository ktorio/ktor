package org.jetbrains.ktor.netty.http2

import io.netty.channel.*
import io.netty.handler.codec.http2.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.netty.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*

internal class NettyHttp2ApplicationCall(override val application: Application,
                                         val context: ChannelHandlerContext,
                                         streamId: Int,
                                         val headers: Http2Headers,
                                         handler: HostHttpHandler,
                                         host: NettyApplicationHost,
                                         connection: Http2Connection,
                                         override val pool: ByteBufferPool) : BaseApplicationCall(application) {
    override val request = NettyHttp2ApplicationRequest(this, context, streamId, headers)

    override val response = NettyHttp2ApplicationResponse(this, host, handler, context, respondPipeline, connection)

    override fun PipelineContext<*>.handleUpgrade(upgrade: ProtocolUpgrade) {
        throw UnsupportedOperationException("HTTP/2 doesn't support upgrade")
    }

    override fun responseChannel() = response.channelLazy.value

    override fun close() {
        // TODO send reset, remove headers
        response.ensureChannelClosed()
    }

}