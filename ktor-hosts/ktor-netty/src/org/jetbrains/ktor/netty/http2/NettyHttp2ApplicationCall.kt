package org.jetbrains.ktor.netty.http2

import io.netty.channel.*
import io.netty.handler.codec.http2.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.netty.*

internal class NettyHttp2ApplicationCall(override val application: Application,
                                         val context: ChannelHandlerContext,
                                         streamId: Int,
                                         val headers: Http2Headers,
                                         handler: NettyHostHttp2Handler,
                                         connection: Http2Connection
) : BaseApplicationCall(application) {
    override val bufferPool = NettyByteBufferPool(context)
    override val request = NettyHttp2ApplicationRequest(this, context, streamId, headers)
    override val response = NettyHttp2ApplicationResponse(this, handler, context, connection)

    suspend override fun respondUpgrade(upgrade: FinalContent.ProtocolUpgrade) {
        throw UnsupportedOperationException("HTTP/2 doesn't support upgrade")
    }

    override suspend fun responseChannel() = response.channelLazy.value

    suspend override fun respondFinalContent(content: FinalContent) {
        try {
            super.respondFinalContent(content)
        } finally {
            response.ensureChannelClosed()
        }
    }
}